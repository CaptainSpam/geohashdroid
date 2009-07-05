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
import android.location.Location;

/**
 * This is a simple utility class which converts a distance output (in meters)
 * into whatever is needed for the job (kilometers, miles, feet).
 * 
 * @author Nicholas Killewald
 */
public class UnitConverter {
    /** The number of feet per meter. */
    public static final double FEET_PER_METER = 3.2808399;
    /** The number of feet per mile. */
    public static final int FEET_PER_MILE = 5280;
    
    /** Output should be short, with fewer decimal places. */
    public static final int OUTPUT_SHORT = 0;
    /** Output should be long, with more decimal places. */
    public static final int OUTPUT_LONG = 1;
    /** Output should be even longer, with even more decimal places. */
    public static final int OUTPUT_DETAILED = 2;
    
    protected static final DecimalFormat SHORT_FORMAT = new DecimalFormat("###.000");
    protected static final DecimalFormat LONG_FORMAT = new DecimalFormat("###.00000");
    protected static final DecimalFormat DETAIL_FORMAT = new DecimalFormat("###.00000000");

    /**
     * Perform a distance conversion. This will attempt to get whatever
     * preference is set for the job and, using the given DecimalFormat, convert
     * it into a string, suitable for displaying.
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
                GHDConstants.PREFS_BASE, 0);
        String units = prefs.getString(GHDConstants.PREF_DIST_UNITS, "Metric");

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
    
    /**
     * Perform a coordinate conversion.  This will read in whatever preference
     * is currently in play (degrees, minutes, seconds) and return a string with
     * both latitude and longitude separated by a space.
     * 
     * @param c
     *            Context from whence the preference comes
     * @param l
     *            Location to calculate
     * @param useNegative
     *            true to use positive/negative values, false to use N/S or E/W
     * @param format
     *            specify the output format using one of the OUTPUT_ statics
     * @return
     *             a string form of the coordinates given
     */
    public static String makeFullCoordinateString(Context c, Location l,
            boolean useNegative, int format) {
        return makeLatitudeCoordinateString(c, l.getLatitude(), useNegative, format) + " "
            + makeLongitudeCoordinateString(c, l.getLongitude(), useNegative, format);
    }
    
    /**
     * This is the latitude half of makeFullCoordinateString.
     * 
     * @param c
     *            Context from whence the preference comes
     * @param lat
     *            Latitude to calculate
     * @param useNegative
     *            true to use positive/negative values, false to use N/S
     * @param format
     *            specify the output format using one of the OUTPUT_ statics
     * @return
     *             a string form of the latitude of the coordinates given
     */
    public static String makeLatitudeCoordinateString(Context c, double lat,
            boolean useNegative, int format) {
        String units = getCoordUnitPreference(c);
        
        // Keep track of whether or not this is negative.  We'll attach the
        // prefix or suffix later.
        boolean isNegative = lat < 0;
        // Make this absolute so we know we won't have to juggle negatives until
        // we know what they'll wind up being.
        double rawCoord = Math.abs(lat);
        String coord;
        
        coord = makeCoordinateString(units, rawCoord, format);
        
        // Now, attach negative or suffix, as need be.
        if(useNegative) {
            if(isNegative)
                return "-" + coord;
            else
                return coord;
        } else {
            if(isNegative)
                return coord + "S";
            else
                return coord + "N";
        }
    }
    
    /**
     * This is the longitude half of makeFullCoordinateString.
     * 
     * @param c
     *            Context from whence the preference comes
     * @param lon
     *            Longitude to calculate
     * @param useNegative
     *            true to use positive/negative values, false to use E/W
     * @param format
     *            specify the output format using one of the OUTPUT_ statics
     * @return
     *             a string form of the longitude of the coordinates given
     */
    public static String makeLongitudeCoordinateString(Context c, double lon,
            boolean useNegative, int format) {
        String units = getCoordUnitPreference(c);
        
        // Keep track of whether or not this is negative.  We'll attach the
        // prefix or suffix later.
        boolean isNegative = lon < 0;
        // Make this absolute so we know we won't have to juggle negatives until
        // we know what they'll wind up being.
        double rawCoord = Math.abs(lon);
        String coord;
        
        coord = makeCoordinateString(units, rawCoord, format);
        
        // Now, attach negative or suffix, as need be.
        if(useNegative) {
            if(isNegative)
                return "-" + coord;
            else
                return coord;
        } else {
            if(isNegative)
                return coord + "W";
            else
                return coord + "E";
        }
    }
    
    private static String makeCoordinateString(String units, double coord, int format) {
        // Just does the generic coordinate conversion stuff for coordinates.
        if(units.equals("Degrees")) {
            // Easy case: Use the result Location gives us, modified by the
            // longForm boolean.
            switch(format) {
                case OUTPUT_SHORT:
                    return SHORT_FORMAT.format(coord) + "\u00b0";
                case OUTPUT_LONG:
                    return LONG_FORMAT.format(coord) + "\u00b0";
                default:
                    return DETAIL_FORMAT.format(coord) + "\u00b0";
            }
        } else if(units.equals("Minutes")) {
            // Harder case 1: Minutes.
            String temp = Location.convert(coord, Location.FORMAT_MINUTES);
            String[] split = temp.split(":");
            
            switch(format) {
                case OUTPUT_SHORT:
                    return split[0] + "\u00b0" + split[1].substring(0, 5) + "\u2032";
                case OUTPUT_LONG:
                    return split[0] + "\u00b0" + split[1].substring(0, 7) + "\u2032";
                default:
                    return split[0] + "\u00b0" + split[1]+ "\u2032";
            }
        } else if(units.equals("Seconds")) {
            // Harder case 2: Seconds.
            String temp = Location.convert(coord, Location.FORMAT_SECONDS);
            String[] split = temp.split(":");
            
            switch(format) {
                case OUTPUT_SHORT:
                    return split[0] + "\u00b0" + split[1] + "\u2032" + split[2].substring(0, 5) + "\u2033";
                case OUTPUT_LONG:
                    return split[0] + "\u00b0" + split[1] + "\u2032" + split[2].substring(0, 7) + "\u2033";
                default:
                    return split[0] + "\u00b0" + split[1] + "\u2032" + split[2] + "\u2033";
            }
        } else {
            return "???";
        }
    }
    
    /**
     * Grab the current coordinate unit preference.
     * 
     * @param c Context from whence the preferences arise
     * @return "Degrees", "Minutes", or "Seconds"
     */
    public static String getCoordUnitPreference(Context c) {
        // Units GO!!!
        SharedPreferences prefs = c.getSharedPreferences(
                GHDConstants.PREFS_BASE, 0);
        return prefs.getString(GHDConstants.PREF_COORD_UNITS, "Degrees");
    }
}
