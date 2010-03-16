package net.exclaimindustries.geohashdroid;

import android.location.Location;
import net.exclaimindustries.geohashdroid.Info;

interface GeohashServiceInterface {
    /**
     * Determines if there's any tracking going on at all right now.
     */
    boolean isTracking();
    
    /**
     * Determines if we know where we are (that is, if we've had a location
     * update yet and recently).
     */
    boolean hasLocation();
    
    /**
     * Gets the distance, in meters, between the final destination and the
     * last-known location.  This will return Float.MAX_VALUE if we don't know
     * where we are right now.
     */
    float getLastDistanceInMeters();
    
    /**
     * Gets the last-known accuracy, in meters.  This will return
     * Float.MAX_VALUE if we don't know where we are right now.
     */
    float getLastAccuracyInMeters();
    
    /**
     * Gets the last-known location.  This will return null if we don't know
     * where we are right now.
     */
    Location getLastLocation();

    /**
     * Changes to a new Info bundle (i.e. when someone pokes a nearby point in
     * MainMap).  This will cause all clients to get a trackingStarted call with
     * the new data.
     */
     void changeInfo(in Info info);
}
