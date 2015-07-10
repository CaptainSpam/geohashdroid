/*
 * SelectAGraticuleMode.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.activities.CentralMap;
import net.exclaimindustries.geohashdroid.fragments.GraticulePickerFragment;
import net.exclaimindustries.geohashdroid.services.StockService;
import net.exclaimindustries.geohashdroid.widgets.ErrorBanner;
import net.exclaimindustries.tools.LocationUtil;

import java.util.Calendar;

/**
 * <code>SelectAGraticuleMode</code> encompasses the user selecting a Graticule
 * from the map.
 */
public class SelectAGraticuleMode
        extends CentralMap.CentralMapMode
        implements GoogleMap.OnMapClickListener,
                   GraticulePickerFragment.GraticulePickerListener {
    private static final String DEBUG_TAG = "SelectAGraticuleMode";

    private static final double CLOSENESS_X = 2.5;
    private static final double CLOSENESS_Y_UP = 2;
    private static final double CLOSENESS_Y_DOWN = 3;

    private static final String GRATICULE_PICKER_STACK = "GraticulePickerStack";

    private Polygon mPolygon;

    private GraticulePickerFragment mFrag;

    /** The "working" calendar. */
    private Calendar mCalendar;
    /**
     * The last Calendar we saw that had a valid hashpoint.  Well, almost.  If
     * there wasn't a valid hashpoint coming in from ExpeditionMode (i.e. the
     * user started the app fresh before the opening of the DJIA for that day),
     * this still won't be a "good" calendar, per se.  At any rate, this is what
     * gets sent back in the bundle when the mode ends.
     */
    private Calendar mLastGoodCalendar;
    /**
     * The last Graticule we saw that had a valid hashpoint.  This gets set
     * alongside {@link #mLastGoodCalendar}, and is used very similarly.  This
     * CAN be null, of course, which brings us to {@link #mLastGoodGlobal}...
     */
    private Graticule mLastGoodGraticule;
    /**
     * The last status of the globalhash flag we saw that had a valid hashpoint.
     * Same deal as the other two.
     */
    private boolean mLastGoodGlobal;

    private Location mLastLocation;

    private boolean mWasEmptyStart = false;
    private Graticule mInitialGraticule;
    private boolean mInitialGlobal;

    private LocationListener mFindClosestListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // Okay, NOW we have a location.  Don't call the clear method yet,
            // though!  We've still got a lookup to do!
            if(getGoogleClient() != null)
                LocationServices.FusedLocationApi.removeLocationUpdates(getGoogleClient(), this);

            if(!isCleanedUp()) {
                mCentralMap.getErrorBanner().animateBanner(false);
                applyFoundGraticule(location);
            }
        }
    };

    @Override
    public void init(@Nullable Bundle bundle) {
        // Hi, map!
        mMap.setOnMapClickListener(this);

        // Remember if this was an empty start.  We'll want that flag set back
        // up if the user didn't set anything so that ExpeditionMode can try
        // again afterward.
        mWasEmptyStart = (bundle != null && bundle.getBoolean(ExpeditionMode.DO_INITIAL_START, false));

        // Gather some data for later.  The Calendar may be of particular
        // interest in a restart situation.
        if(bundle != null) {
            if(bundle.containsKey(INFO)) {
                Info i = bundle.getParcelable(INFO);
                if(i != null) {
                    mInitialGraticule = i.getGraticule();
                    mCalendar = i.getCalendar();
                    mInitialGlobal = i.isGlobalHash();
                }
            } else {
                if(bundle.containsKey(GRATICULE)) {
                    mInitialGraticule = bundle.getParcelable(GRATICULE);
                }

                if(bundle.containsKey(CALENDAR)) {
                    mCalendar = (Calendar) bundle.getSerializable(CALENDAR);
                }

                if(bundle.containsKey(GLOBALHASH)) {
                    mInitialGlobal = bundle.getBoolean(GLOBALHASH, false);
                }
            }
        }

        if(mCalendar == null)
            mCalendar = Calendar.getInstance();

        mLastGoodCalendar = mCalendar;
        mLastGoodGraticule = mInitialGraticule;
        mLastGoodGlobal = mInitialGlobal;

        // The fragment might already be there if the Activity's being rebuilt.
        // If not, we need to place it there.
        FragmentManager manager = mCentralMap.getFragmentManager();
        mFrag = (GraticulePickerFragment)manager.findFragmentById(R.id.graticulepicker);
        if(mFrag == null) {
            // If the fragment's not there, let's make sure it IS there,
            // complete with bonus data.
            FragmentTransaction transaction = manager.beginTransaction();
            transaction.setCustomAnimations(R.animator.slide_in_from_bottom,
                    R.animator.slide_out_to_bottom,
                    R.animator.slide_in_from_bottom,
                    R.animator.slide_out_to_bottom);

            mFrag = new GraticulePickerFragment();
            mFrag.setListener(this);

            // Toss in the current Graticule so the thing knows where to start.
            Bundle args = new Bundle();
            args.putParcelable(GraticulePickerFragment.GRATICULE, mInitialGraticule);
            args.putBoolean(GraticulePickerFragment.GLOBALHASH, mInitialGlobal);
            mFrag.setArguments(args);

            transaction.replace(R.id.graticulepicker, mFrag, "GraticulePicker");
            transaction.addToBackStack(GRATICULE_PICKER_STACK);
            transaction.commit();
        } else {
            // If the fragment already existed, re-assign the listener.
            mFrag.setListener(this);
            mFrag.triggerListener();
        }

        setTitle(R.string.title_graticule_picker);

        mInitComplete = true;
    }

    @Override
    public void cleanUp() {
        super.cleanUp();

        // Bye, map!
        if(mMap != null) {
            mMap.setOnMapClickListener(null);
            if(mPolygon != null) mPolygon.remove();
        }

        // And bye, picker!
        FragmentManager manager = mCentralMap.getFragmentManager();
        if(manager.findFragmentById(R.id.graticulepicker) != null)
            manager.popBackStack(GRATICULE_PICKER_STACK, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        // Find Closest listener, you too!
        clearFindClosest();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle bundle) {
        Graticule g = mFrag.getGraticule();
        boolean global = mFrag.isGlobalhash();

        // If we didn't get any valid input (that is, the graticule is false AND
        // it's not a Globalhash)...
        if(g == null && !global) {
            // ...then, after checking if we came in from an empty start...
            if(mWasEmptyStart) {
                // ...set the flag back if we were...
                bundle.putBoolean(ExpeditionMode.DO_INITIAL_START, true);
            } else {
                // ...and set the initial entries if not.
                bundle.putParcelable(GRATICULE, mInitialGraticule);
                bundle.putBoolean(GLOBALHASH, mInitialGlobal);
            }
        } else {
            // If we DID get valid input, just use that.
            bundle.putParcelable(GRATICULE, mLastGoodGraticule);
            bundle.putBoolean(GLOBALHASH, mLastGoodGlobal);
        }

        bundle.putSerializable(CALENDAR, mLastGoodCalendar);
    }

    @Override
    public void pause() {
        // Stop that listener!
        GoogleApiClient gClient = getGoogleClient();
        if(gClient != null)
            LocationServices.FusedLocationApi.removeLocationUpdates(gClient, mFindClosestListener);
    }

    @Override
    public void resume() {
        // Nothing needs doing on resume here.  The Find Closest thing can kick
        // back in on the user's command.
    }

    @Override
    public void onCreateOptionsMenu(MenuInflater inflater, Menu menu) {
        inflater.inflate(R.menu.centralmap_selectagraticule, menu);
    }

    @Override
    public void handleInfo(Info info, Info[] nearby, int flags) {
        if(mInitComplete) {
            if((flags & StockService.FLAG_FIND_CLOSEST) == StockService.FLAG_FIND_CLOSEST) {
                mCentralMap.getErrorBanner().animateBanner(false);

                // This is a result from Find Closest.  To the findermatron!
                if(mLastLocation == null || mFrag == null) return;

                Info nearest = Info.measureClosest(mLastLocation, info, nearby);
                clearFindClosest();

                // And that's our target!
                Graticule g = new Graticule(nearest.getFinalLocation());
                mFrag.setNewGraticule(g);
                outlineGraticule(g);
            } else {
                // If we get an Info in, plant a flag where it needs to be.
                addDestinationPoint(info);
                mLastGoodCalendar = info.getCalendar();
                mLastGoodGraticule = info.getGraticule();
                mLastGoodGlobal = info.isGlobalHash();

                // If it's a globalhash, zip right off to it.
                if(mMap != null && info != null && info.isGlobalHash()) {
                    zoomToPoint(info.getFinalDestinationLatLng());
                }
            }
        }
    }

    @Override
    public void handleLookupFailure(int reqFlags, int responseCode) {
        // If this was a Find Closest lookup, we need to make sure the button on
        // the fragment is re-enabled.
        if((reqFlags & StockService.FLAG_FIND_CLOSEST) == StockService.FLAG_FIND_CLOSEST) {
            clearFindClosest();
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        // Okay, so now we've got a Graticule.  Well, we will right here:
        Graticule g = new Graticule(latLng);
        removeDestinationPoint();

        // We can update the fragment with that.  We'll get updateGraticule back
        // so we can add the outline.
        mFrag.setNewGraticule(g);
    }

    @Override
    public void updateGraticule(@Nullable Graticule g) {
        // New graticule!
        outlineGraticule(g);

        removeDestinationPoint();

        // Fetch the stock, too.
        requestStock(g, mCalendar, StockService.FLAG_USER_INITIATED | StockService.FLAG_SELECT_A_GRATICULE);
    }

    @Override
    public void findClosest() {
        GoogleApiClient gClient = getGoogleClient();

        // TODO: I should really have a way to go on standby if this happens and
        // redo the request once the connection comes in.
        if(gClient == null)
            Log.w(DEBUG_TAG, "Tried to call findClosest when the Google API Client was either null or not connected!");

        // Same as with the initial zoom, only we're setting a Graticule.
        Location lastKnown = LocationServices.FusedLocationApi.getLastLocation(getGoogleClient());

        // We want the last known location to be at least SANELY recent.
        if(LocationUtil.isLocationNewEnough(lastKnown)) {
            applyFoundGraticule(lastKnown);
        } else {
            // This shouldn't be called OFTEN, but it'll probably be called.
            ErrorBanner banner = mCentralMap.getErrorBanner();
            banner.setErrorStatus(ErrorBanner.Status.NORMAL);
            banner.setText(mCentralMap.getText(R.string.search_label).toString());
            banner.setCloseVisible(false);
            banner.animateBanner(true);
            LocationRequest lRequest = LocationRequest.create();
            lRequest.setInterval(1000);
            lRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            LocationServices.FusedLocationApi.requestLocationUpdates(getGoogleClient(), lRequest, mFindClosestListener);
        }
    }

    @Override
    public void graticulePickerClosing() {
        mCentralMap.exitSelectAGraticuleMode();
    }

    private void applyFoundGraticule(Location loc) {
        // So, we found a location.  Good!  That's our start point.  Request!
        ErrorBanner banner = mCentralMap.getErrorBanner();
        banner.setErrorStatus(ErrorBanner.Status.NORMAL);
        banner.setText(mCentralMap.getText(R.string.stock_label).toString());
        banner.setCloseVisible(false);
        banner.animateBanner(true);

        mLastLocation = loc;
        requestStock(new Graticule(loc), mCalendar, StockService.FLAG_USER_INITIATED | StockService.FLAG_FIND_CLOSEST);
    }

    private void outlineGraticule(Graticule g) {
        // If we had an outline, remove it.
        if(mPolygon != null)
            mPolygon.remove();

        // A null Graticule means either there's no valid input or we're in
        // globalhash mode, so we just don't draw the outline at all.
        if(g == null) return;

        // And with that Graticule, we can get a Polygon.
        PolygonOptions opts = g.getPolygon()
                .strokeColor(mCentralMap.getResources().getColor(R.color.graticule_stroke))
                .strokeWidth(2)
                .fillColor(mCentralMap.getResources().getColor(R.color.graticule_fill));

        if(mMap != null) {
            mPolygon = mMap.addPolygon(opts);

            zoomToPoint(g.getCenterLatLng());
        }
    }

    private void zoomToPoint(LatLng newPoint) {
        // Zoom in as need be, cover an area of a couple graticules in any
        // direction, leaving space for the graticule picker on the bottom of
        // the screen.
        LatLngBounds.Builder builder = LatLngBounds.builder();
        LatLng point = new LatLng(newPoint.latitude - CLOSENESS_Y_DOWN, newPoint.longitude - CLOSENESS_X);
        builder.include(point);

        point = new LatLng(newPoint.latitude - CLOSENESS_Y_DOWN, newPoint.longitude + CLOSENESS_X);
        builder.include(point);

        point = new LatLng(newPoint.latitude + CLOSENESS_Y_UP, newPoint.longitude + CLOSENESS_X);
        builder.include(point);

        point = new LatLng(newPoint.latitude + CLOSENESS_Y_UP, newPoint.longitude - CLOSENESS_X);
        builder.include(point);

        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 0));
    }

    @Override
    public void changeCalendar(@NonNull Calendar newDate) {
        // Unlike in ExpeditionMode, we can immediately set our concept of the
        // current Calendar now.  It'll just wipe out the current point.
        mCalendar = newDate;
        if(mFrag.getGraticule() != null || mFrag.isGlobalhash())
            updateGraticule(mFrag.getGraticule());
    }

    private void clearFindClosest() {
        if(getGoogleClient() != null)
            LocationServices.FusedLocationApi.removeLocationUpdates(getGoogleClient(), mFindClosestListener);

        if(mFrag != null)
            mFrag.resetFindClosest();

        mLastLocation = null;
    }
}
