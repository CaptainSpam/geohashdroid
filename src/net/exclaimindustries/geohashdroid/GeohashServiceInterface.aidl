package net.exclaimindustries.geohashdroid;

import net.exclaimindustries.geohashdroid.Info;
import android.location.Location;

interface GeohashServiceInterface {
    /**
     * Starts tracking for a given Info bundle.  If we were already tracking,
     * stop that one (and send out a notification) and start this one.
     */
    void startTracking(in Info info);
    
    /**
     * Stops tracking whatever was going on.  If this indeed stopped something,
     * notification will be sent.
     */
    void stopTracking();
    
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
}