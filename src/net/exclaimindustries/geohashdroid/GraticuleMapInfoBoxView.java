/**
 * GraticuleInfoBoxView.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.content.Context;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/**
 * The GraticuleMapInfoBoxView displays the InfoBox on the graticule-picking
 * map. This, in turn, basically just shows what graticule is currently
 * selected.
 * 
 * @author Nicholas Killewald
 * 
 */
public class GraticuleMapInfoBoxView extends TextView {
    private Graticule mLastGrat = null;
    private Location mLastLoc = null;

    public GraticuleMapInfoBoxView(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        // TODO Auto-generated constructor stub
    }

    public GraticuleMapInfoBoxView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub
    }

    public GraticuleMapInfoBoxView(Context context) {
        super(context);
        // TODO Auto-generated constructor stub
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
        if (visibility == View.VISIBLE) {
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
        if (getVisibility() != View.VISIBLE)
            return;

        if (grat == null) {
            // If there is no graticule, just fall back to the default text.
            setText(R.string.gratmap_activity_title);
        } else {
            // Otherwise, display the graticule. Add the 30W rule note if need
            // be.
            setText(getContext().getString(R.string.graticule_label)
                    + " "
                    + grat.getLatitude()
                    + "\u00b0"
                    + (grat.isSouth() ? 'S' : 'N')
                    + " "
                    + grat.getLongitude()
                    + "\u00b0"
                    + (grat.isWest() ? 'W' : 'E')
                    + (grat.uses30WRule() ? "\n"
                            + getContext().getString(R.string.infobox_30w) : ""));
        }
    }
}
