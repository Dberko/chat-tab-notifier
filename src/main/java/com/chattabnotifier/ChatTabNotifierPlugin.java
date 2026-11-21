package com.chattabnotifier;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.RuneLite;
import net.runelite.client.audio.AudioPlayer;
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

	/** What the volume slider's top end means, matching the game's own sound effect range. */
	private static final int MAX_VOLUME = 127;

	/** Sound formats Java reads without any extra library, so the plugin needs no dependencies. */
	private static final String[] SOUND_EXTENSIONS = {".wav", ".aiff", ".aif", ".au"};

	/** Basename of the sound file for messages you send, beside the per-tab ones. */
	static final String SEND_SOUND_NAME = "send";

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

	@Inject
	private AudioPlayer audioPlayer;

	@Inject
	private ScheduledExecutorService executor;

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

		// Made on startup rather than on first use, so there is somewhere obvious to drop files
		// before anything has been configured.
		File folder = soundFolder();

		if (!folder.isDirectory() && !folder.mkdirs())
		{
			log.warn("could not create the sound folder at {}", folder);
		}
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
		// A private message you send is filed under a type of its own and belongs to no tab, but it
		// is still something you sent, which is what the send sound is for.
		if (event.getType() == ChatMessageType.PRIVATECHATOUT)
		{
			playSound(config.sendSound(), SEND_SOUND_NAME);
			return;
		}

		ChatTab tab = tabFor(event.getType());

		if (tab == null)
		{
			if (config.logDecisions())
			{
				log.info("{}: not filed under any tab", event.getType());
			}

			return;
		}

		// A line you typed yourself gets the send sound and never a channel sound as well, so one
		// message can never make two noises.
		boolean mine = isFromLocalPlayer(event);

		if (mine)
		{
			playSound(config.sendSound(), SEND_SOUND_NAME);
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

		if (!mine)
		{
			playSound(soundFor(tab), tab.getSoundName());
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

		// Say where the sound folder is and which files were found there, so a silent channel can
		// be told apart from a misnamed or unreadable file.
		if (args.length > 0 && "sounds".equalsIgnoreCase(args[0]))
		{
			reply("sound folder: " + soundFolder());

			for (ChatTab tab : ChatTab.values())
			{
				if (tab != ChatTab.ALL)
				{
					reply(tab.getSoundName() + ": " + soundFileStatus(tab.getSoundName()));
				}
			}

			reply(SEND_SOUND_NAME + ": " + soundFileStatus(SEND_SOUND_NAME));
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

	private static String soundFileStatus(String basename)
	{
		File file = soundFile(basename);

		if (file == null)
		{
			return "no file (drop " + basename + ".wav in the folder)";
		}

		return file.getName() + ", " + file.length() + " bytes"
			+ (file.canRead() ? "" : " (cannot be read)");
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

	/**
	 * Plays one of the game's own sound effects, or a sound file of the player's own.
	 *
	 * @param fileBasename the file to look for, without an extension, when the choice is a file
	 */
	private void playSound(ChatSound sound, String fileBasename)
	{
		if (sound == null)
		{
			return;
		}

		int id = sound.getSoundEffectId();

		if (id == ChatSound.SILENT)
		{
			return;
		}

		if (id == ChatSound.FROM_FILE)
		{
			playFile(fileBasename);
			return;
		}

		if (id == ChatSound.BY_CONFIG)
		{
			id = config.customSoundId();
		}

		if (id <= 0)
		{
			return;
		}

		client.playSoundEffect(id, config.soundVolume());
	}

	/**
	 * Plays a sound file the player dropped in the plugin's folder.
	 *
	 * <p>Reading the file and opening an audio line both block, and this is the client thread, so
	 * the work goes to RuneLite's executor. Nothing is shipped with the plugin: the files are the
	 * player's own.
	 */
	private void playFile(String basename)
	{
		File file = soundFile(basename);

		if (file == null)
		{
			if (config.logDecisions())
			{
				log.info("no {} sound file in {}", basename, soundFolder());
			}

			return;
		}

		float gain = gainDecibels(config.soundVolume());

		executor.execute(() ->
		{
			try
			{
				audioPlayer.play(file, gain);
			}
			catch (IOException | UnsupportedAudioFileException | LineUnavailableException e)
			{
				log.warn("could not play {}", file, e);
			}
		});
	}

	/** The folder the player drops their own sounds into. */
	static File soundFolder()
	{
		return new File(RuneLite.RUNELITE_DIR, "chat-tab-notifier");
	}

	/**
	 * The player's sound file for a channel, or null when there is none. The basename always comes
	 * from this plugin's own names, never from anything typed, so it cannot walk out of the folder.
	 */
	static File soundFile(String basename)
	{
		File folder = soundFolder();

		for (String extension : SOUND_EXTENSIONS)
		{
			File candidate = new File(folder, basename + extension);

			if (candidate.isFile())
			{
				return candidate;
			}
		}

		return null;
	}

	/**
	 * Turns the 0 to 127 volume into the decibel gain {@code AudioPlayer} wants, where 0 dB is the
	 * file's own level and quieter is negative.
	 */
	static float gainDecibels(int volume)
	{
		int clamped = Math.max(1, Math.min(MAX_VOLUME, volume));

		return (float) (20.0 * Math.log10(clamped / (double) MAX_VOLUME));
	}

	private ChatSound soundFor(ChatTab tab)
	{
		switch (tab)
		{
			case GAME:
				return config.soundGame();

			case PUBLIC:
				return config.soundPublic();

			case PRIVATE:
				return config.soundPrivate();

			case CHANNEL:
				return config.soundChannel();

			case CLAN:
				return config.soundClan();

			case TRADE:
				return config.soundTrade();

			default:
				return ChatSound.OFF;
		}
	}

	/** Whether this message is one the local player sent, rather than one they received. */
	private boolean isFromLocalPlayer(ChatMessage event)
	{
		Player local = client.getLocalPlayer();

		if (local == null)
		{
			return false;
		}

		String sender = cleanName(event.getName());

		return !sender.isEmpty() && sender.equalsIgnoreCase(cleanName(local.getName()));
	}

	/** Strips the rank icons and the non-breaking spaces the game puts in display names. */
	private static String cleanName(String name)
	{
		return name == null ? "" : Text.removeTags(name).replace(' ', ' ').trim();
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
		String name = cleanName(event.getName());

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
