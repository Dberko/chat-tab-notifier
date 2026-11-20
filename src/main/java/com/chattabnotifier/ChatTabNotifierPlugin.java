package com.chattabnotifier;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Chat Tab Notifier",
	description = "Highlights the chat tab buttons at the bottom left when a channel has a message you have not read",
	tags = {"chat", "tab", "notify", "highlight", "private", "message", "pm", "clan", "trade", "friends"},
	internalName = "chat-tab-notifier"
)
public class ChatTabNotifierPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(ChatTabNotifierPlugin.class);

	/** Typed as {@code ::chattab}, and only handled while the debugging switch is on. */
	private static final String TEST_COMMAND = "chattab";

	/**
	 * Messages the game files under a channel tab that nobody typed: joins, leaves, and the notices
	 * a clan or friends chat posts about itself.
	 */
	private static final Set<ChatMessageType> CHANNEL_NOTICES = EnumSet.of(
		ChatMessageType.FRIENDSCHATNOTIFICATION,
		ChatMessageType.CLAN_MESSAGE,
		ChatMessageType.CLAN_GUEST_MESSAGE,
		ChatMessageType.CLAN_GIM_MESSAGE,
		ChatMessageType.CLAN_CREATION_INVITATION);

	@Inject
	private Client client;

	@Inject
	private ChatTabNotifierConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChatTabNotifierOverlay overlay;

	@Inject
	private Notifier notifier;

	/**
	 * Tabs holding something unread, against the wall clock time of their newest message. Written on
	 * the client thread from the event handlers and read on the client thread by the overlay, so no
	 * synchronisation is needed.
	 */
	private final Map<ChatTab, Long> unread = new EnumMap<>(ChatTab.class);

	/** The tab that was open last tick, so switching to a tab can clear it. */
	private ChatTab lastActiveTab;

	@Provides
	ChatTabNotifierConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChatTabNotifierConfig.class);
	}

	@Override
	protected void startUp()
	{
		unread.clear();
		lastActiveTab = null;
		overlayManager.add(overlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		unread.clear();
		lastActiveTab = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();

		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			unread.clear();
			lastActiveTab = null;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatTab tab = tabFor(event.getType());

		if (tab == null)
		{
			if (config.logDecisions())
			{
				log.info("{}: not filed under any tab", event.getType());
			}

			return;
		}

		String skipped = reasonToSkip(tab, event);

		if (config.logDecisions())
		{
			log.info("{} on the {} tab: {}", event.getType(), tab.getLabel(),
				skipped == null ? "lighting it up" : "skipped, " + skipped);
		}

		if (skipped != null)
		{
			return;
		}

		boolean firstOfItsKind = unread.put(tab, System.currentTimeMillis()) == null;

		// Only the message that lights a dark tab notifies. A channel mid-conversation would
		// otherwise ping on every line.
		if (firstOfItsKind)
		{
			notifier.notify(notificationFor(tab), describe(tab, event));
		}
	}

	/**
	 * Null when a message should light its tab, otherwise why it should not. Split out from the
	 * handler so the reason can be logged, which is the only way to tell a deliberate skip from a
	 * plugin that is not working.
	 */
	private String reasonToSkip(ChatTab tab, ChatMessage event)
	{
		if (!isEnabled(tab))
		{
			return "the " + tab.getLabel() + " channel is switched off";
		}

		if (config.ignoreSystemMessages() && CHANNEL_NOTICES.contains(event.getType()))
		{
			return "it is a channel notice and those are ignored";
		}

		ChatTab active = activeTab();

		// Nothing to flag if the message landed on the tab already in front of you.
		if (config.suppressActiveTab() && active == tab)
		{
			return "that tab is already open";
		}

		if (config.treatAllTabAsRead() && active == ChatTab.ALL)
		{
			return "the All tab is open and counts as read";
		}

		return null;
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (!config.testCommand() || !TEST_COMMAND.equalsIgnoreCase(event.getCommand()))
		{
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		String[] args = event.getArguments();

		// Print what the client says each tab occupies, so the highlight can be lined up against
		// real numbers instead of judged by eye.
		if (args.length > 0 && "bounds".equalsIgnoreCase(args[0]))
		{
			for (ChatTab tab : ChatTab.values())
			{
				String line = tab.getLabel()
					+ " container " + rectOf(client.getWidget(tab.getButtonId()))
					+ ", face " + rectOf(client.getWidget(tab.getGraphicId()));

				log.info("{}", line);
				reply(line);
			}

			return;
		}

		// Light the tabs without going near the chat pipeline, to tell a routing problem from a
		// drawing one.
		if (args.length > 0 && "draw".equalsIgnoreCase(args[0]))
		{
			List<ChatTab> lit = enabledTabs();

			for (ChatTab tab : lit)
			{
				unread.put(tab, System.currentTimeMillis());
			}

			reply(lit.isEmpty()
				? "every channel is switched off, so there is nothing to light"
				: "lit " + describe(lit) + " directly, skipping chat");

			return;
		}

		List<ChatTab> targets;

		if (args.length == 0)
		{
			targets = enabledTabs();

			if (targets.isEmpty())
			{
				reply("every channel is switched off, so nothing would light up");
				return;
			}
		}
		else
		{
			ChatTab named = byName(args[0]);

			if (named == null)
			{
				reply("no channel called '" + args[0]
					+ "'. Try game, public, private, channel, clan, trade, or draw");
				return;
			}

			if (!isEnabled(named))
			{
				reply("the " + named.getLabel()
					+ " channel is switched off, so this message will not light it");
			}

			targets = Collections.singletonList(named);
		}

		for (ChatTab tab : targets)
		{
			client.addChatMessage(sampleType(tab), "Tester", "Chat Tab Notifier test message", null);
		}

		reply("faked a message in " + describe(targets)
			+ ". Nothing left your client and nothing was sent to anyone");
	}

	private List<ChatTab> enabledTabs()
	{
		List<ChatTab> enabled = new ArrayList<>();

		for (ChatTab tab : ChatTab.values())
		{
			if (isEnabled(tab))
			{
				enabled.add(tab);
			}
		}

		return enabled;
	}

	private void reply(String message)
	{
		client.addChatMessage(ChatMessageType.CONSOLE, "", "Chat Tab Notifier: " + message, null);
	}

	private static String rectOf(Widget widget)
	{
		if (widget == null)
		{
			return "missing";
		}

		Rectangle bounds = widget.getBounds();

		if (bounds == null)
		{
			return widget.isHidden() ? "hidden, no bounds" : "no bounds";
		}

		return bounds.width + "x" + bounds.height + " at " + bounds.x + "," + bounds.y
			+ (widget.isHidden() ? " (hidden)" : "");
	}

	private static String describe(List<ChatTab> tabs)
	{
		StringBuilder names = new StringBuilder();

		for (ChatTab tab : tabs)
		{
			if (names.length() > 0)
			{
				names.append(", ");
			}

			names.append(tab.getLabel());
		}

		return names.toString();
	}

	/** A message type that lands on the given tab, for the test command to fake. */
	static ChatMessageType sampleType(ChatTab tab)
	{
		switch (tab)
		{
			case GAME:
				return ChatMessageType.GAMEMESSAGE;

			case PUBLIC:
				return ChatMessageType.PUBLICCHAT;

			case PRIVATE:
				return ChatMessageType.PRIVATECHAT;

			case CHANNEL:
				return ChatMessageType.FRIENDSCHAT;

			case CLAN:
				return ChatMessageType.CLAN_CHAT;

			case TRADE:
				return ChatMessageType.TRADEREQ;

			default:
				return ChatMessageType.GAMEMESSAGE;
		}
	}

	/** Matches a channel the way somebody would type it, nicknames included. */
	static ChatTab byName(String name)
	{
		String wanted = name.trim().toLowerCase(Locale.ROOT);

		switch (wanted)
		{
			case "pm":
			case "pms":
			case "dm":
				return ChatTab.PRIVATE;

			case "fc":
			case "friends":
			case "friendschat":
				return ChatTab.CHANNEL;

			case "cc":
				return ChatTab.CLAN;

			default:
				break;
		}

		for (ChatTab tab : ChatTab.values())
		{
			if (tab.getLabel().toLowerCase(Locale.ROOT).equals(wanted))
			{
				return tab;
			}
		}

		return null;
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		ChatTab active = activeTab();

		// Track this every tick even with nothing unread, otherwise a stale value can match the tab
		// the player switches to later and the switch goes unnoticed.
		if (active != lastActiveTab)
		{
			lastActiveTab = active;

			if (active == ChatTab.ALL && config.treatAllTabAsRead())
			{
				unread.clear();
			}
			else if (active != null)
			{
				unread.remove(active);
			}
		}

		int seconds = config.clearAfter();

		if (seconds > 0 && !unread.isEmpty())
		{
			long cutoff = System.currentTimeMillis() - seconds * 1000L;
			unread.values().removeIf(stamp -> stamp < cutoff);
		}
	}

	/**
	 * Which tab is open. The selected button wears a sprite of its own, which is read first because
	 * it describes itself; {@code CHAT_VIEW} is the fallback for when that sprite set changes.
	 */
	private ChatTab activeTab()
	{
		for (ChatTab tab : ChatTab.values())
		{
			Widget graphic = client.getWidget(tab.getGraphicId());

			if (graphic == null)
			{
				continue;
			}

			int sprite = graphic.getSpriteId();

			if (sprite == Chatbox.SPRITE_SELECTED || sprite == Chatbox.SPRITE_SELECTED_HOVERED)
			{
				return tab;
			}
		}

		return ChatTab.fromChatView(client.getVarcIntValue(Chatbox.VAR_CHAT_VIEW));
	}

	/** The tab a message is filed under, or null for anything that does not belong to one. */
	private static ChatTab tabFor(ChatMessageType type)
	{
		switch (type)
		{
			case PUBLICCHAT:
			case MODCHAT:
			case AUTOTYPER:
			case MODAUTOTYPER:
				return ChatTab.PUBLIC;

			case PRIVATECHAT:
			case MODPRIVATECHAT:
				return ChatTab.PRIVATE;

			case FRIENDSCHAT:
			case FRIENDSCHATNOTIFICATION:
				return ChatTab.CHANNEL;

			case CLAN_CHAT:
			case CLAN_MESSAGE:
			case CLAN_GUEST_CHAT:
			case CLAN_GUEST_MESSAGE:
			case CLAN_GIM_CHAT:
			case CLAN_GIM_MESSAGE:
			case CLAN_CREATION_INVITATION:
				return ChatTab.CLAN;

			case TRADEREQ:
			case TRADE:
			case CHALREQ_TRADE:
			case CHALREQ_FRIENDSCHAT:
			case CHALREQ_CLANCHAT:
				return ChatTab.TRADE;

			case GAMEMESSAGE:
			case SPAM:
			case ENGINE:
			case CONSOLE:
			case BROADCAST:
			case WELCOME:
			case LEVELUPMESSAGE:
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
			case FRIENDNOTIFICATION:
			case IGNORENOTIFICATION:
			case LOGINLOGOUTNOTIFICATION:
			case DIDYOUKNOW:
			case PLAYERRELATED:
			case TENSECTIMEOUT:
			case SNAPSHOTFEEDBACK:
				return ChatTab.GAME;

			default:
				// Dialogue and mesboxes, your own outgoing private message and trade offer, and
				// anything else the client does not put on a tab.
				return null;
		}
	}

	private boolean isEnabled(ChatTab tab)
	{
		switch (tab)
		{
			case GAME:
				return config.notifyGame();

			case PUBLIC:
				return config.notifyPublic();

			case PRIVATE:
				return config.notifyPrivate();

			case CHANNEL:
				return config.notifyChannel();

			case CLAN:
				return config.notifyClan();

			case TRADE:
				return config.notifyTrade();

			default:
				// The All tab shows every channel, so there is nothing it could be unread for.
				return false;
		}
	}

	Color colourFor(ChatTab tab)
	{
		switch (tab)
		{
			case GAME:
				return config.colourGame();

			case PUBLIC:
				return config.colourPublic();

			case PRIVATE:
				return config.colourPrivate();

			case CHANNEL:
				return config.colourChannel();

			case CLAN:
				return config.colourClan();

			case TRADE:
				return config.colourTrade();

			default:
				return Color.WHITE;
		}
	}

	private Notification notificationFor(ChatTab tab)
	{
		switch (tab)
		{
			case GAME:
				return config.notificationGame();

			case PUBLIC:
				return config.notificationPublic();

			case PRIVATE:
				return config.notificationPrivate();

			case CHANNEL:
				return config.notificationChannel();

			case CLAN:
				return config.notificationClan();

			case TRADE:
				return config.notificationTrade();

			default:
				return Notification.OFF;
		}
	}

	private static String describe(ChatTab tab, ChatMessage event)
	{
		String name = event.getName() == null ? "" : Text.removeTags(event.getName()).replace(' ', ' ').trim();

		if (tab == ChatTab.GAME || name.isEmpty())
		{
			return "New " + tab.getLabel().toLowerCase() + " message";
		}

		if (tab == ChatTab.PRIVATE)
		{
			return "Private message from " + name;
		}

		return tab.getLabel() + " message from " + name;
	}

	/** The live map the overlay draws from; both run on the client thread. */
	Map<ChatTab, Long> getUnreadTabs()
	{
		return unread;
	}

	/** For the self check, which has no client to read tabs from. */
	static Map<ChatMessageType, ChatTab> tabMapping()
	{
		Map<ChatMessageType, ChatTab> mapping = new EnumMap<>(ChatMessageType.class);

		for (ChatMessageType type : ChatMessageType.values())
		{
			ChatTab tab = tabFor(type);

			if (tab != null)
			{
				mapping.put(type, tab);
			}
		}

		return Collections.unmodifiableMap(mapping);
	}
}
