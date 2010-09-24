/**
 * LocationAwareActivity.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.tools;

import android.app.Activity;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;

/**
 * A <code>LocationAwareActivity</code> is one that, at resume time, will start
 * listening for location updates and will stop doing so at pause time.
 * Everything else, like wakelocking and such, is up to you.
 * 
 * @author Nicholas Killewald
 *
 */
public abstract class LocationAwareActivity extends Activity implements LocationListener {

    private Location mLastLocation;
    
    /**
     * Returns the last Location this Activity has seen.  Note that this can be
     * null if nothing has been seen yet, and no attempt is made to get
     * LocationManager's last known Location.
     * 
     * @return the last Location this Activity has seen
     */
    protected Location getLastLocation() {
        return mLastLocation;
    }
    
    /**
     * Determines whether or not the last Location this Activity has seen was
     * obtained within the time frame specified, in milliseconds.
     * 
     * @return true if new enough, false if not
     */
    protected boolean isLastLocationNewEnough(long age) {
        if(mLastLocation == null) return false;
        
        return System.currentTimeMillis() - mLastLocation.getTime() < age;
    }
    
    /* (non-Javadoc)
     * @see android.location.LocationListener#onLocationChanged(android.location.Location)
     */
    @Override
    public void onLocationChanged(Location arg0) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see android.location.LocationListener#onProviderDisabled(java.lang.String)
     */
    @Override
    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see android.location.LocationListener#onProviderEnabled(java.lang.String)
     */
    @Override
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see android.location.LocationListener#onStatusChanged(java.lang.String, int, android.os.Bundle)
     */
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub

    }

}
