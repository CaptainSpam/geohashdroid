/**
 * GraticuleMapView.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.google.android.maps.MapView;

/**
 * The <code>GraticuleMapView</code> is a simple override of MapView to allow it
 * to know what to do with trackball events. Which is to say, make it handle the
 * trackball BEFORE sending things off to the outline overlay.
 * 
 * @author Nicholas Killewald
 */
public class GraticuleMapView extends MapView {
    private GraticuleOutlineOverlay mGoo = null;

    public GraticuleMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOutlineOverlay(GraticuleOutlineOverlay goo) {
        this.mGoo = goo;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.google.android.maps.MapView#onTrackballEvent(android.view.MotionEvent
     * )
     */
    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        boolean toReturn = super.onTrackballEvent(event);

        // AFTER everything's handled, THEN we let the outline overlay know
        // what's going on.
        if (mGoo != null) {
            mGoo.updateGraticule(this);
        }

        return toReturn;
    }
}
