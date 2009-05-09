/**
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.text.DecimalFormat;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * This is a simple utility class which converts a distance output (in meters)
 * into whatever is needed for the job (kilometers, miles, feet).
 * 
 * @author Nicholas Killewald
 */
public class DistanceConverter {
    /** The number of feet per meter. */
    public static final double FEET_PER_METER = 3.2808399;
    /** The number of feet per mile. */
    public static final int FEET_PER_MILE = 5280;

    /**
     * Do the actual conversion. This will attempt to get whatever preference is
     * set for the job and, using the given DecimalFormat, convert it into a
     * string, suitable for displaying.
     * 
     * @param c
     *            the context from which to get the preferences
     * @param df
     *            the format of the string
     * @param distance
     *            the distance, as returned by Location's distanceTo method
     * @return a String of the distance, with units marked
     */
    public static String makeDistanceString(Context c, DecimalFormat df,
            float distance) {
        // First, get the current unit preference.
        SharedPreferences prefs = c.getSharedPreferences(
                GeohashDroid.PREFS_BASE, 0);
        String units = prefs.getString(c.getResources().getString(
                R.string.pref_units_key), "Metric");

        // Second, run the conversion.
        if (units.equals("Metric")) {
            // Meters are easy, if not only for the fact that, by default, the
            // Location object returns distances in meters. And the fact that
            // it's in powers of ten.
            if (distance >= 1000) {
                return df.format(distance / 1000) + "km";
            } else {
                return df.format(distance) + "m";
            }
        } else if (units.equals("Imperial")) {
            // Convert!
            double feet = distance * FEET_PER_METER;

            if (feet >= FEET_PER_MILE) {
                return df.format(feet / FEET_PER_MILE) + "mi";
            } else {
                return df.format(feet) + "ft";
            }
        } else {
            return units + "???";
        }
    }
}
