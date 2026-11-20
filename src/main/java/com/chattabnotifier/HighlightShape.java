package com.chattabnotifier;

/**
 * How an unread tab is marked.
 *
 * <p>Public on purpose: the config interface hands this back, and RuneLite implements that interface
 * as a dynamic proxy in another package, which cannot reach a package private return type. Hiding it
 * costs an {@code IllegalAccessError} thrown from inside the client's draw loop.
 */
public enum HighlightShape
{
	OUTLINE("Outline"),
	FILL("Filled glow"),
	UNDERLINE("Underline");

	private final String label;

	HighlightShape(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
