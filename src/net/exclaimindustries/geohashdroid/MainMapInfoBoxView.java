/**
 * MainMapInfoBoxView.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.text.DecimalFormat;

import android.content.Context;
import android.location.Location;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/**
 * The MainMapInfoBoxView displays the InfoBox on the map. It keeps all the data
 * up-to-date and makes sure to turn itself off if need be.
 * 
 * @author Nicholas Killewald
 * 
 */
public class MainMapInfoBoxView extends TextView {
    /** The last known Info bundle. */
    protected Info lastInfo = null;
    /** The last known location. */
    protected Location lastLoc = null;

    /** The decimal format for the coordinates. */
    protected DecimalFormat mLatLonFormat = new DecimalFormat("###.000");
    /** The decimal format for distances. */
    protected DecimalFormat mDistFormat = new DecimalFormat("###.###");

    /** Threshold for the "Accuracy Low" warning (currently 64m). **/
    protected static final int LOW_ACCURACY_THRESHOLD = 64;
    /** Threshold for the "Accuracy Really Low" warning (currently 200m). **/
    protected static final int REALLY_LOW_ACCURACY_THRESHOLD = 200;

    public MainMapInfoBoxView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public MainMapInfoBoxView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MainMapInfoBoxView(Context context) {
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
        if (visibility == View.VISIBLE && lastInfo != null) {
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
    public void update(Info info, Location loc) {
        // If this isn't visible right now, skip this step.
        lastInfo = info;
        lastLoc = loc;

        Context c = getContext();

        if (getVisibility() != View.VISIBLE)
            return;

        // Get the final destination. We'll translate it to N/S and E/W
        // instead of positive/negative. We'll also narrow it down to three
        // decimal points.

        // The final destination coordinates
        String finalLine = c.getString(R.string.infobox_final) + " "
                    + UnitConverter.makeLatitudeCoordinateString(c, info.getLatitude(), false, UnitConverter.OUTPUT_SHORT) + " "
                    + UnitConverter.makeLongitudeCoordinateString(c, info.getLongitude(), false, UnitConverter.OUTPUT_SHORT);

        // Your current location coordinates
        String youLine;
        if (loc != null) {
            youLine = c.getString(R.string.infobox_you) + " "
                    + UnitConverter.makeLatitudeCoordinateString(c, loc.getLatitude(), false, UnitConverter.OUTPUT_SHORT) + " "
                    + UnitConverter.makeLongitudeCoordinateString(c, loc.getLongitude(), false, UnitConverter.OUTPUT_SHORT);
        } else {
            youLine = c.getString(R.string.infobox_you) + " "
                    + c.getString(R.string.standby_title);
        }

        // The distance to the final destination (as the crow flies)
        String distanceLine = c.getString(R.string.infobox_dist)
                + " "
                + (loc != null ? (UnitConverter.makeDistanceString(c,
                        mDistFormat, info.getDistanceInMeters(loc))) : c
                        .getString(R.string.standby_title));

        // Whether or not this is at all accurate.
        String accuracyLine;
        if (loc == null) {
            accuracyLine = "";
        } else {
            float accuracy = loc.getAccuracy();
            if (accuracy >= REALLY_LOW_ACCURACY_THRESHOLD) {
                accuracyLine = "\n"
                        + c.getString(R.string.infobox_accuracy_really_low);
            } else if (accuracy >= LOW_ACCURACY_THRESHOLD) {
                accuracyLine = "\n"
                        + c.getString(R.string.infobox_accuracy_low);
            } else {
                accuracyLine = "";
            }
        }

        setText(finalLine + "\n" + youLine + "\n" + distanceLine + accuracyLine);
    }
}
