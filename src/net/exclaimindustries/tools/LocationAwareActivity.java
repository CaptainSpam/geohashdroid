/**
 * LocationAwareActivity.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.tools;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;

/**
 * A <code>LocationAwareActivity</code> is one that, at resume time, will start
 * listening for location updates and will stop doing so at pause time.
 * Everything else, like wakelocking and such, is up to you.
 * 
 * The manner by which this will listen for locations is to pick up both network
 * AND GPS signals, preferring GPS (that is to say, it will behave the same way
 * Geohash Droid does).
 * 
 * @author Nicholas Killewald
 *
 */
public abstract class LocationAwareActivity extends Activity implements LocationListener {

    private Location mLastLocation;
    private boolean mIsGPSActive = false;
    private LocationManager mManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Summon a LocationManager from the mists of Android!
        mManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // Stop getting location updates.
        mManager.removeUpdates(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Populate the location with the last known data, GPS taking
        // precedence.  We don't care how old it is; the implementation will
        // take care of that.
        Location lastKnownGPS = mManager
                .getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location lastKnownTower = mManager
                .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        if (lastKnownGPS != null) {
            mLastLocation = lastKnownGPS;
        } else if (lastKnownTower != null) {
            mLastLocation = lastKnownTower;
        } else {
            // If all else fails, throw a null.
            mLastLocation = null;
        }
        
        // See what's open.
        List<String> providers = mManager.getProviders(true);

        // Now, register all providers and get us going!
        for (String s : providers) {
            mManager.requestLocationUpdates(s, 0, 0, this);
        }
    }

    /**
     * Returns the last Location this Activity has seen.  Note that this can be
     * null if nothing has been seen yet.  Also, this will be pre-populated with
     * LocationManager's last-seen fix, GPS taking precedence.
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
     * @param age maximum age, in milliseconds
     * @return true if new enough, false if not
     */
    protected boolean isLastLocationNewEnough(long age) {
        if(mLastLocation == null) return false;
        
        return System.currentTimeMillis() - mLastLocation.getTime() < age;
    }
    
    /**
     * Gets the active LocationManager.
     * 
     * @return the active LocationManager.
     */
    protected LocationManager getLocationManager() {
        return mManager;
    }
    
    /* (non-Javadoc)
     * @see android.location.LocationListener#onLocationChanged(android.location.Location)
     */
    @Override
    public void onLocationChanged(Location loc) {
        if (loc.getProvider() != null
                && loc.getProvider().equals(LocationManager.GPS_PROVIDER)) {
            // If this was a GPS fix, flip on our handy boolean and update!
            mLastLocation = loc;
            mIsGPSActive = true;
            locationUpdated();
        } else if (!mIsGPSActive) {
            // If this wasn't a GPS fix, but last we knew, GPS wasn't active
            // (or doesn't have a fix yet), update anyway.
            mLastLocation = loc;
            locationUpdated();
        }
        // We don't update mLastLocation otherwise.  If neither of those were
        // true, that was an update from the cell towers when GPS was active, so
        // we don't want to remember THAT.
    }

    /* (non-Javadoc)
     * @see android.location.LocationListener#onProviderDisabled(java.lang.String)
     */
    @Override
    public void onProviderDisabled(String provider) {
        // If GPS was disabled, go flip the boolean.
        if (provider.equals(LocationManager.GPS_PROVIDER))
            mIsGPSActive = false;
    }

    /* (non-Javadoc)
     * @see android.location.LocationListener#onProviderEnabled(java.lang.String)
     */
    @Override
    public void onProviderEnabled(String provider) {
        // This is blank; even if GPS comes back on from being off, we still
        // want to wait for the first fix before we accept that it's on.
    }

    /* (non-Javadoc)
     * @see android.location.LocationListener#onStatusChanged(java.lang.String, int, android.os.Bundle)
     */
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // If GPS goes down, flip our good friend, the boolean.
        if (provider.equals(LocationManager.GPS_PROVIDER)
                && status != LocationProvider.AVAILABLE)
            mIsGPSActive = false;
    }

    /**
     * Called whenever the location ACTUALLY updates.  That is, this won't be
     * called when a network update comes in but we think GPS is active.
     */
    abstract protected void locationUpdated();
}
