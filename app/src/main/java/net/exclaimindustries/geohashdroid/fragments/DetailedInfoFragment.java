/*
 * DetailedInfoFragment.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.UnitConverter;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.tools.LocationUtil;

/**
 * The DetailedInfoFragment shows us some detailed info.  It's Javadocs like
 * this that really sell the whole concept, I know.
 */
public class DetailedInfoFragment extends Fragment {
    /** The bundle key for the Info. */
    public final static String INFO = "info";

    private TextView mYouLat;
    private TextView mYouLon;
    private TextView mDestLat;
    private TextView mDestLon;
    private TextView mDistance;
    private TextView mAccuracy;

    private boolean mIsListening = false;

    private GoogleApiClient mGClient;
    private Location mLastLocation;
    private Info mInfo;

    private CloseListener mCloseListener;

    /**
     * Now, what you've got here is your garden-variety interface that something
     * ought to implement to handle the close button on this here fragment.
     */
    public interface CloseListener {
        /**
         * Called when the user clicks the close button.  Use this opportunity
         * to either dismiss the fragment or close the activity that contains
         * it.
         */
        void detailedInfoClosing();
    }

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

        // We hopefully have an argument.
        Bundle args = getArguments();
        if(args != null) {
            mInfo = args.getParcelable(INFO);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.detail, container, true);

        // TextViews!
        mYouLat = (TextView)layout.findViewById(R.id.you_lat);
        mYouLon = (TextView)layout.findViewById(R.id.you_lon);
        mDestLat = (TextView)layout.findViewById(R.id.dest_lat);
        mDestLon = (TextView)layout.findViewById(R.id.dest_lon);
        mDistance = (TextView)layout.findViewById(R.id.distance);
        mAccuracy = (TextView)layout.findViewById(R.id.accuracy);

        // Button!
        Button closeButton = (Button) layout.findViewById(R.id.close);

        // Button does a thing!
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mCloseListener != null) mCloseListener.detailedInfoClosing();
            }
        });

        return layout;
    }

    /**
     * Tells the fragment to start listening for updates.  Does nothing if it
     * thinks it already is.
     *
     * @param gClient the GoogleApiClient to use to listen (will be stored for later unlistening)
     */
    public void startListening(GoogleApiClient gClient) {
        if(mIsListening) return;

        mGClient = gClient;

        // We should know the drill by now.  If not, see InfoBox.
        Location loc = LocationServices.FusedLocationApi.getLastLocation(gClient);

        if(LocationUtil.isLocationNewEnough(loc))
            mLastLocation = loc;
        else
            mLastLocation = null;

        updateDisplay();

        LocationRequest lRequest = LocationRequest.create();
        lRequest.setInterval(1000);
        lRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGClient, lRequest, mLocationListener);

        mIsListening = true;
    }

    /**
     * Tells the fragment to stop listening to location updates.  Does nothing
     * if it doesn't think it is already.  This will use whatever
     * GoogleApiClient was passed in to {@link #startListening(GoogleApiClient)}
     * earlier, which is why this won't do anything if you haven't started
     * listening first.
     */
    public void stopListening() {
        if(!mIsListening) return;

        LocationServices.FusedLocationApi.removeLocationUpdates(mGClient, mLocationListener);

        mIsListening = false;
    }

    /**
     * Sets the Info.  If null, this will make it go to standby.  Note that in
     * general, you'd set this as an argument to the fragment, so unless you're
     * changing the Info midway through, this shouldn't be necessary.
     *
     * @param info the new Info
     */
    public void setInfo(@Nullable final Info info) {
        // New info!
        mInfo = info;

        updateDisplay();
    }

    /**
     * Sets what'll be listening for the close button.
     *
     * @param closeListener some CloseListener somewhere
     */
    public void setCloseListener(CloseListener closeListener) {
        mCloseListener = closeListener;
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
                    // here.
                    if(mInfo == null) {
                        mDestLat.setText(R.string.standby_title);
                        mDestLon.setText("");
                    } else {
                        mDestLat.setText(UnitConverter.makeLatitudeCoordinateString(getActivity(), mInfo.getFinalLocation().getLatitude(), false, UnitConverter.OUTPUT_DETAILED));
                        mDestLon.setText(UnitConverter.makeLongitudeCoordinateString(getActivity(), mInfo.getFinalLocation().getLongitude(), false, UnitConverter.OUTPUT_DETAILED));
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
                        mDistance.setTextColor(getResources().getColor(R.color.infobox_text));
                    } else {
                        float distance = mLastLocation.distanceTo(mInfo.getFinalLocation());
                        mDistance.setText(UnitConverter.makeDistanceString(getActivity(), GHDConstants.DIST_FORMAT, distance));

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
    }
}
