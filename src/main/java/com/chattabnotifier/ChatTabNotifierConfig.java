package com.chattabnotifier;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(ChatTabNotifierConfig.GROUP)
public interface ChatTabNotifierConfig extends Config
{
	String GROUP = "chattabnotifier";

	@ConfigSection(
		name = "Channels",
		description = "Which tabs light up. Game and Public are off by default because both run constantly",
		position = 1
	)
	String channelSection = "channelSection";

	@ConfigSection(
		name = "Appearance",
		description = "What the highlight looks like",
		position = 10
	)
	String appearanceSection = "appearanceSection";

	@ConfigSection(
		name = "Colours",
		description = "A colour per tab, so you can tell what arrived without reading it",
		position = 20,
		closedByDefault = true
	)
	String colourSection = "colourSection";

	@ConfigSection(
		name = "Behaviour",
		description = "When a tab counts as read again",
		position = 30,
		closedByDefault = true
	)
	String behaviourSection = "behaviourSection";

	@ConfigSection(
		name = "Notifications",
		description = "Optional tray, sound or window flash on top of the highlight. Off everywhere by default",
		position = 40,
		closedByDefault = true
	)
	String notificationSection = "notificationSection";

	@ConfigSection(
		name = "Sounds",
		description = "A sound per channel when a message arrives, and one for messages you send. Off everywhere by default",
		position = 45,
		closedByDefault = true
	)
	String soundSection = "soundSection";

	@ConfigSection(
		name = "Debugging",
		description = "Tools for working out why a tab did or did not light up, without waiting for real chat",
		position = 50,
		closedByDefault = true
	)
	String debugSection = "debugSection";

	// --- channels ---------------------------------------------------------------------------

	@ConfigItem(
		keyName = "notifyGame",
		name = "Game",
		description = "Highlight the Game tab. This one fires on nearly every action, so it will stay lit almost always",
		position = 1,
		section = "channelSection"
	)
	default boolean notifyGame()
	{
		return false;
	}

	@ConfigItem(
		keyName = "notifyPublic",
		name = "Public",
		description = "Highlight the Public tab. Anywhere busy will keep it lit",
		position = 2,
		section = "channelSection"
	)
	default boolean notifyPublic()
	{
		return false;
	}

