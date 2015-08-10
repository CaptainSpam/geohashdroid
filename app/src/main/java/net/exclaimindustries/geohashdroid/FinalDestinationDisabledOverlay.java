/**
 * FinalDestinationDisabledOverlay.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
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

    /**
     * Creates a new disabled final destination overlay.
     * 
     * @param d Drawable to use as the flag
     * @param i Info bundle within this overlay (defines where it is)
     * @param parent parent MainMap which will pop up a dialog when this is tapped
     */
    public FinalDestinationDisabledOverlay(Drawable d, Info i, MainMap parent) {
        super(d, i, parent);
    }

    @Override
    public boolean onTap(GeoPoint p, MapView mapView) {
        // Disabled destination overlays can be tapped.  This seems more than a
        // bit counterintuitive, given the name, but it makes sense.  Trust me.
        if(isPointOnIcon(p, mapView))
        {
            // If this is on us, we need to act!  Give us a popup!
            mParent.showSwitchGraticulePrompt(mInfo);
            return true;
        }
        else
            // If not, well, we don't!    
            return false;
    }
    
}
