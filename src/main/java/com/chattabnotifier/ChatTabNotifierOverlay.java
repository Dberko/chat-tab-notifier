package com.chattabnotifier;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Draws the highlight over whichever chat tab buttons have something unread. */
class ChatTabNotifierOverlay extends Overlay
{
	/** How dim a pulse gets at its lowest, so the tab never disappears entirely. */
	private static final float MIN_PULSE = 0.25f;

	/** A solid fill would bury the tab label, so the fill shape is always translucent. */
	private static final float FILL_ALPHA = 0.45f;

	private final Client client;
	private final ChatTabNotifierPlugin plugin;
	private final ChatTabNotifierConfig config;

	@Inject
	ChatTabNotifierOverlay(Client client, ChatTabNotifierPlugin plugin, ChatTabNotifierConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(Overlay.PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (plugin.getUnreadTabs().isEmpty())
		{
			return null;
		}

		HighlightShape shape = config.shape();
		int thickness = config.thickness();

		// Java takes the full arc diameter rather than a radius, so a 3 pixel corner is an arc of 6.
		int arc = config.cornerRadius() * 2;

		float strength = config.pulse()
			? pulseFactor(System.currentTimeMillis(), config.pulsePeriod())
			: 1f;

		if (shape == HighlightShape.FILL)
		{
			strength *= FILL_ALPHA;
		}

		graphics.setStroke(new BasicStroke(thickness));

		// On a button only 16 pixels tall an unsmoothed corner reads as a staircase, so round corners
		// get antialiasing. The hint belongs to the shared Graphics2D, so it goes back afterwards.
		Object priorHint = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);

		if (arc > 0)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		}

		try
		{
			for (ChatTab tab : plugin.getUnreadTabs().keySet())
			{
				Rectangle bounds = faceOf(tab);

				if (bounds == null)
				{
					continue;
				}

				graphics.setColor(fade(plugin.colourFor(tab), strength));
				draw(graphics, shape, bounds, thickness, arc);
			}
		}
		finally
		{
			if (arc > 0)
			{
				graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					priorHint == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : priorHint);
			}
		}

		return null;
	}

	/**
	 * Where to draw a tab's highlight, or null when there is nowhere to draw it.
	 *
	 * <p>The tab's container widget is a few pixels taller than the button face inside it, which
	 * left the highlight hanging below the button. The face is the background sprite, so that is
	 * what gets measured, with the container kept as a fallback in case a client build stops
	 * giving the sprite bounds of its own.
	 */
	private Rectangle faceOf(ChatTab tab)
	{
		Widget container = client.getWidget(tab.getButtonId());

		// The tabs go away with the whole chatbox during cutscenes and the like. The tab stays
		// flagged; there is just nowhere to draw it until the chatbox is back.
		if (container == null || container.isHidden())
		{
			return null;
		}

		Widget face = client.getWidget(tab.getGraphicId());
		Rectangle bounds = face != null && !face.isHidden() ? face.getBounds() : null;

		if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
		{
			bounds = container.getBounds();
		}

		return bounds != null && bounds.width > 0 && bounds.height > 0 ? bounds : null;
	}

	private static void draw(Graphics2D graphics, HighlightShape shape, Rectangle bounds,
		int thickness, int arc)
	{
		switch (shape)
		{
			case FILL:
				if (arc > 0)
				{
					graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc);
				}
				else
				{
					graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
				}

				break;

			case UNDERLINE:
				int barHeight = Math.min(thickness * 2, bounds.height);
				int barY = bounds.y + bounds.height - barHeight;

				if (arc > 0)
				{
					// Clip to the button's own rounded outline so the bar curves in at its ends
					// instead of overhanging the corners. clip() intersects, so the overlay's own
					// clip is kept.
					Shape priorClip = graphics.getClip();
					graphics.clip(new RoundRectangle2D.Float(bounds.x, bounds.y,
						bounds.width, bounds.height, arc, arc));
					graphics.fillRect(bounds.x, barY, bounds.width, barHeight);
					graphics.setClip(priorClip);
				}
				else
				{
					graphics.fillRect(bounds.x, barY, bounds.width, barHeight);
				}

				break;

			case OUTLINE:
			default:
				// A stroke straddles the line it is drawn along, so pull the rectangle in by half of
				// it to keep the whole border inside the button.
				int inset = thickness / 2;

				if (arc > 0)
				{
					graphics.drawRoundRect(bounds.x + inset, bounds.y + inset,
						bounds.width - thickness, bounds.height - thickness, arc, arc);
				}
				else
				{
					graphics.drawRect(bounds.x + inset, bounds.y + inset,
						bounds.width - thickness, bounds.height - thickness);
				}

				break;
		}
	}

	/** A cosine so the pulse eases at both ends rather than snapping between bright and dim. */
	static float pulseFactor(long nowMillis, int periodMillis)
	{
		if (periodMillis <= 0)
		{
			return 1f;
		}

		double phase = (nowMillis % periodMillis) / (double) periodMillis;
		double wave = 0.5 - 0.5 * Math.cos(phase * 2 * Math.PI);

		return (float) (MIN_PULSE + (1 - MIN_PULSE) * wave);
	}

	static Color fade(Color colour, float factor)
	{
		if (factor >= 1f)
		{
			return colour;
		}

		int alpha = Math.max(0, Math.min(255, Math.round(colour.getAlpha() * factor)));

		return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
	}
}
