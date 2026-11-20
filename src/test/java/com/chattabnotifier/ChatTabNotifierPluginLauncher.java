package com.chattabnotifier;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Runs a RuneLite client with this plugin loaded, for development. */
public class ChatTabNotifierPluginLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ChatTabNotifierPlugin.class);
		RuneLite.main(args);
	}
}
