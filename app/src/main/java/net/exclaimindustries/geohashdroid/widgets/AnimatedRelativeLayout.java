/*
 * AnimatedRelativeLayout.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/**
 * This is simply a RelativeLayout with properties that can be more easily
 * animated for things like, say, entering and exiting the screen.
 */
public class AnimatedRelativeLayout
        extends RelativeLayout {


    public AnimatedRelativeLayout(Context context) {
        super(context);
    }

    public AnimatedRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AnimatedRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Gettable property for a fractional X coordinate.
     *
     * @return the current fractional X position
     */
    public float getXFraction() {
        if(getWidth() == 0) return 0;
        else return getX() / getWidth();
    }

    /**
     * Settable property for a fractional X coordinate.
     *
     * @param xFraction the new fractional X position
     */
    public void setXFraction(float xFraction) {
        int width = getWidth();
        setX(width > 0 ? xFraction * width : -9999);
    }

    /**
     * Gettable property for a fractional Y coordinate.
     *
     * @return the current fractional Y position
     */
    public float getYFraction() {
        if(getHeight() == 0) return 0;
        else return getY() / getHeight();
    }

    /**
     * Settable property for a fractional Y coordinate.
     *
     * @param yFraction the new fractional Y position
     */
    public void setYFraction(float yFraction) {
        int height = getHeight();
        setY(height > 0 ? yFraction * height : -9999);
    }
}
