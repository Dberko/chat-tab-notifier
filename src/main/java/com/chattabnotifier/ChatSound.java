package com.chattabnotifier;

/**
 * A sound the plugin can play, taken from the game's own sound effects so nothing has to be shipped
 * with the plugin and everything plays through the engine the rest of the game uses.
 *
 * <p>Public on purpose, for the same reason as {@link HighlightShape}: the config interface hands
 * this back, and RuneLite implements that interface as a dynamic proxy in another package, which
 * cannot reach a package private type.
 *
 * <p>The two negative ids are spelled out as literals rather than as the named constants below,
 * because Java forbids an enum constant's arguments from referring to the enum's own static fields.
 */
public enum ChatSound
{
	OFF("Off", -1),
	BOOP("Boop", 2266),
	DING_A_LING("Ding-a-ling", 3925),
	BELL_DING("Bell ding", 3813),
	BELL_DONG("Bell dong", 3817),
	COIN_TINKLE("Coin tinkle", 3924),
	BLOOP("Bloop", 3928),
	ANVIL_TINK("Anvil tink", 3790),
	DOOR_OPEN("Door open", 62),
	DOOR_CLOSE("Door close", 60),
	WOOSH("Woosh", 200),
	CUSTOM("Custom sound id", -2);

	/** Plays nothing at all. */
	static final int SILENT = -1;

	/** Takes its id from the custom sound id setting instead of this table. */
	static final int BY_CONFIG = -2;

	private final String label;
	private final int soundEffectId;

	ChatSound(String label, int soundEffectId)
	{
		this.label = label;
		this.soundEffectId = soundEffectId;
	}

	/** The game's sound effect id, or one of the two negative markers above. */
	int getSoundEffectId()
	{
		return soundEffectId;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
