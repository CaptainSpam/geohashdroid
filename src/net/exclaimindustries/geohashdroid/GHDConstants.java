/**
 * GHDConstants.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.text.DecimalFormat;

/**
 * The <code>GHDConstants</code> class doesn't do anything directly.  All it
 * does is serve as a place to store project-wide statics.
 * 
 * @author Nicholas Killewald
 */
public final class GHDConstants {
    /** Preferences base. */
    public static final String PREFS_BASE = "GeohashDroid";
    
    /**
     * Prefs key where the last latitude is stored. 
     * @see PREF_REMEMBER_GRATICULE
     * */
    public static final String PREF_DEFAULT_LAT = "DefaultLatitude";
    /** Prefs key where the last longitude is stored. */
    public static final String PREF_DEFAULT_LON = "DefaultLongitude";
    /** Prefs key where we keep track of whether we were in globalhash mode. */
    public static final String PREF_GLOBALHASH_MODE = "GlobalhashMode";
    /** Prefs key specifying coordinate units. */
    public static final String PREF_COORD_UNITS = "CoordUnits";
    /** Prefs key specifying distance units. */
    public static final String PREF_DIST_UNITS = "Units";
    /** Prefs key specifying whether or not to remember the last graticule. */
    public static final String PREF_REMEMBER_GRATICULE = "RememberGraticule";
    /** Prefs key specifying whether or not to auto-zoom. */
    public static final String PREF_AUTOZOOM = "AutoZoom";
    /** Prefs key specifying info box size. */
    public static final String PREF_INFOBOX_SIZE = "InfoBoxSize";
    /** Prefs key specifying stock cache size. */
    public static final String PREF_STOCK_CACHE_SIZE = "StockCacheSize";
    /** Prefs key specifying to show nearby meetup points. */
    public static final String PREF_NEARBY_POINTS = "NearbyPoints";
    /** Prefs key specifying whether the closest checkbox is ticked. */
    public static final String PREF_CLOSEST = "ClosestOn";
    /** Prefs key specifying wiki user name. */
    public static final String PREF_WIKI_USER = "WikiUserName";
    /** Prefs key specifying wiki user pass. */
    public static final String PREF_WIKI_PASS = "WikiPassword";
    /** Prefs key specifying to use the phone's time, not the wiki's */
    public static final String PREF_WIKI_PHONE_TIME = "WikiUsePhoneTime";
    /** Prefs key specifying if the Today checkbox is ticked. */
    public static final String PREF_TODAY = "AlwaysToday";
    /**
     * Prefs key tracking if we've reported on the closeness of the user to the
     * final destination.
     */
    public static final String PREF_CLOSENESS_REPORTED = "ClosenessReported";
    
    /** Prefs value for no infobox at all. */
    public static final String PREFVAL_INFOBOX_NONE = "None";
    /** Prefs value for a small infobox (with compass). */
    public static final String PREFVAL_INFOBOX_SMALL = "Small";
    /** Prefs value for a jumbo infobox (without compass). */
    public static final String PREFVAL_INFOBOX_JUMBO = "Jumbo";
    
    /** Prefs value for metric distances. */
    public static final String PREFVAL_DIST_METRIC = "Metric";
    /** Prefs value for not-metric distances. */
    public static final String PREFVAL_DIST_IMPERIAL = "Imperial";
    
    /** Prefs value for coordinates in degrees. */
    public static final String PREFVAL_COORD_DEGREES = "Degrees";
    /** Prefs value for coordinates in minutes. */
    public static final String PREFVAL_COORD_MINUTES = "Minutes";
    /** Prefs value for coordinates in minutes and seconds. */
    public static final String PREFVAL_COORD_SECONDS = "Seconds";
    
    /** Threshold for the "Accuracy Low" warning (currently 64m). **/
    public static final int LOW_ACCURACY_THRESHOLD = 64;
    /** Threshold for the "Accuracy Really Low" warning (currently 200m). **/
    public static final int REALLY_LOW_ACCURACY_THRESHOLD = 200;
    
    /**
     * Action for picking a graticule. In Geohash Droid, this means to go to
     * GraticuleMap. Though, so long as it returns a
     * net.exclaimindustries.geohashdroid.Graticule object, I'd assume anything
     * could take its place if someone else writes a better graticule picker.
     */
    public static final String PICK_GRATICULE = "net.exclaimindustries.geohashdroid.PICK_GRATICULE";
    
    
    /** The decimal format for most distances. */
    public static final DecimalFormat DIST_FORMAT = new DecimalFormat("###.######");
    /** The decimal format for most accuracy readouts. */
    public static final DecimalFormat ACCURACY_FORMAT = new DecimalFormat("###.##");
}
