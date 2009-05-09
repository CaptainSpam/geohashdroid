/**
 * GraticuleOverlay.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;

import com.google.android.maps.GeoPoint;
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
     * <code>RectCoords</code> just stashes the coordinates of the rectangle on
     * which a graticule will be drawn, since we can't get the raw values from
     * an Android Rect object.
     * 
     * @author Nicholas Killewald
     */
    protected class RectCoords {
        int top;
        int bottom;
        int left;
        int right;

        /**
         * Build us up a RectCoords!
         * 
         * @param left
         *            Left!
         * @param top
         *            Top!
         * @param right
         *            Right!
         * @param bottom
         *            Bottom!
         */
        public RectCoords(int left, int top, int right, int bottom) {
            this.top = top;
            this.bottom = bottom;
            this.left = left;
            this.right = right;
        }
    }

    /**
     * Forms a RectCoords object from the given Graticule.
     * 
     * @param g
     * @return a RectCoords from the graticule.
     */
    protected RectCoords getRectFromGraticule(Graticule g) {
        int top;
        int bottom;
        int left;
        int right;

        if (g.isSouth()) {
            top = (-1 * g.getLatitude()) * 1000000;
            bottom = (-1 * (g.getLatitude() + 1)) * 1000000;
        } else {
            top = (g.getLatitude() + 1) * 1000000;
            bottom = g.getLatitude() * 1000000;
        }

        if (g.isWest()) {
            left = (-1 * g.getLongitude()) * 1000000;
            right = (-1 * (g.getLongitude() + 1)) * 1000000;
        } else {
            left = (g.getLongitude() + 1) * 1000000;
            right = g.getLongitude() * 1000000;
        }

        return new RectCoords(left, top, right, bottom);
    }

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

        // First, the rectangle. The following aren't the coordinates of the
        // rectangle, they're the offsets of the GeoPoint.
        RectCoords rc = getRectFromGraticule(g);

        // Now, get two points out of the deal.
        Point topleft = pr.toPixels(new GeoPoint(rc.top, rc.left), null);
        Point bottomright = pr
                .toPixels(new GeoPoint(rc.bottom, rc.right), null);

        // Last, draw the line with the Paint supplied.
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

        // Again, first, the rectangle.
        RectCoords rc = getRectFromGraticule(g);

        // And again, two points.
        Point topleft = pr.toPixels(new GeoPoint(rc.top, rc.left), null);
        Point bottomright = pr
                .toPixels(new GeoPoint(rc.bottom, rc.right), null);

        // And finally, draw it out.
        c
                .drawRect(new Rect(topleft.x, topleft.y, bottomright.x,
                        bottomright.y), p);
    }
}
