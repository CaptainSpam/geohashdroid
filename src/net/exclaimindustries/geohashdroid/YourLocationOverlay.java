/**
 * YourLocationOverlay.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.location.Location;

import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

/**
 * The <code>YourLocationOverlay</code> is an overlay that represents your
 * current location, and has data fed into it via <code>MainMap</code>'s connection to
 * <code>GeohashService</code>.  Yes, I called it YourLocationOverlay.  I needed
 * an overlay that didn't make its own LocationManager calls.
 * 
 * @author Nicholas Killewald
 *
 */
public class YourLocationOverlay extends Overlay {

    /** The last-known location we're drawing. */
    private Location mLastKnownLoc;
    /**
     * The last location we drew.  If this is different from the last KNOWN, we
     * return true in the draw method, indicating we need to redraw immediately,
     * as the user's moved.
     */
    private Location mLastDrawnLoc;
    
    /** When this whole thing started. */
    private long mFirstDrawTime;
    
    /** The number of milliseconds between frames. */
    private static final long MILLIS_PER_FRAME = 100;

    private static Paint mAccuracyFillPaint;
    private static Paint mAccuracyStrokePaint;
    
    /**
     * The colors for the fading accuracy meter.  Should go from fully clear
     * up to 0x18 in ten steps (eleven frames).  Should also follow a 
     * logarithmic (or kinda-sorta logarithmic, at least) progression so that it
     * eases out slightly as it reaches the end (so, not a linear progression).
     * Looks fancier that way.
     *
     * Also, we define these here because we know they'll always be the same
     * values, meaning we're not forcing the phone to calculate logarithms
     * on every frame needlessly.
     */
    private static final int[] ACCURACY_FRAMES = { 0x006666ff, 0x076666ff, 0x0b6666ff, 0x0e6666ff, 0x106666ff, 0x126666ff, 0x136666ff, 0x156666ff, 0x166666ff, 0x176666ff, 0x186666ff };
    
    public YourLocationOverlay() {
        // Initialize the first draw time.  Honestly, we don't really care when
        // EXACTLY we started, we just need a baseline.
        mFirstDrawTime = System.currentTimeMillis();
        
        // Make our paints, too.
        if(mAccuracyFillPaint == null) {
            mAccuracyFillPaint = new Paint();
            mAccuracyFillPaint.setAntiAlias(true);
            mAccuracyFillPaint.setStyle(Style.FILL);
            // Color gets reset on each frame.
        }
        
        if(mAccuracyStrokePaint == null) {
            mAccuracyStrokePaint = new Paint();
            mAccuracyStrokePaint.setAntiAlias(true);
            mAccuracyStrokePaint.setStyle(Style.STROKE);
            mAccuracyStrokePaint.setColor(0xff6666ff);
        }
    }
    
    /**
     * Updates the last known location.  On the next redraw, this overlay will
     * move itself to accommodate.
     * 
     * @param newLoc
     */
    public void setLocation(Location newLoc) {
        mLastKnownLoc = newLoc;
    }
    
    @Override
    public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {
        // We don't draw shadows.
        if(shadow) return false;
        
        boolean mustRedraw = false;
        long curFrame;
        
        // First off, check to see if we need an immediate redraw.  If what we
        // last drew and what we're about to draw are identical, we don't need
        // an immediate redraw.  If they're different, we do, as the user's
        // moved and we need to update as soon as possible.  The first compare
        // is there to prevent NullPointerExceptions.
        if(mLastDrawnLoc != null && mLastKnownLoc != null
                && (mLastDrawnLoc != null && mLastKnownLoc == null
                || mLastDrawnLoc == null && mLastKnownLoc != null
                || !mLastDrawnLoc.equals(mLastKnownLoc))) {
            mustRedraw = true;
        }
        
        
        // Now, figure out what part of the animation cycle we're in.  We
        // basically need to determine when in the pulsing accuracy meter we
        // are (one second to fade in, one second delay, one second to fade out,
        // one second delay), as well as when in the blinking location indicator
        // we are (one second per state).  And, of course, we don't draw a thing
        // if the current location is null.
        if(mLastKnownLoc != null) {
            int curAccuracyFrame;
            boolean curIndicatorBlink;

            // Figure out what frame we're on.  This'll only really fail us if
            // we overflow a long, and even then it's a one-frame glitch.
            curFrame = (when - mFirstDrawTime) / MILLIS_PER_FRAME;

            // Frame 0: first fadein (0)
            // Frames 0-9: fadein (0-9)
            // Frame 10: fully in (10)
            // Frames 10-19: hold (10)
            // Frame 20: first fadeout (9)
            // Frames 20-28: fadeout (9-1)
            // Frame 29: fully out (0)
            // Frames 29-39: hold (0)
            curAccuracyFrame = (int)(curFrame % 40);
            if(curAccuracyFrame < 10)
                mAccuracyFillPaint.setColor(ACCURACY_FRAMES[curAccuracyFrame]);
            else if(curAccuracyFrame >= 10 && curAccuracyFrame < 20)
                mAccuracyFillPaint.setColor(ACCURACY_FRAMES[10]);
            else if(curAccuracyFrame >= 20 && curAccuracyFrame < 30)
                mAccuracyFillPaint.setColor(ACCURACY_FRAMES[29 - curAccuracyFrame]);
            else
                mAccuracyFillPaint.setColor(ACCURACY_FRAMES[0]);

            // Frames 0-9: blink on
            // Frames 10-19: blink off
            if(curFrame % 20 < 10)
                curIndicatorBlink = true;
            else
                curIndicatorBlink = false;
         
            // Next, figure out the radius of the circle.
            Projection projection = mapView.getProjection();
            double latitude = mLastKnownLoc.getLatitude();
            double longitude = mLastKnownLoc.getLongitude();
            float accuracy = mLastKnownLoc.getAccuracy();
            
            float[] result = new float[1];

            // The distance that one degree represents under the current
            // projection is the key to this whole mess.  We use that to
            // determine how far a pixel is and use the location's accuracy
            // field to determine how big a circle we need to draw.
            Location.distanceBetween(latitude, longitude, latitude, longitude + 1, result);
            float longitudeLineDistance = result[0];
            
            // And DRAW!
            
        }
        
        // Finally, set all our new values and return whatever needs to be returned.
        mLastDrawnLoc = mLastKnownLoc;
        return mustRedraw;
    }

}
