package com.chattabnotifier;

/**
 * The chatbox interface, sprite and client variable ids this plugin reads.
 *
 * <p>RuneLite names all of these in {@code net.runelite.api.gameval} - {@code InterfaceID.Chatbox},
 * {@code SpriteID.ChatTabButton} and {@code VarClientID.CHAT_VIEW}. Those classes hold nothing but
 * compile time constants, so they are stripped out of the runtime api jar the client ships and
 * cannot be compiled against without pulling the full jar from RuneLite's maven repository. javac
 * inlines the numbers either way, so naming them here produces exactly the same bytecode and keeps
 * the plugin buildable against the jars the client already downloaded.
 */
final class Chatbox
{
	/** Interface group of the chatbox. */
	static final int GROUP = 162;

	/** {@code SpriteID.ChatTabButton.SELECTED}, worn by the background of the open tab. */
	static final int SPRITE_SELECTED = 3053;

	/** {@code SpriteID.ChatTabButton.SELECTED_HOVERED}, the open tab with the mouse over it. */
	static final int SPRITE_SELECTED_HOVERED = 3054;

	/** {@code VarClientID.CHAT_VIEW}, the open tab numbered left to right from zero. */
	static final int VAR_CHAT_VIEW = 41;

	private Chatbox()
	{
	}

	/** Packs a child of the chatbox group into the component id {@code Client.getWidget} takes. */
	static int component(int child)
	{
		return (GROUP << 16) | child;
	}
}
