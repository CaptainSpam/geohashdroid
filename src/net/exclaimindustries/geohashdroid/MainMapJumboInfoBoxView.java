/**
 * MainMapJumboInfoBoxView.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.text.DecimalFormat;

import android.content.Context;
import android.location.Location;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * <p>
 * This is MainMapInfoBoxView's big brother. This one is all jumbo-sized with a
 * big ol' coordinate readout, ideal for taking pictures. This and junior are,
 * in general, both placed in the map at the same time and are just made visible
 * or invisible depending on preferences.
 * </p>
 * 
 * <p>
 * This displays the coordinates to a higher degree of accuracy than junior, and
 * does NOT display distance to save some screen real estate. Also, as jumbo is
 * intended to fully stretch across the top of the screen, the compass should be
 * disabled when this comes into play.
 * </p>
 * 
 * @author Nicholas Killewald
 * 
 */
public class MainMapJumboInfoBoxView extends MainMapInfoBoxView {

    public MainMapJumboInfoBoxView(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
        setFormats();
    }

    public MainMapJumboInfoBoxView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFormats();
    }

    public MainMapJumboInfoBoxView(Context context) {
        super(context);
        setFormats();
    }

    private void setFormats() {
        mLatLonFormat = new DecimalFormat("###.000000");
        mDistFormat = new DecimalFormat("###.######");
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
        
        // Because the minutes and seconds readouts are MUCH longer than that of
        // degrees, we need to use short form for them, AND reduce the font
        // size just a bit.
        int format = UnitConverter.OUTPUT_SHORT;
        String pref = UnitConverter.getCoordUnitPreference(c);
        
        if(pref.equals("Degrees"))
            format = UnitConverter.OUTPUT_LONG;
        else
            format = UnitConverter.OUTPUT_SHORT;
        
        if(pref.equals("Seconds"))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        else
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
                
        // Get the final destination. We'll translate it to N/S and E/W
        // instead of positive/negative. We'll also narrow it down to three
        // decimal points.

        // The final destination coordinates
        String finalLine = c.getString(R.string.infobox_final) + " "
                    + UnitConverter.makeLatitudeCoordinateString(c, info.getLatitude(), false, format) + " "
                    + UnitConverter.makeLongitudeCoordinateString(c, info.getLongitude(), false, format);

        // Your current location coordinates
        String youLine;
        if (loc != null) {
            youLine = c.getString(R.string.infobox_you) + " "
                    + UnitConverter.makeLatitudeCoordinateString(c, loc.getLatitude(), false, format) + " "
                    + UnitConverter.makeLongitudeCoordinateString(c, loc.getLongitude(), false, format);
        } else {
            youLine = c.getString(R.string.infobox_you) + " "
                    + c.getString(R.string.standby_title);
        }

        // Whether or not this is at all accurate.
        String accuracyLine;
        if (loc == null) {
            accuracyLine = "";
        } else {
            float accuracy = loc.getAccuracy();
            if (accuracy > GHDConstants.REALLY_LOW_ACCURACY_THRESHOLD) {
                accuracyLine = "\n"
                        + c.getString(R.string.infobox_accuracy_really_low);
            } else if (accuracy > GHDConstants.LOW_ACCURACY_THRESHOLD) {
                accuracyLine = "\n"
                        + c.getString(R.string.infobox_accuracy_low);
            } else {
                accuracyLine = "";
            }
        }

        setText(finalLine + "\n" + youLine + accuracyLine);
    }
}
