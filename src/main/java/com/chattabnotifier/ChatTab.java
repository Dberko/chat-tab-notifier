package com.chattabnotifier;

import java.util.Locale;

/**
 * The chat tab buttons along the bottom left of the chatbox, in the order they sit on screen.
 *
 * <p>That order is load bearing: when no tab reports the selected sprite, {@code CHAT_VIEW} is read
 * instead, and its values run left to right over these same tabs.
 *
 * <p>The child numbers are the chatbox children RuneLite calls {@code CHAT_ALL}, {@code
 * CHAT_ALL_GRAPHIC} and so on - see {@link Chatbox} for why they are spelled out here.
 */
enum ChatTab
{
	ALL("All", 4, 5),
	GAME("Game", 7, 8),
	PUBLIC("Public", 11, 12),
	PRIVATE("Private", 15, 16),
	CHANNEL("Channel", 19, 20),
	CLAN("Clan", 23, 24),
	TRADE("Trade", 27, 28);

	private final String label;
	private final int buttonChild;
	private final int graphicChild;

	ChatTab(String label, int buttonChild, int graphicChild)
	{
		this.label = label;
		this.buttonChild = buttonChild;
		this.graphicChild = graphicChild;
	}

	/** The name printed on the tab, as the game spells it. */
	String getLabel()
	{
		return label;
	}

	/**
	 * The basename of this tab's sound file: the tab's own name in lower case, so the Private tab
	 * plays {@code private.wav}. Derived from this plugin's own labels, never from anything typed.
	 */
	String getSoundName()
	{
		return label.toLowerCase(Locale.ROOT);
	}

	/** The whole button, which is what gets highlighted. */
	int getButtonId()
	{
		return Chatbox.component(buttonChild);
	}

	/** The button's background sprite, which says whether this tab is the open one. */
	int getGraphicId()
	{
		return Chatbox.component(graphicChild);
	}

	static ChatTab fromChatView(int chatView)
	{
		ChatTab[] tabs = values();

		return chatView >= 0 && chatView < tabs.length ? tabs[chatView] : null;
	}
}
