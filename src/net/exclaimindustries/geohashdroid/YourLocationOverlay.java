/**
 * YourLocationOverlay.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.graphics.Canvas;
import android.location.Location;

import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

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
    
    /** The last frame drawn. */
    private long mLastDrawnFrame = 0;
    
    /** The number of milliseconds between frames. */
    private static final long MILLIS_PER_FRAME = 100;
    
    public YourLocationOverlay() {
        // Initialize the first draw time.  Honestly, we don't really care when
        // EXACTLY we started, we just need a baseline.
        mFirstDrawTime = System.currentTimeMillis();
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
        // moved and we need to update as soon as possible.
        if(mLastDrawnLoc != null && mLastKnownLoc == null
                || mLastDrawnLoc == null && mLastKnownLoc != null
                || !mLastDrawnLoc.equals(mLastKnownLoc)) {
            mustRedraw = true;
        }
        
        // Then, figure out what frame we're on.
        curFrame = (when - mFirstDrawTime) / MILLIS_PER_FRAME;
        
        // Now, figure out what part of the animation cycle we're in.  We
        // basically need to determine when in the pulsing accuracy meter we
        // are (one second to fade in, one second delay, one second to fade out,
        // one second delay), as well as when in the blinking location indicator
        // we are (one second per state).  And, of course, we don't draw a thing
        // if the current location is null.
        if(mLastKnownLoc != null) {
            
        }
        
        // Finally, set all our new values and return whatever needs to be returned.
        mLastDrawnLoc = mLastKnownLoc;
        mLastDrawnFrame = curFrame;
        return mustRedraw;
    }

}
