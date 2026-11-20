package com.chattabnotifier;

import java.awt.Color;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import net.runelite.api.ChatMessageType;

/**
 * Checks the parts that hold no client state: which tab each message type lands on, the chat view
 * fallback, and the pulse maths. Run with {@code .\build.ps1 -Verify}.
 */
public class SelfCheck
{
	private static int failures;

	public static void main(String[] args)
	{
		checkConfigVisibility();
		checkTabMapping();
		checkTestCommand();
		checkChatViewFallback();
		checkPulse();
		checkFade();

		System.out.println();

		if (failures > 0)
		{
			System.out.println(failures + " check(s) failed");
			System.exit(1);
		}

		System.out.println("all checks passed");
	}

	/**
	 * RuneLite implements the config interface as a dynamic proxy that lives in another package, so
	 * every type the interface hands back has to be public. A package private one is not a compile
	 * error: it throws {@code IllegalAccessError} from inside the client's draw loop the first time
	 * a tab lights up, which freezes the client rather than disabling the plugin.
	 */
	private static void checkConfigVisibility()
	{
		System.out.println("-- config proxy visibility --");

		expect("the config interface itself is public", Boolean.TRUE,
			Boolean.valueOf(Modifier.isPublic(ChatTabNotifierConfig.class.getModifiers())));

		for (Method method : ChatTabNotifierConfig.class.getDeclaredMethods())
		{
			Class<?> returned = method.getReturnType();

			if (returned.isPrimitive())
			{
				continue;
			}

			if (Modifier.isPublic(returned.getModifiers()))
			{
				System.out.println("  ok   " + method.getName() + " returns public "
					+ returned.getSimpleName());
				continue;
			}

			fail(method.getName() + " returns " + returned.getName()
				+ ", which the config proxy cannot reach");
		}
	}

	private static void checkTabMapping()
	{
		System.out.println("-- message type to tab --");

		Map<ChatMessageType, ChatTab> mapping = ChatTabNotifierPlugin.tabMapping();

		expect("public chat", ChatTab.PUBLIC, mapping.get(ChatMessageType.PUBLICCHAT));
		expect("mod chat", ChatTab.PUBLIC, mapping.get(ChatMessageType.MODCHAT));
		expect("private chat", ChatTab.PRIVATE, mapping.get(ChatMessageType.PRIVATECHAT));
		expect("jmod private chat", ChatTab.PRIVATE, mapping.get(ChatMessageType.MODPRIVATECHAT));
		expect("friends chat", ChatTab.CHANNEL, mapping.get(ChatMessageType.FRIENDSCHAT));
		expect("clan chat", ChatTab.CLAN, mapping.get(ChatMessageType.CLAN_CHAT));
		expect("guest clan chat", ChatTab.CLAN, mapping.get(ChatMessageType.CLAN_GUEST_CHAT));
		expect("trade request", ChatTab.TRADE, mapping.get(ChatMessageType.TRADEREQ));
		expect("duel request", ChatTab.TRADE, mapping.get(ChatMessageType.CHALREQ_TRADE));
		expect("game message", ChatTab.GAME, mapping.get(ChatMessageType.GAMEMESSAGE));
		expect("filtered game message", ChatTab.GAME, mapping.get(ChatMessageType.SPAM));

		// Your own outgoing lines and the dialogue boxes must not light anything, or the plugin
		// would flag the Private tab every time you answer someone.
		expect("own private message", null, mapping.get(ChatMessageType.PRIVATECHATOUT));
		expect("own trade offer", null, mapping.get(ChatMessageType.TRADE_SENT));
		expect("npc dialogue", null, mapping.get(ChatMessageType.DIALOG));
		expect("mesbox", null, mapping.get(ChatMessageType.MESBOX));
		expect("npc overhead", null, mapping.get(ChatMessageType.NPC_SAY));

		// The All tab cannot be unread, so nothing may map to it.
		for (Map.Entry<ChatMessageType, ChatTab> entry : mapping.entrySet())
		{
			if (entry.getValue() == ChatTab.ALL)
			{
				fail(entry.getKey() + " maps to the All tab");
			}
		}
	}

