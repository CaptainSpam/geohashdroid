/**
 * FinalDestinationDisabledOverlay.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;

import android.graphics.drawable.Drawable;

/**
 * The FinalDestinationDisabledOverlay draws a greyed-out and translucent flag
 * which can be tapped to change the active graticule.
 * 
 * @author Nicholas Killewald
 */
public class FinalDestinationDisabledOverlay extends FinalDestinationOverlay {

    public FinalDestinationDisabledOverlay(Drawable d, Info i) {
        super(d, i);
    }

    @Override
    public boolean onTap(GeoPoint p, MapView mapView) {
        if(isPointOnIcon(p, mapView))
            // If this isn't even on us, forget it.
            return false;
        
        // TODO: Do something!   
        return false;
    }
    
}
