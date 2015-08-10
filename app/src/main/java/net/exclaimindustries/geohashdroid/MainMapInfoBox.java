/**
 * MainMapInfoBox.java
 * Copyright (C)2012 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.content.Context;
import android.location.Location;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * This is the abstract class for that info box thingy on the main map screen.
 * All info boxen should derive from here, preferably as LinearLayouts for now.
 * The exact LinearLayout lineage of this class is subject to change if I decide
 * to get really fancy with the infobox.
 * 
 * Which I might do, now that I know how compound components work.
 * 
 * @author Nicholas Killewald
 */
public abstract class MainMapInfoBox extends LinearLayout {
    /** The last known Info bundle. */
    protected Info lastInfo = null;
    /** The last known location. */
    protected Location lastLoc = null;
    
    public MainMapInfoBox(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public MainMapInfoBox(Context context) {
        super(context);
    }
    
    
    /*
     * (non-Javadoc)
     * 
     * @see android.view.View#setVisibility(int)
     */
    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        // If we're being set visible again, immediately update the box.
        // This probably isn't strictly necessary, but it's defensive.
        if (visibility == VISIBLE && lastInfo != null) {
            update(lastInfo, lastLoc);
        }
    }
   
    /**
     * Updates the InfoBox with the given bundle of Info, plus the Location from
     * wherever the user currently is.
     * 
     * @param info
     *            Info bundle that contains, well, info
     * @param loc
     *            Location where the user currently is. If null, this assumes
     *            the user's location is unknown.
     */
    public abstract void update(Info info, Location loc);

    /**
     * Gets the appropriate color for the distance text.  That is, normal if the
     * user is out of range, something else if the user is within the accuracy
     * of the GPS signal AND said accuracy isn't low to begin with.
     * 
     * @param c
     *            a Context
     * @param info
     *            the active info
     * @param loc
     *            where the user is (can be null)
     * @return
     *            the appropriate color (NOT a resource reference)
     */
    protected int getDistanceColor(Context c, Info info, Location loc) {
        if(loc == null) {
            return c.getResources().getColor(R.color.infobox_text);
        } else {
            float accuracy = loc.getAccuracy();
            
            // For testing purposes, if the accuracy comes up as zero, assume
            // that we're either feeding in data via DDMS for testing purposes
            // or someone decided to put alien GPS-like technology with somehow
            // absolute accuracy into an Android device and they really have
            // better things to do than Geohashing.  In either case, use a
            // relatively sane value for the accuracy.
            if(accuracy == 0) accuracy = 5;

            if(loc != null
                    && accuracy < GHDConstants.LOW_ACCURACY_THRESHOLD
                    && info.getDistanceInMeters(loc) <= accuracy) {
                    return c.getResources().getColor(R.color.infobox_in_range);
                } else {
                    return c.getResources().getColor(R.color.infobox_text);
                }
        }
    }
}
