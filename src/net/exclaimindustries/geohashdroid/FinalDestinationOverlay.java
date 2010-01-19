/**
 * FinalDestinationOverlay.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

/**
 * The FinalDestinationOverlay draws the final destination flag on the map.
 * 
 * @author Nicholas Killewald
 * 
 */
public class FinalDestinationOverlay extends Overlay {
    protected Drawable mDrawable;
    protected GeoPoint mDestination;
    protected Graticule mGraticule;
    protected Info mInfo;
    protected MainMap mParent;

    /**
     * Creates a new FinalDestinationOverlay.
     * 
     * @param d Drawable to draw as the overlay.  This is presumably a flag.
     * @param p an Info bundle describing where this destination is
     * @param parent parent MainMap which will pop up a dialog when this is tapped
     */
    public FinalDestinationOverlay(Drawable d, Info i, MainMap parent) {
        mDrawable = d;
        mInfo = i;
        mParent = parent;
        mDestination = i.getFinalDestination();
        mGraticule = i.getGraticule();
    }
    
    /**
     * Determines if a given GeoPoint (most likely, a tap) is somewhere on the
     * flag icon.  Or, put better, if the given tap should be handled by the
     * calling icon.
     * 
     * @param p point to check
     * @param mapView view from which a Projection can be retrieved
     * @return true if on the icon, false if not
     */
    protected boolean isPointOnIcon(GeoPoint p, MapView mapView) {
        // We need to check if the this is somewhere within the area of the
        // ICON.  Part of the icon (the flag tip) includes the point, sure, but
        // the user's going to be tapping the flag, most likely.
        Point iconPoint = getIconPosition(mapView.getProjection());
        Point tapPoint = mapView.getProjection().toPixels(p, null);
        
        // Now, determine if the tap was anywhere in the icon.
        if(tapPoint.x > iconPoint.x
                && tapPoint.x < iconPoint.x + mDrawable.getIntrinsicWidth()
                && tapPoint.y > iconPoint.y
                && tapPoint.y < iconPoint.y + mDrawable.getIntrinsicHeight())
        {
            return true;
        }
        return false;
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        super.draw(canvas, mapView, shadow);

        // All we're responsible for is coming up with a point and throwing it
        // to the protected method.
        Projection p = mapView.getProjection();

        // We need to offset the image to the left one half of its width and up
        // its entire height. So, let's do that.
        int x;
        int y;

        if (!shadow) {
            Point point = getIconPosition(p);
            x = point.x;
            y = point.y;
        } else {
            // x needs to be adjusted for the skew, depending on the sign.
            // TODO: Check the skewing algorithm; this can't possibly be right
            // in all cases (i.e. SHADOW_X_SKEW changing).
            float scalefactor = 1 - Math.abs(SHADOW_X_SKEW);
            x = (int)(p.toPixels(mDestination, null).x + (mDrawable
                    .getIntrinsicWidth() * scalefactor));
            y = (int)(p.toPixels(mDestination, null).y - (mDrawable
                    .getIntrinsicHeight() * SHADOW_Y_SCALE));
        }

        // And now we can draw!
        drawAt(canvas, mDrawable, x, y, shadow);
    }
    
    /**
     * Gets the top-left position of the icon (not the shadow).
     * 
     * @param p Projection from whence the position will come
     * @return the position as a Point
     */
    protected Point getIconPosition(Projection p) {
        return new Point(p.toPixels(mDestination, null).x - (mDrawable.getIntrinsicWidth() / 2),
                p.toPixels(mDestination, null).y - (mDrawable.getIntrinsicHeight()));
    }
    
    @Override
    public boolean onTap(GeoPoint p, MapView mapView) {
        // With a tap, we prompt to send it off to the Maps app.  To MainMap!
        if(isPointOnIcon(p, mapView))
        {
            // If this is on us, we need to act!  Give us a popup!
            mParent.showDialog(MainMap.DIALOG_SEND_TO_MAPS);
            return true;
        }
        else
            // If not, well, we don't!    
            return false;
    }
}
