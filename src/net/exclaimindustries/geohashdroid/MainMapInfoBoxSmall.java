/**
 * MainMapInfoBoxSmall.java
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
import android.view.LayoutInflater;
import android.widget.TextView;

/**
 * MainMapInfoBoxSmall contains numerous TextViews which, collectively, display
 * the InfoBox on the map. It keeps all the data up-to-date and makes sure to
 * turn itself off if need be.
 * 
 * @author Nicholas Killewald
 * 
 */
public class MainMapInfoBoxSmall extends MainMapInfoBox {
    /** The decimal format for distances. */
    private DecimalFormat mDistFormat = new DecimalFormat("###.###");
    
    /**
     * @param context
     */
    public MainMapInfoBoxSmall(Context context) {
        super(context);
        
        // INFLATE!
        setOrientation(VERTICAL);
        
        LayoutInflater.from(context).inflate(R.layout.infobox_small, this, true);
        
        // With everything inflated, all we do is wait for that first update.
        // All default text should already be set up.
    }

    /**
     * @param context
     * @param attrs
     */
    public MainMapInfoBoxSmall(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        // INFLATE!
        setOrientation(VERTICAL);
        
        LayoutInflater.from(context).inflate(R.layout.infobox_small, this, true);
        
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
        String accuracyLine = null;
        if (loc != null) {
            float accuracy = loc.getAccuracy();
            if (accuracy >= GHDConstants.REALLY_LOW_ACCURACY_THRESHOLD) {
                accuracyLine = c.getString(R.string.infobox_accuracy_really_low);
            } else if (accuracy >= GHDConstants.LOW_ACCURACY_THRESHOLD) {
                accuracyLine = c.getString(R.string.infobox_accuracy_low);
            }
        }
        
        // Good!  Now that we've got all the data, let's grab all our TextViews
        // and go to town!
        ((TextView)findViewById(R.id.ToText)).setText(finalLine);
        ((TextView)findViewById(R.id.YouText)).setText(youLine);
        ((TextView)findViewById(R.id.DistanceText)).setText(distanceLine);
        
        // Accuracy is hidden if it's not needed.
        if(accuracyLine == null) {
            ((TextView)findViewById(R.id.AccuracyText)).setVisibility(GONE);
        } else {
            TextView tv = ((TextView)findViewById(R.id.AccuracyText)); 
            tv.setVisibility(VISIBLE);
            tv.setText(accuracyLine);
        }
    }
    
}
