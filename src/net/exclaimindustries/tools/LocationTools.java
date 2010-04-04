/**
 * LocationTools.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.tools;

import com.google.android.maps.GeoPoint;

import android.location.Location;

/**
 * <code>LocationTools</code> features some handy tools for Location objects.
 * To be more exact, it allows conversions between Locations and GeoPoints.
 * 
 * While this can be a useful class, it's really a stopgap measure as I convert
 * stuff from the MyLocationOverlay-based updates to GeohashService-based.  One
 * uses GeoPoints, the other uses Locations.
 * 
 * @author Nicholas Killewald
 */
public class LocationTools {
    /**
     * Creates a Location from the given GeoPoint.  The Location won't have any
     * data besides latitude and longitude (no altitude, speed, or whatever) and
     * will have an empty string as the provider.  If a null is fed in, a null
     * will be given back.
     * 
     * @param point GeoPoint to convert
     * @return a new Location
     */
    public static Location makeLocationFromGeoPoint(GeoPoint point) {
        if(point == null)
            return null;
        
        Location toReturn = new Location("");
        toReturn.setLatitude(point.getLatitudeE6() / (double)1000000.0);
        toReturn.setLongitude(point.getLongitudeE6() / (double)1000000.0);
        return toReturn;
    }
    
    /**
     * Creates a GeoPoint from the given Location.  The process, of course, will
     * lose any extra data in the Location (altitude, speed, provider, etc),
     * since GeoPoints don't store that.  If the Location is null, a null
     * GeoPoint will come back.
     * 
     * @param loc Location to convert
     * @return a new GeoPoint
     */
    public static GeoPoint makeGeoPointFromLocation(Location loc) {
        if(loc == null)
            return null;
        else
            return new GeoPoint((int)(loc.getLatitude() * 1000000), (int)(loc.getLongitude() * 1000000));
    }
}
