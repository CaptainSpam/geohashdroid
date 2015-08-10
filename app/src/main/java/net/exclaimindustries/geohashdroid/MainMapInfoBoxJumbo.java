/**
 * MainMapInfoBoxJumbo.java
 * Copyright (C)2012 Nicholas Killewald
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
import android.view.LayoutInflater;
import android.widget.TextView;

/**
 * <p>
 * This is MainMapInfoBoxSmall's big brother. This one is all jumbo-sized
 * with a big ol' coordinate readout, ideal for taking pictures. This and junior
 * are, in general, both placed in the map at the same time and are just made
 * visible or invisible depending on preferences.
 * </p>
 * 
 * <p>
 * This displays the coordinates to a higher degree of accuracy than junior, and
 * does NOT display the final destination to save some screen real estate. Also,
 * as jumbo is intended to fully stretch across the top of the screen, the
 * compass should be disabled when this comes into play.
 * </p>
 * 
 * @author Nicholas Killewald
 * 
 */
public class MainMapInfoBoxJumbo extends MainMapInfoBox {
    /** The decimal format for distances. */
    private DecimalFormat mDistFormat = new DecimalFormat("###.######");
    
    /**
     * @param context
     */
    public MainMapInfoBoxJumbo(Context context) {
        super(context);
        
        // INFLATE!
        setOrientation(VERTICAL);
        
        LayoutInflater.from(context).inflate(R.layout.infobox_jumbo, this, true);
        
        // With everything inflated, all we do is wait for that first update.
        // All default text should already be set up.
    }

    /**
     * @param context
     * @param attrs
     */
    public MainMapInfoBoxJumbo(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        // INFLATE!
        setOrientation(VERTICAL);
        
        LayoutInflater.from(context).inflate(R.layout.infobox_jumbo, this, true);
        
        // With everything inflated, all we do is wait for that first update.
        // All default text should already be set up.
    }

    public void update(Info info, Location loc) {
        // If this isn't visible right now, skip this step.
        lastInfo = info;
        lastLoc = loc;

        Context c = getContext();

        if (getVisibility() != VISIBLE)
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
                
        // Get your current destination. We'll translate it to N/S and E/W
        // instead of positive/negative. We'll also narrow it down to three
        // decimal points.

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
        
        // The distance to the final destination (as the crow flies)
        String distanceLine = c.getString(R.string.infobox_dist)
                + " "
                + (loc != null ? (UnitConverter.makeDistanceString(c,
                        mDistFormat, info.getDistanceInMeters(loc)))
                        : c.getString(R.string.standby_title));

        // Whether or not this is at all accurate.
        String accuracyLine = null;
        if (loc != null) {
            float accuracy = loc.getAccuracy();
            if (accuracy > GHDConstants.REALLY_LOW_ACCURACY_THRESHOLD) {
                accuracyLine = "\n"
                        + c.getString(R.string.infobox_accuracy_really_low);
            } else if (accuracy > GHDConstants.LOW_ACCURACY_THRESHOLD) {
                accuracyLine = "\n"
                        + c.getString(R.string.infobox_accuracy_low);
            }
        }
        
        // Good!  Now that we've got all the data, let's grab all our TextViews
        // and go to town!
        TextView ytv, dtv, atv;
        
        ytv = (TextView)findViewById(R.id.YouText);
        ytv.setText(youLine);
        
        dtv = (TextView)findViewById(R.id.DistanceText);
        dtv.setText(distanceLine);
        dtv.setTextColor(getDistanceColor(c, info, loc));
        
        // Accuracy is hidden if it's not needed.
        atv = (TextView)findViewById(R.id.AccuracyText);
        if(accuracyLine == null) {
            atv.setVisibility(GONE);
        } else {
            atv.setVisibility(VISIBLE);
            atv.setText(accuracyLine);
        }
        
        // In Jumbo, we also need to shrink the text size if we're dealing with
        // seconds.
        if(pref.equals("Seconds")) {
            atv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            dtv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            ytv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        } else {
            atv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            dtv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            ytv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        }
    }
}
