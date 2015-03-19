/**
 * ZoomChangeOverlay.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.tools;

import android.graphics.Canvas;
import android.util.Log;

import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;

/**
 * The ZoomChangeOverlay is, more or less, a workaround to the fact that Android
 * has no callback to determine if a MapView has been zoomed or not.  This
 * becomes a problem with the multitouch map update, where pinch-zooming will
 * zoom without going through the ZoomControls mechanisms.
 * 
 * In effect, this overlay just waits for any draw() callback.  If it gets one,
 * it checks the zoom and sends a callback if it changed.
 * 
 * @author Nicholas Killewald
 *
 */
public class ZoomChangeOverlay extends Overlay {
    
    private static final String DEBUG_TAG = "ZoomChangeOverlay";
    private int mPrevZoom = -1;
    
    private ZoomChangeObserver mObserver;
    
    public ZoomChangeOverlay(ZoomChangeObserver observer) {
        mObserver = observer;
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        super.draw(canvas, mapView, shadow);
        
        if(shadow) return;
        
        // We need to sync at this point so we know that we won't be checking or
        // updating mPrevZoom while we're already checking/updating them.
        synchronized(this)
        {
            // Check the zoom level.  We only do the callback if this is different.
            int newZoom = mapView.getZoomLevel();
            if(newZoom != mPrevZoom)
            {
                Log.d(DEBUG_TAG, "Zoom level changed from " + mPrevZoom + " to " + newZoom);
                // Also, we only do the callback after we know the initial zoom.
                if(mPrevZoom != -1 && mObserver != null)
                    mObserver.zoomChanged(mapView, mPrevZoom, newZoom);
                
                mPrevZoom = newZoom;
            }
        }
    }
    
    /**
     * Interface that gets called upon any time the zoom changes.
     * 
     * @author Nicholas Killewald
     */
    public interface ZoomChangeObserver {
        /**
         * Indicates the zoom has changed.  This is called after that's
         * happened, so the MapView will already be in the new zoomed position.
         * 
         * @param mapView the MapView that got zoomed
         * @param prevZoom the previous zoom level
         * @param newZoom the new zoom level
         */
        public void zoomChanged(MapView mapView, int prevZoom, int newZoom);
    }
}
