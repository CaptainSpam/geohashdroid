/**
 * GraticuleMapInfoBox.java
 * Copyright (C)2012 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import net.exclaimindustries.geohashdroid.util.Graticule;
import android.content.Context;
import android.location.Location;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The GraticuleMapInfoBox displays the InfoBox on the graticule-picking map.
 * This, in turn, basically just shows what graticule is currently selected and
 * whether or not the 30W rule applies.
 * 
 * @author Nicholas Killewald
 *
 */
public class GraticuleMapInfoBox extends LinearLayout {
    private Graticule mLastGrat = null;
    private Location mLastLoc = null;
    
    /**
     * @param context
     */
    public GraticuleMapInfoBox(Context context) {
        super(context);

        // INFLATE!
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.infobox_gratmap, this, true);
    }

    /**
     * @param context
     * @param attrs
     */
    public GraticuleMapInfoBox(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        // INFLATE!
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.infobox_gratmap, this, true);
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see android.view.View#setVisibility(int)
     */
    @Override
    public void setVisibility(int visibility) {
        // TODO Auto-generated method stub
        super.setVisibility(visibility);

        // If we're being set visible again, immediately update the box.
        // This probably isn't strictly necessary, but it's defensive.
        if (visibility == VISIBLE) {
            update(mLastGrat, mLastLoc);
        }
    }
    

    /**
     * Updates the box with the given Graticule and, if need be, Location. The
     * Location part is for future use. For now, just rest assured that the
     * Location is needed.
     * 
     * @param grat
     *            Currently selected Graticule
     * @param loc
     *            Location where the user currently is. If null, this assumes
     *            the user's location is unknown.
     */
    public void update(Graticule grat, Location loc) {
        mLastGrat = grat;
        mLastLoc = loc;

        // If this isn't visible right now, skip this step.
        if (getVisibility() != VISIBLE)
            return;

        if (grat == null) {
            // If there is no graticule, just fall back to the default text.
            ((TextView)findViewById(R.id.TapSomethingText)).setVisibility(VISIBLE);
            ((TextView)findViewById(R.id.GraticuleText)).setVisibility(GONE);
            ((TextView)findViewById(R.id.ThirtyWestText)).setVisibility(GONE);
        } else {
            // Otherwise, display the graticule.
            String gratText = getContext().getString(R.string.graticule_label)
                    + " "
                    + grat.getLatitude()
                    + "\u00b0"
                    + (grat.isSouth() ? 'S' : 'N')
                    + " "
                    + grat.getLongitude()
                    + "\u00b0"
                    + (grat.isWest() ? 'W' : 'E');
            
            ((TextView)findViewById(R.id.TapSomethingText)).setVisibility(GONE);
            TextView tv = ((TextView)findViewById(R.id.GraticuleText));
            tv.setVisibility(VISIBLE);
            tv.setText(gratText);
            
            // Make the 30W rule not be not-visible if need be.
            ((TextView)findViewById(R.id.ThirtyWestText)).setVisibility(grat.uses30WRule() ? VISIBLE : GONE);
        }
    }
}
