/*
 * InfoBox.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.widgets;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.UnitConverter;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.tools.LocationUtil;

import java.text.DecimalFormat;

/**
 * This is the info box.  It sits neatly on top of the map screen.  Given an
 * Info and a stream of updates, it'll report on where the user is and how far
 * from the target they are.
 */
public class InfoBox extends LinearLayout {

    private Info mInfo;

    private TextView mDest;
    private TextView mYou;
    private TextView mDistance;
    private TextView mAccuracyLow;
    private TextView mAccuracyReallyLow;

    private GoogleApiClient mGClient;
    private Location mLastLocation;

    private static final DecimalFormat mDistFormat = new DecimalFormat("###.###");

    private boolean mIsListening = false;
    private boolean mAlreadyLaidOut = false;

    private LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // Hey, look, a location!
            mLastLocation = location;
            updateBox();
        }
    };

    public InfoBox(Context c) {
        this(c, null);
    }

    public InfoBox(Context c, AttributeSet attrs) {
        super(c, attrs);

        // INFLATE!
        inflate(c, R.layout.infobox, this);

        mDest = (TextView)findViewById(R.id.infobox_hashpoint);
        mYou = (TextView)findViewById(R.id.infobox_you);
        mDistance = (TextView)findViewById(R.id.infobox_distance);
        mAccuracyLow = (TextView)findViewById(R.id.infobox_accuracy_low);
        mAccuracyReallyLow = (TextView)findViewById(R.id.infobox_accuracy_really_low);

        // As usual, make sure the view's just gone until we need it.
        // ExpeditionMode will pull it back in.
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Got a height!  Hopefully.
                if(!mAlreadyLaidOut) {
                    mAlreadyLaidOut = true;
                    setInfoBoxVisible(false);
                }
            }
        });
    }

    /**
     * Sets the Info.  If null, this will make it go to standby.
     *
     * @param info the new Info
     */
    public void setInfo(@Nullable final Info info) {
        // New info!
        mInfo = info;

        updateBox();
    }

    private void updateBox() {
        ((Activity)getContext()).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                float accuracy = 0.0f;
                if(mLastLocation != null) accuracy = mLastLocation.getAccuracy();

                // Redraw the Info.  Always do this.  The user might be coming
                // back from Preferences, for instance.
                if(mInfo == null) {
                    mDest.setText(R.string.standby_title);
                } else {
                    mDest.setText(UnitConverter.makeFullCoordinateString(getContext(), mInfo.getFinalLocation(), false, UnitConverter.OUTPUT_SHORT));
                }

                // Reset the accuracy warnings.  The right one will go back up
                // as need be.
                mAccuracyLow.setVisibility(View.GONE);
                mAccuracyReallyLow.setVisibility(View.GONE);

                // If we've got a location yet, use that.  If not, to standby
                // with you!
                if(mLastLocation == null) {
                    mYou.setText(R.string.standby_title);
                } else {
                    mYou.setText(UnitConverter.makeFullCoordinateString(getContext(), mLastLocation, false, UnitConverter.OUTPUT_SHORT));

                    // Hey, as long as we're here, let's also do accuracy.
                    if(accuracy >= GHDConstants.REALLY_LOW_ACCURACY_THRESHOLD)
                        mAccuracyReallyLow.setVisibility(View.VISIBLE);
                    else if(accuracy >= GHDConstants.LOW_ACCURACY_THRESHOLD)
                        mAccuracyLow.setVisibility(View.VISIBLE);
                }

                // Next, calculate the distance, if possible.
                if(mLastLocation == null || mInfo == null) {
                    mDistance.setText(R.string.standby_title);
                    mDistance.setTextColor(getResources().getColor(R.color.infobox_text));
                } else {
                    float distance = mLastLocation.distanceTo(mInfo.getFinalLocation());
                    mDistance.setText(UnitConverter.makeDistanceString(getContext(), mDistFormat, distance));

                    // Plus, if we're close enough AND accurate enough, make the
                    // text be green.  We COULD do this with geofencing
                    // callbacks and all, but, I mean, we're already HERE,
                    // aren't we?
                    if(accuracy < GHDConstants.LOW_ACCURACY_THRESHOLD && distance <= accuracy)
                        mDistance.setTextColor(getResources().getColor(R.color.infobox_in_range));
                    else
                        mDistance.setTextColor(getResources().getColor(R.color.infobox_text));

                }
            }
        });
    }

    /**
     * Tells the InfoBox to start listening for updates.  Does nothing if it
     * thinks it already is.
     *
     * @param gClient the GoogleApiClient to use to listen (will be stored for later unlistening)
     */
    public void startListening(GoogleApiClient gClient) {
        if(mIsListening) return;

        mGClient = gClient;

        // Time to wake up and start processing locations!  We'll get the
        // current location first just for speed, AND we'll subscribe for
        // updates.
        Location loc = LocationServices.FusedLocationApi.getLastLocation(gClient);

        if(LocationUtil.isLocationNewEnough(loc))
            mLastLocation = loc;
        else
            mLastLocation = null;

        updateBox();

        LocationRequest lRequest = LocationRequest.create();
        lRequest.setInterval(1000);
        lRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGClient, lRequest, mLocationListener);

        mIsListening = true;
    }

    /**
     * Tells the InfoBox to stop listening to location updates.  Does nothing if
     * it doesn't think it is already.  This will use whatever GoogleApiClient
     * was passed in to {@link #startListening(GoogleApiClient)} earlier.
     */
    public void stopListening() {
        if(!mIsListening) return;

        LocationServices.FusedLocationApi.removeLocationUpdates(mGClient, mLocationListener);

        mIsListening = false;
    }

    /**
     * Slides the InfoBox in to or out of view.
     *
     * @param visible true to slide in, false to slide out
     */
    public void animateInfoBoxVisible(boolean visible) {
        // Quick note: The size of the InfoBox might change due to the width of
        // the text shown (as well as the accuracy warning), but since we alpha
        // it out anyway, that shouldn't be a real major issue.
        if(!visible) {
            // Slide out!
            animate().translationX(getWidth()).alpha(0.0f);
            stopListening();
        } else {
            // Slide in!
            animate().translationX(0.0f).alpha(1.0f);
        }
    }

    /**
     * Makes the InfoBox be in or out of view without animating it.
     * @param visible true to appear, false to vanish
     */
    public void setInfoBoxVisible(boolean visible) {
        if(!visible) {
            setTranslationX(getWidth());
            setAlpha(0.0f);
            stopListening();
        } else {
            setTranslationX(0.0f);
            setAlpha(1.0f);
        }
    }
}
