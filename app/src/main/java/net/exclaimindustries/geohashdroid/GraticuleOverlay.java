/**
 * GraticuleOverlay.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import net.exclaimindustries.geohashdroid.util.Graticule;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;

import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

/**
 * Any GraticuleOverlay is expected to be able to draw itself when given only a
 * Graticule object. It also comes with a couple handy methods for the most
 * common graticule-drawing extravaganzas, drawing an outline and drawing the
 * filled-in area, if given a Paint object with which to draw.
 * 
 * @author Nicholas Killewald
 * 
 */
public abstract class GraticuleOverlay extends Overlay {
    /**
     * Implement this to draw whatever needs to be drawn for this overlay.
     * 
     * @param c
     *            Canvas on which to draw
     * @param pr
     *            Projection to use to get coordinates
     * @param g
     *            the Graticule to draw
     */
    protected abstract void drawGraticule(Canvas c, Projection pr, Graticule g);

    /**
     * Convenience method to draw the outline of the given graticule on the
     * canvas. Override it if you really really want to.
     * 
     * @param c
     *            Canvas on which to draw
     * @param pr
     *            Projection to use to get the proper coordinates
     * @param g
     *            Graticule to draw
     * @param p
     *            Paint to use to draw
     */
    protected void drawGraticuleOutline(Canvas c, Projection pr, Graticule g,
            Paint p) {
        if (g == null) {
            return;
        }

        // Now, get two points out of the deal.
        Point topleft = pr.toPixels(g.getTopLeft(), null);
        Point bottomright = pr.toPixels(g.getBottomRight(), null);

        // Then, draw the line with the Paint supplied.
        c.drawLine(topleft.x, topleft.y, bottomright.x, topleft.y, p);
        c.drawLine(bottomright.x, topleft.y, bottomright.x, bottomright.y, p);
        c.drawLine(bottomright.x, bottomright.y, topleft.x, bottomright.y, p);
        c.drawLine(topleft.x, bottomright.y, topleft.x, topleft.y, p);
    }

    /**
     * Convenience method to draw the fill of the given graticule on the canvas.
     * Override it if you really really want to.
     * 
     * @param c
     *            Canvas on which to draw
     * @param pr
     *            Projection to use to get the proper coordinates
     * @param g
     *            Graticule to draw
     * @param p
     *            Paint to use to draw
     */
    protected void drawGraticuleFill(Canvas c, Projection pr, Graticule g,
            Paint p) {
        if (g == null) {
            return;
        }

        // And again, two points.
        Point topleft = pr.toPixels(g.getTopLeft(), null);
        Point bottomright = pr.toPixels(g.getBottomRight(), null);

        // And finally, draw it out.
        c.drawRect(new Rect(topleft.x, topleft.y, bottomright.x,
                        bottomright.y), p);
    }
}
