package net.exclaimindustries.geohashdroid;

import android.location.Location;
import net.exclaimindustries.geohashdroid.Info;
import net.exclaimindustries.geohashdroid.GeohashServiceCallback;

interface GeohashServiceInterface {
	void registerCallback(in GeohashServiceCallback callback);
	void unregisterCallback(in GeohashServiceCallback callback);

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
     * where we are right now, or if the last fix is too old (currently two
     * minutes).
     */
    Location getLastLocation();
    
    /**
     * Gets the info we're currently tracking.
     */
    Info getInfo();

    /**
     * Changes to a new Info bundle (i.e. when someone pokes a nearby point in
     * MainMap).  This will cause all clients to get a trackingStarted call with
     * the new data.  This will also start tracking right away if it wasn't
     * tracking to begin with (i.e. the service was started via onBind instead
     * of onStart).
     */
     oneway void changeInfo(in Info info);
     
     /**
      * Stops tracking entirely.  All clients will be informed of this.
      */
     oneway void stopTracking();
}
