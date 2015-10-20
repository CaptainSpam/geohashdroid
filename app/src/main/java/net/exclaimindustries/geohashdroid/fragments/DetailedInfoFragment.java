/*
 * DetailedInfoFragment.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.UnitConverter;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.tools.LocationUtil;

import java.text.DateFormat;

/**
 * The DetailedInfoFragment shows us some detailed info.  It's Javadocs like
 * this that really sell the whole concept, I know.
 */
public class DetailedInfoFragment extends CentralMapExtraFragment
        implements GoogleApiClient.ConnectionCallbacks,
                   GoogleApiClient.OnConnectionFailedListener {
    /** The bundle key for the Info. */
    public final static String INFO = "info";

    private final static int LOCATION_PERMISSION = 1;

    private TextView mDate;
    private TextView mYouLat;
    private TextView mYouLon;
    private TextView mDestLat;
    private TextView mDestLon;
    private TextView mDistance;
    private TextView mAccuracy;

    private GoogleApiClient mGClient;
    private Location mLastLocation;

    private LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // Ding!
            mLastLocation = location;
            updateDisplay();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We'll also form the Google API Client here.  I'm actually not sure
        // why I thought it was a good idea to pass it in from outside when it's
        // far more reliable to do it here and manage everything that way.
        mGClient = new GoogleApiClient.Builder(getActivity())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.detail, container, false);

        // TextViews!
        mDate = (TextView)layout.findViewById(R.id.detail_date);
        mYouLat = (TextView)layout.findViewById(R.id.you_lat);
        mYouLon = (TextView)layout.findViewById(R.id.you_lon);
        mDestLat = (TextView)layout.findViewById(R.id.dest_lat);
        mDestLon = (TextView)layout.findViewById(R.id.dest_lon);
        mDistance = (TextView)layout.findViewById(R.id.distance);
        mAccuracy = (TextView)layout.findViewById(R.id.accuracy);

        // Button!
        Button closeButton = (Button) layout.findViewById(R.id.close);

        // Button does a thing!
        if(closeButton != null) registerCloseButton(closeButton);

        return layout;
    }

    @Override
    public void onStart() {
        super.onStart();

        // Connect up!
        mGClient.connect();
    }

    @Override
    public void onStop() {
        // Stop!
        if(mGClient != null) {
            if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                LocationServices.FusedLocationApi.removeLocationUpdates(mGClient, mLocationListener);
            mGClient.disconnect();
        }

        super.onStop();
    }

    @Override
    public void onConnected(Bundle bundle) {
        startListening();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(permissions.length <= 0 || grantResults.length <= 0)
            return;

        if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // We're good!  Fire it up again!
            startListening();
        } else {
            // We're not good.  Throw a popup.
            Bundle args = new Bundle();
            args.putInt(PermissionDeniedDialogFragment.TITLE, R.string.title_permission_location);
            args.putInt(PermissionDeniedDialogFragment.MESSAGE, R.string.explain_permission_location);

            PermissionDeniedDialogFragment frag = new PermissionDeniedDialogFragment();
            frag.setArguments(args);
            frag.show(getFragmentManager(), "PermissionDeniedDialog");
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        // HALP
        mLastLocation = null;
        updateDisplay();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // The only way into DetailedInfoFragment is via CentralMap (either by
        // an added fragment or jumping to the new activity).  We already
        // covered API failures back there, so either we still have those
        // permissions set or the user refused and we don't want to keep on
        // bugging them.  Either way, we can ignore this.
    }

    private void startListening() {
        // Unlike in CentralMap, we won't set up an entire method to abstract
        // this away, since the only use we have for this permission is right
        // here.
        if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Permission granted!  Let's get registered for updates!
            Location loc = LocationServices.FusedLocationApi.getLastLocation(mGClient);

            if(LocationUtil.isLocationNewEnough(loc))
                mLastLocation = loc;
            else
                mLastLocation = null;

            updateDisplay();

            LocationRequest lRequest = LocationRequest.create();
            lRequest.setInterval(1000);
            lRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGClient, lRequest, mLocationListener);
        } else {
            // Else, we need to fire off a permissions check.
            FragmentCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION);
        }
    }

    /**
     * Sets the Info.  If null, this will make it go to standby.  Whatever gets
     * set here will override any arguments originally passed in if and when
     * onSaveInstanceState is needed.
     *
     * @param info the new Info
     */
    public void setInfo(@Nullable final Info info) {
        super.setInfo(info);

        updateDisplay();
    }

    private void updateDisplay() {
        // Good!  This is almost the same as the InfoBox.  It just has more
        // detail and such.
        Activity activity = getActivity();

        if(activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    float accuracy = 0.0f;
                    if(mLastLocation != null) accuracy = mLastLocation.getAccuracy();

                    // One by one, just like InfoBox!  I mean, not JUST like it.
                    // We split the coordinate parts into different TextViews
                    // here, and we have the date to display, but other than
                    // THAT...
                    if(mInfo == null) {
                        mDestLat.setText(R.string.standby_title);
                        mDestLon.setText("");
                        mDate.setText("");
                    } else {
                        mDestLat.setText(UnitConverter.makeLatitudeCoordinateString(getActivity(), mInfo.getFinalLocation().getLatitude(), false, UnitConverter.OUTPUT_DETAILED));
                        mDestLon.setText(UnitConverter.makeLongitudeCoordinateString(getActivity(), mInfo.getFinalLocation().getLongitude(), false, UnitConverter.OUTPUT_DETAILED));
                        mDate.setText(DateFormat.getDateInstance(DateFormat.LONG).format(
                                mInfo.getCalendar().getTime()));
                    }

                    // Location and accuracy!
                    if(mLastLocation == null) {
                        mYouLat.setText(R.string.standby_title);
                        mYouLon.setText("");
                        mAccuracy.setText("");
                    } else {
                        mYouLat.setText(UnitConverter.makeLatitudeCoordinateString(getActivity(), mLastLocation.getLatitude(), false, UnitConverter.OUTPUT_DETAILED));
                        mYouLon.setText(UnitConverter.makeLongitudeCoordinateString(getActivity(), mLastLocation.getLongitude(), false, UnitConverter.OUTPUT_DETAILED));

                        mAccuracy.setText(getString(R.string.details_accuracy,
                                UnitConverter.makeDistanceString(getActivity(),
                                        GHDConstants.ACCURACY_FORMAT, mLastLocation.getAccuracy())));
                    }

                    // Distance!
                    if(mLastLocation == null || mInfo == null) {
                        mDistance.setText(R.string.standby_title);
                        mDistance.setTextColor(getResources().getColor(R.color.details_text));
                    } else {
                        float distance = mLastLocation.distanceTo(mInfo.getFinalLocation());
                        mDistance.setText(UnitConverter.makeDistanceString(getActivity(), GHDConstants.DIST_FORMAT, distance));

                        // Plus, if we're close enough AND accurate enough, make the
                        // text be green.  We COULD do this with geofencing
                        // callbacks and all, but, I mean, we're already HERE,
                        // aren't we?
                        if(accuracy < GHDConstants.LOW_ACCURACY_THRESHOLD && distance <= accuracy)
                            mDistance.setTextColor(getResources().getColor(R.color.details_in_range));
                        else
                            mDistance.setTextColor(getResources().getColor(R.color.details_text));

                    }
                }
            });
        }
    }

    @NonNull
    @Override
    public FragmentType getType() {
        return FragmentType.DETAILS;
    }
}
