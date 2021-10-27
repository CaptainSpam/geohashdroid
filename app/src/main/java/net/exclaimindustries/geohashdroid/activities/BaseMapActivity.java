/*
 * BaseMapActivity.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.Manifest;
import android.app.backup.BackupManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapsSdkInitializedCallback;
import com.google.android.gms.maps.model.MapStyleOptions;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.fragments.MapTypeDialogFragment;
import net.exclaimindustries.geohashdroid.util.GHDConstants;

/**
 * This is just a base Activity that holds the permission-checking stuff shared
 * between CentralMap and KnownLocationsPicker.  Hooray for not cutting and
 * pasting code!
 */
public abstract class BaseMapActivity
        extends BaseGHDThemeActivity
        implements MapTypeDialogFragment.MapTypeCallback,
                   OnMapsSdkInitializedCallback {
    private static final String DEBUG_TAG = "BaseMapActivity";

    // Bool to track whether the app is already resolving an error.
    protected boolean mResolvingError = false;
    // Bool to track whether or not the user's refused permissions.
    protected boolean mPermissionsDenied = false;

    protected GoogleMap mMap;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Do the Maps init stuff here before anything else has a chance to mess
        // around with stuff.
        // Init the new renderer.
        MapsInitializer.initialize(getApplicationContext(),
                MapsInitializer.Renderer.LATEST,
                this);
    }

    @Override
    public void onMapsSdkInitialized(@NonNull MapsInitializer.Renderer renderer) {
        // I don't think I need to do anything special here, but this could be
        // useful for debuggery purposes.
        switch(renderer) {
            case LATEST:
                Log.i(DEBUG_TAG, "Initialized with the latest map renderer.");
                break;
            case LEGACY:
                Log.i(DEBUG_TAG, "Initialized with the legacy map renderer.");
                break;
        }
    }

    /**
     * <p>
     * Checks for permissions on {@link Manifest.permission#ACCESS_FINE_LOCATION},
     * automatically firing off the permission request if it hasn't been
     * granted yet.  This method DOES return, mind; if it returns true, continue
     * as normal, and if it returns false, don't do anything.  In the false
     * case, it will (usually) ask for permissions, with CentralMap handling the
     * callback.
     * </p>
     *
     * <p>
     * If skipRequest is set, permissions won't be asked for in the event that
     * they're not already granted, and no explanation popup will show up,
     * either.  Use that for cases like shutdowns where all the listeners are
     * being unregistered.
     * </p>
     *
     * @param requestCode the type of check this is, so that whatever it was can be tried again on permissions being granted
     * @param skipRequest if true, don't bother requesting permission, just drop it and go on
     * @return true if permissions are good, false if not (in the false case, a request might be in progress)
     */
    public synchronized boolean checkLocationPermissions(int requestCode, boolean skipRequest) {
        // First, the easy case: Permissions granted.
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Yay!
            return true;
        } else {
            // Boo!  Now we need to fire off a permissions request!  If we were
            // already denied permissions once, though, don't bother trying
            // again.
            if(!skipRequest && !mPermissionsDenied)
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        requestCode);
            return false;
        }
    }

    /**
     * Convenience method that calls {@link #checkLocationPermissions(int, boolean)}
     * with skipRequest set to false.
     *
     * @param requestCode the type of check this is, so that whatever it was can be tried again on permissions being granted
     * @return true if permissions are good, false if not (in the false case, a request might be in progress)
     */
    public synchronized boolean checkLocationPermissions(int requestCode) {
        return checkLocationPermissions(requestCode, false);
    }

    @Override
    public void mapTypeSelected(int type) {
        // Do this first to force any custom styles to be cleared.
        mMap.setMapStyle(null);

        // Map type!
        if(mMap != null) {
            switch(type) {
                case GoogleMap.MAP_TYPE_HYBRID:
                case GoogleMap.MAP_TYPE_TERRAIN:
                    mMap.setMapType(type);
                    break;
                case GoogleMap.MAP_TYPE_NORMAL:
                default:
                    // The default case is to just go to normal.  This allows us
                    // to not have to worry about the old hacked-together night
                    // mode the app used to have.
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

                    // Is it night?  Only you can decide!
                    if(isNightMode()) {
                        // Night technically isn't a type.  It's a style.
                        if(!mMap.setMapStyle(
                                MapStyleOptions.loadRawResourceStyle(
                                        this,
                                        R.raw.map_night)))
                            Log.e(DEBUG_TAG, "Couldn't parse the map style JSON!");
                    }
            }
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putInt(GHDConstants.PREF_LAST_MAP_TYPE, type);
        edit.apply();

        BackupManager bm = new BackupManager(this);
        bm.dataChanged();
    }
}