	@ConfigItem(
		keyName = "notifyPrivate",
		name = "Private",
		description = "Highlight the Private tab when someone messages you. Messages you send are ignored",
		position = 3,
		section = "channelSection"
	)
	default boolean notifyPrivate()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyChannel",
		name = "Channel",
		description = "Highlight the Channel tab for friends chat",
		position = 4,
		section = "channelSection"
	)
	default boolean notifyChannel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyClan",
		name = "Clan",
		description = "Highlight the Clan tab, guest clan chat included",
		position = 5,
		section = "channelSection"
	)
	default boolean notifyClan()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyTrade",
		name = "Trade",
		description = "Highlight the Trade tab for trade and duel requests",
		position = 6,
		section = "channelSection"
	)
	default boolean notifyTrade()
	{
		return true;
	}

	// --- appearance -------------------------------------------------------------------------

	@ConfigItem(
		keyName = "shape",
		name = "Highlight",
		description = "An outline around the button, a translucent fill over it, or a bar along the bottom edge",
		position = 1,
		section = "appearanceSection"
	)
	default HighlightShape shape()
	{
		return HighlightShape.OUTLINE;
	}

	@ConfigItem(
		keyName = "pulse",
		name = "Pulse",
		description = "Fade the highlight in and out instead of holding it steady",
		position = 2,
		section = "appearanceSection"
	)
	default boolean pulse()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pulsePeriod",
		name = "Pulse length",
		description = "How long one fade in and out takes. Lower is more frantic",
		position = 3,
		section = "appearanceSection"
	)
	@Range(min = 200, max = 4000)
	@Units(Units.MILLISECONDS)
	default int pulsePeriod()
	{
		return 1200;
	}

	@ConfigItem(
		keyName = "thickness",
		name = "Thickness",
		description = "Width of the outline, or height of the underline bar",
		position = 4,
		section = "appearanceSection"
	)
	@Range(min = 1, max = 6)
	@Units(Units.PIXELS)
	default int thickness()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "cornerRadius",
		name = "Corner rounding",
		description = "Round the highlight to follow the shape of the tab button underneath it. Zero draws square corners",
		position = 5,
		section = "appearanceSection"
	)
	@Range(max = 8)
	@Units(Units.PIXELS)
	default int cornerRadius()
	{
		return 3;
	}

	// --- colours ----------------------------------------------------------------------------

	@Alpha
	@ConfigItem(
		keyName = "colourGame",
		name = "Game",
		description = "Highlight colour for the Game tab",
		position = 1,
		section = "colourSection"
	)
	default Color colourGame()
	{
		return Color.WHITE;
	}

	@Alpha
	@ConfigItem(
		keyName = "colourPublic",
		name = "Public",
		description = "Highlight colour for the Public tab",
		position = 2,
		section = "colourSection"
	)
	default Color colourPublic()
	{
		return new Color(0x00, 0xC8, 0xFF);
	}

	@Alpha
	@ConfigItem(
		keyName = "colourPrivate",
		name = "Private",
		description = "Highlight colour for the Private tab",
		position = 3,
		section = "colourSection"
	)
	default Color colourPrivate()
	{
		return new Color(0x3C, 0xFF, 0x7A);
	}

	@Alpha
	@ConfigItem(
		keyName = "colourChannel",
		name = "Channel",
		description = "Highlight colour for the Channel tab",
		position = 4,
		section = "colourSection"
	)
	default Color colourChannel()
	{
		return new Color(0xB4, 0x78, 0xFF);
	}

	@Alpha
	@ConfigItem(
		keyName = "colourClan",
		name = "Clan",
		description = "Highlight colour for the Clan tab",
		position = 5,
		section = "colourSection"
	)
	default Color colourClan()
	{
		return new Color(0xFF, 0x8C, 0x1A);
	}

	@Alpha
	@ConfigItem(
		keyName = "colourTrade",
		name = "Trade",
		description = "Highlight colour for the Trade tab",
		position = 6,
		section = "colourSection"
	)
	default Color colourTrade()
	{
		return new Color(0xFF, 0xD7, 0x00);
	}

	// --- behaviour --------------------------------------------------------------------------

	@ConfigItem(
		keyName = "suppressActiveTab",
		name = "Skip the open tab",
		description = "Do not highlight the tab you already have open, since the message is on screen in front of you",
		position = 1,
		section = "behaviourSection"
	)
	default boolean suppressActiveTab()
	{
		return true;
	}

	@ConfigItem(
		keyName = "treatAllTabAsRead",
		name = "All tab counts as read",
		description = "Treat sitting on the All tab as having read everything, so nothing highlights while you are on it. Leave this off if the point is to catch a message in the flood",
		position = 2,
		section = "behaviourSection"
	)
	default boolean treatAllTabAsRead()
	{
		return false;
	}

	@ConfigItem(
		keyName = "ignoreSystemMessages",
		name = "Ignore channel notices",
		description = "Skip joins, leaves and other notices inside a channel or clan, so only what somebody actually typed lights the tab. The Game tab is unaffected",
		position = 3,
		section = "behaviourSection"
	)
	default boolean ignoreSystemMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clearAfter",
		name = "Give up after",
		description = "Drop a highlight on its own once this long has passed without you opening the tab. Zero keeps it until you do",
		position = 4,
		section = "behaviourSection"
	)
	@Range(max = 600)
	@Units(Units.SECONDS)
	default int clearAfter()
	{
		return 0;
	}

	// --- notifications ----------------------------------------------------------------------

	@ConfigItem(
		keyName = "notificationGame",
		name = "Game",
		description = "Notification when the Game tab lights up",
		position = 1,
		section = "notificationSection"
	)
	default Notification notificationGame()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "notificationPublic",
		name = "Public",
		description = "Notification when the Public tab lights up",
		position = 2,
		section = "notificationSection"
	)
	default Notification notificationPublic()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "notificationPrivate",
		name = "Private",
		description = "Notification when the Private tab lights up",
		position = 3,
		section = "notificationSection"
	)
	default Notification notificationPrivate()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "notificationChannel",
		name = "Channel",
		description = "Notification when the Channel tab lights up",
		position = 4,
		section = "notificationSection"
	)
	default Notification notificationChannel()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "notificationClan",
		name = "Clan",
		description = "Notification when the Clan tab lights up",
		position = 5,
		section = "notificationSection"
	)
	default Notification notificationClan()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = "notificationTrade",
		name = "Trade",
		description = "Notification when the Trade tab lights up",
		position = 6,
		section = "notificationSection"
	)
	default Notification notificationTrade()
	{
		return Notification.OFF;
	}

	// --- sounds -----------------------------------------------------------------------------

	@ConfigItem(
		keyName = "soundGame",
		name = "Game",
		description = "Sound when a game message arrives",
		position = 1,
		section = "soundSection"
	)
	default ChatSound soundGame()
	{
		return ChatSound.OFF;
	}

	@ConfigItem(
		keyName = "soundPublic",
		name = "Public",
		description = "Sound when somebody speaks in public chat",
		position = 2,
		section = "soundSection"
	)
	default ChatSound soundPublic()
	{
		return ChatSound.OFF;
	}

	@ConfigItem(
		keyName = "soundPrivate",
		name = "Private",
		description = "Sound when somebody messages you privately",
		position = 3,
		section = "soundSection"
	)
	default ChatSound soundPrivate()
	{
		return ChatSound.OFF;
	}

	@ConfigItem(
		keyName = "soundChannel",
		name = "Channel",
		description = "Sound when a friends chat message arrives",
		position = 4,
		section = "soundSection"
	)
	default ChatSound soundChannel()
	{
		return ChatSound.OFF;
	}

	@ConfigItem(
		keyName = "soundClan",
		name = "Clan",
		description = "Sound when a clan message arrives",
		position = 5,
		section = "soundSection"
	)
	default ChatSound soundClan()
	{
		return ChatSound.OFF;
	}

	@ConfigItem(
		keyName = "soundTrade",
		name = "Trade",
		description = "Sound when a trade or duel request arrives",
		position = 6,
		section = "soundSection"
	)
	default ChatSound soundTrade()
	{
		return ChatSound.OFF;
	}

	@ConfigItem(
		keyName = "sendSound",
		name = "Message you send",
		description = "Sound when you send a message yourself, in any channel. A line you typed never plays a channel sound as well, so it cannot double up",
		position = 7,
		section = "soundSection"
	)
	default ChatSound sendSound()
	{
		return ChatSound.OFF;
	}

	@ConfigItem(
		keyName = "customSoundId",
		name = "Custom sound id",
		description = "The game sound effect played by the Custom sound id choice above. Any of the game's own effect ids works",
		position = 8,
		section = "soundSection"
	)
	@Range(min = 1, max = 20000)
	default int customSoundId()
	{
		return 2266;
	}

	@ConfigItem(
		keyName = "soundVolume",
		name = "Volume",
		description = "How loud these sounds are, from silent to full",
		position = 9,
		section = "soundSection"
	)
	@Range(max = 127)
	default int soundVolume()
	{
		return 96;
	}

	// --- debugging --------------------------------------------------------------------------

	@ConfigItem(
		keyName = "testCommand",
		name = "Enable ::chattab",
		description = "Type ::chattab in the chat box to fake a message in every enabled channel, ::chattab clan for one of them, ::chattab draw to light the tabs without going through chat at all, or ::chattab bounds to print what each tab occupies on screen. Only your own client sees any of it",
		position = 1,
		section = "debugSection"
	)
	default boolean testCommand()
	{
		return false;
	}

	@ConfigItem(
		keyName = "logDecisions",
		name = "Log decisions",
		description = "Write a line to the client log for every chat message, saying which tab it belongs to and why it did or did not light up. Very chatty - leave it off unless you are chasing a problem",
		position = 2,
		section = "debugSection"
	)
	default boolean logDecisions()
	{
		return false;
	}
}
