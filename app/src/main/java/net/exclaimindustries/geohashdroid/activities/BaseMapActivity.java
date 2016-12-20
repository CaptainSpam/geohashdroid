/*
 * BaseMapActivity.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.Manifest;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.backup.BackupManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.GoogleMap;
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
        implements GoogleApiClient.ConnectionCallbacks,
                   GoogleApiClient.OnConnectionFailedListener,
                   MapTypeDialogFragment.MapTypeCallback {
    private static final String DEBUG_TAG = "BaseMapActivity";

    protected GoogleApiClient mGoogleClient;

    // Bool to track whether the app is already resolving an error.
    protected boolean mResolvingError = false;
    // Bool to track whether or not the user's refused permissions.
    protected boolean mPermissionsDenied = false;

    protected GoogleMap mMap;

    /**
     * This is a fragment used to display an error dialog, used by both map
     * activities when they do their crazy permissions requesting stuff.
     */
    public static class ErrorDialogFragment
            extends DialogFragment {

        /** Request code to use when launching the resolution activity. */
        public static final int REQUEST_RESOLVE_ERROR = 1001;

        /** A unique tag for the error dialog fragment. */
        public static final String DIALOG_API_ERROR = "ApiErrorDialog";

        public ErrorDialogFragment() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_API_ERROR);
            return GoogleApiAvailability.getInstance().getErrorDialog(
                    this.getActivity(), errorCode, REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            // Gee, I really hope this only gets called by BaseMapActivity!
            ((BaseMapActivity) getActivity()).onDialogDismissed();
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        // Oh, so THAT'S how the connection can fail: If we're using Marshmallow
        // and the user refused to give permissions to the API or the user
        // doesn't have the Google Play Services installed.  Okay, that's fair.
        // Let's deal with it, then.
        if(!mResolvingError) {
            if(result.hasResolution()) {
                try {
                    mResolvingError = true;
                    result.startResolutionForResult(this, ErrorDialogFragment.REQUEST_RESOLVE_ERROR);
                } catch(IntentSender.SendIntentException e) {
                    // We get this if something went wrong sending the intent.  So,
                    // let's just try to connect again.
                    mGoogleClient.connect();
                }
            } else {
                // If we can't actually resolve this, give up and throw an error.
                // doReadyChecks() won't ever be called.
                showErrorDialog(result.getErrorCode());
                mResolvingError = true;
            }
        }
    }

    private void showErrorDialog(int errorCode) {
        BaseMapActivity.ErrorDialogFragment dialogFragment = new BaseMapActivity.ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(BaseMapActivity.ErrorDialogFragment.DIALOG_API_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getFragmentManager(), "errordialog");
    }

    public void onDialogDismissed() {
        mResolvingError = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BaseMapActivity.ErrorDialogFragment.REQUEST_RESOLVE_ERROR) {
            mResolvingError = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mGoogleClient.isConnecting() &&
                        !mGoogleClient.isConnected()) {
                    mGoogleClient.connect();
                }
            }
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
        // 1 is night, -1 is day.
        short becomesNight = 0;

        // Map type!
        if(mMap != null) {
            switch(type) {
                case GoogleMap.MAP_TYPE_NORMAL:
                    mMap.setMapStyle(null);
                    becomesNight = -1;
                    // Let's abuse a fallthrough!
                case GoogleMap.MAP_TYPE_HYBRID:
                case GoogleMap.MAP_TYPE_TERRAIN:
                    mMap.setMapType(type);
                    break;
                case MapTypeDialogFragment.MAP_STYLE_NIGHT:
                    // Whoops, this one isn't a type.  It's a style.  First, the
                    // type has to be normal for this to work.
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

                    // Then, load up the night style.
                    if(!mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_night)))
                        Log.e(DEBUG_TAG, "Couldn't parse the map style JSON!");

                    becomesNight = 1;
                    break;
            }
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = prefs.edit();
        edit.putInt(GHDConstants.PREF_LAST_MAP_TYPE, type);
        edit.apply();

        BackupManager bm = new BackupManager(this);
        bm.dataChanged();

        // Set the night only if it's changed at all.
        if(becomesNight == 1) setNightMode(true);
        else if(becomesNight == -1) setNightMode(false);
    }
}