	private static void checkTestCommand()
	{
		System.out.println("-- test command --");

		Map<ChatMessageType, ChatTab> mapping = ChatTabNotifierPlugin.tabMapping();

		// A faked message has to land on the tab the command claims, or the test tool lies about
		// what it proved.
		for (ChatTab tab : ChatTab.values())
		{
			if (tab == ChatTab.ALL)
			{
				continue;
			}

			expect("a faked " + tab.getLabel() + " message routes back to " + tab.getLabel(),
				tab, mapping.get(ChatTabNotifierPlugin.sampleType(tab)));
		}

		expect("pm", ChatTab.PRIVATE, ChatTabNotifierPlugin.byName("pm"));
		expect("cc", ChatTab.CLAN, ChatTabNotifierPlugin.byName("cc"));
		expect("fc", ChatTab.CHANNEL, ChatTabNotifierPlugin.byName("fc"));
		expect("odd case", ChatTab.CLAN, ChatTabNotifierPlugin.byName("ClAn"));
		expect("surrounding spaces", ChatTab.TRADE, ChatTabNotifierPlugin.byName("  trade "));
		expect("nonsense", null, ChatTabNotifierPlugin.byName("banana"));
	}

	private static void checkChatViewFallback()
	{
		System.out.println("-- chat view fallback --");

		expect("view 0", ChatTab.ALL, ChatTab.fromChatView(0));
		expect("view 1", ChatTab.GAME, ChatTab.fromChatView(1));
		expect("view 3", ChatTab.PRIVATE, ChatTab.fromChatView(3));
		expect("view 6", ChatTab.TRADE, ChatTab.fromChatView(6));
		expect("view 7", null, ChatTab.fromChatView(7));
		expect("view -1", null, ChatTab.fromChatView(-1));
	}

	private static void checkPulse()
	{
		System.out.println("-- pulse --");

		int period = 1000;

		expect("dimmest at the start of a cycle", 0.25f, ChatTabNotifierOverlay.pulseFactor(0, period));
		expect("brightest at the half way point", 1.0f, ChatTabNotifierOverlay.pulseFactor(500, period));
		expect("no period means no pulse", 1.0f, ChatTabNotifierOverlay.pulseFactor(1234, 0));

		for (long now = 0; now < 3000; now += 7)
		{
			float factor = ChatTabNotifierOverlay.pulseFactor(now, period);

			if (factor < 0.25f || factor > 1.0f)
			{
				fail("pulse left its range at " + now + ": " + factor);
			}
		}

		System.out.println("  ok   pulse stays within range over three cycles");
	}

	private static void checkFade()
	{
		System.out.println("-- fade --");

		Color opaque = new Color(0x40, 0x80, 0xC0);

		expect("full strength is untouched", Integer.valueOf(255),
			Integer.valueOf(ChatTabNotifierOverlay.fade(opaque, 1f).getAlpha()));
		expect("half strength halves alpha", Integer.valueOf(128),
			Integer.valueOf(ChatTabNotifierOverlay.fade(opaque, 0.5f).getAlpha()));
		expect("rgb survives a fade", "4080c0",
			Integer.toHexString(ChatTabNotifierOverlay.fade(opaque, 0.5f).getRGB() & 0xFFFFFF));

		Color translucent = new Color(0x40, 0x80, 0xC0, 100);

		expect("a translucent colour fades from its own alpha", Integer.valueOf(50),
			Integer.valueOf(ChatTabNotifierOverlay.fade(translucent, 0.5f).getAlpha()));
	}

	private static void expect(String what, Object expected, Object actual)
	{
		if (expected == null ? actual == null : expected.equals(actual))
		{
			System.out.println("  ok   " + what + " -> " + actual);
			return;
		}

		fail(what + ": expected " + expected + ", got " + actual);
	}

	private static void expect(String what, float expected, float actual)
	{
		if (Math.abs(expected - actual) < 0.0001f)
		{
			System.out.println("  ok   " + what + " -> " + actual);
			return;
		}

		fail(what + ": expected " + expected + ", got " + actual);
	}

	private static void fail(String message)
	{
		System.out.println("  FAIL " + message);
		failures++;
	}
}
