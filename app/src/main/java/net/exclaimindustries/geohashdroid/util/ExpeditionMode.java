/*
 * ExpeditionMode.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.content.SharedPreferences;
import android.graphics.Point;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.UnitConverter;
import net.exclaimindustries.geohashdroid.activities.CentralMap;
import net.exclaimindustries.geohashdroid.fragments.NearbyGraticuleDialogFragment;
import net.exclaimindustries.geohashdroid.services.StockService;
import net.exclaimindustries.geohashdroid.widgets.ErrorBanner;
import net.exclaimindustries.tools.LocationUtil;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * <code>ExpeditionMode</code> is the "main" mode, where it follows one point,
 * maybe shows eight close points, and allows for wiki mode or whatnot.
 */
public class ExpeditionMode
        extends CentralMap.CentralMapMode
        implements GoogleMap.OnInfoWindowClickListener,
                   GoogleMap.OnCameraChangeListener,
                   NearbyGraticuleDialogFragment.NearbyGraticuleClickedCallback {
    private static final String DEBUG_TAG = "ExpeditionMode";

    private static final String NEARBY_DIALOG = "nearbyDialog";

    public static final String DO_INITIAL_START = "doInitialStart";

    private boolean mWaitingOnInitialZoom = false;
    private boolean mWaitingOnEmptyStart = false;

    // This will hold all the nearby points we come up with.  They'll be
    // removed any time we get a new Info in.  It's a map so that we have a
    // quick way to switch to a new Info without having to call StockService.
    private final Map<Marker, Info> mNearbyPoints = new HashMap<>();

    private Info mCurrentInfo;
    private DisplayMetrics mMetrics;

    private Location mInitialCheckLocation;

    private LocationListener mInitialZoomListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // Got it!
            mWaitingOnInitialZoom = false;

            if(getGoogleClient() != null)
                LocationServices.FusedLocationApi.removeLocationUpdates(getGoogleClient(), this);

            if(!isCleanedUp()) {
                mCentralMap.getErrorBanner().animateBanner(false);
                zoomToIdeal(location);
            }
        }
    };

    private LocationListener mEmptyStartListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if(getGoogleClient() != null)
                LocationServices.FusedLocationApi.removeLocationUpdates(getGoogleClient(), this);

            // Second, ask for a stock using that location.
            if(!isCleanedUp()) {
                mInitialCheckLocation = location;
                requestStock(new Graticule(location), Calendar.getInstance(), StockService.FLAG_USER_INITIATED | StockService.FLAG_FIND_CLOSEST);
            }
        }
    };

    @Override
    public void setCentralMap(@NonNull CentralMap centralMap) {
        super.setCentralMap(centralMap);

        // Build up our metrics, too.
        mMetrics = new DisplayMetrics();
        centralMap.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    @Override
    public void init(@Nullable Bundle bundle) {
        // We listen to the map.  A lot.  For many, many reasons.
        mMap.setOnInfoWindowClickListener(this);
        mMap.setOnCameraChangeListener(this);

        // Set a title to begin with.  We'll get a new one soon, hopefully.
        setTitle(R.string.app_name);

        // Do we have a Bundle to un-Bundlify?
        if(bundle != null) {
            // And if we DO have a Bundle, does that Bundle simply tell us to
            // perform the initial startup?
            if(bundle.getBoolean(DO_INITIAL_START, false)) {
                doEmptyStart();
            } else {
                // We've either got a complete Info (highest priority) or a
                // combination of Graticule, boolean, and Calendar.  So we can
                // either start right back up from Info or we just make a call
                // out to StockService.
                //
                // Well, okay, we can also have no data at all, in which case we
                // do nothing but wait until the user goes to Select-A-Graticule
                // to get things moving.
                if(bundle.getParcelable(INFO) != null) {
                    mCurrentInfo = bundle.getParcelable(INFO);
                    requestStock(mCurrentInfo.getGraticule(), mCurrentInfo.getCalendar(), StockService.FLAG_USER_INITIATED | (needsNearbyPoints() ? StockService.FLAG_INCLUDE_NEARBY_POINTS : 0));
                } else if((bundle.containsKey(GRATICULE) || bundle.containsKey(GLOBALHASH)) && bundle.containsKey(CALENDAR)) {
                    // We've got a request to make!  Chances are, StockService
                    // will have this in cache.
                    Graticule g = bundle.getParcelable(GRATICULE);
                    boolean global = bundle.getBoolean(GLOBALHASH, false);
                    Calendar cal = (Calendar) bundle.getSerializable(CALENDAR);

                    // We only go through with this if we have a Calendar and
                    // either a globalhash or a Graticule.
                    if(cal != null && (global || g != null)) {
                        requestStock((global ? null : g), cal, StockService.FLAG_USER_INITIATED | (needsNearbyPoints() ? StockService.FLAG_INCLUDE_NEARBY_POINTS : 0));
                    }
                }
            }
        }

        mInitComplete = true;
    }

    @Override
    public void cleanUp() {
        super.cleanUp();

        // First, get rid of the callbacks.
        GoogleApiClient gClient = getGoogleClient();
        if(gClient != null)
            LocationServices.FusedLocationApi.removeLocationUpdates(gClient, mInitialZoomListener);

        // And the listens.
        if(mMap != null) {
            mMap.setOnInfoWindowClickListener(null);
            mMap.setOnCameraChangeListener(null);
        }

        // Remove the nearby points, too.  The superclass took care of the final
        // destination marker for us.
        removeNearbyPoints();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle bundle) {
        // At instance save time, stash away the last Info we knew about.  If we
        // have anything at all, it'll always be an Info.  If we don't have one,
        // we weren't displaying anything, and thus don't need to stash a
        // Calendar, Graticule, etc.
        bundle.putParcelable(INFO, mCurrentInfo);

        // Also, if we were in the middle of waiting on the empty start, write
        // that out to the bundle.  It'll come back in and we can start the
        // whole process anew.
        if(mWaitingOnEmptyStart)
            bundle.putBoolean(DO_INITIAL_START, true);
    }

    @Override
    public void pause() {
        // Stop listening!
        GoogleApiClient gClient = getGoogleClient();
        if(gClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(gClient, mInitialZoomListener);
            LocationServices.FusedLocationApi.removeLocationUpdates(gClient, mEmptyStartListener);
        }
    }

    @Override
    public void resume() {
        if(!mInitComplete) return;

        // If need be, start listening again!
        if(mWaitingOnInitialZoom)
            doInitialZoom();
        else
            doReloadZoom();

        // Also if need be, try that empty start again!
        if(mWaitingOnEmptyStart)
            doEmptyStart();
    }

    @Override
    public void onCreateOptionsMenu(MenuInflater inflater, Menu menu) {
        inflater.inflate(R.menu.centralmap_expedition, menu);
    }

    @Override
    public void handleInfo(Info info, Info[] nearby, int flags) {
        // PULL!
        if(mInitComplete) {
            mCentralMap.getErrorBanner().animateBanner(false);

            if(mWaitingOnEmptyStart) {
                mWaitingOnEmptyStart = false;
                // Coming in from the initial setup, we should have nearbys.
                // Get the closest one.
                Info inf = Info.measureClosest(mInitialCheckLocation, info, nearby);

                // Presto!  We've got our Graticule AND Calendar!  Now, to make
                // sure we've got all the nearbys set properly, ask StockService
                // for the data again, this time using the best one.  We'll get
                // it back in the else field quickly, as it's cached now.
                requestStock(inf.getGraticule(), inf.getCalendar(), StockService.FLAG_USER_INITIATED | (needsNearbyPoints() ? StockService.FLAG_INCLUDE_NEARBY_POINTS : 0));
            } else {
                setInfo(info);
                doNearbyPoints(nearby);
            }
        }
    }

    @Override
    public void handleLookupFailure(int reqFlags, int responseCode) {
        // Nothing here yet.
    }

    private void addNearbyPoint(Info info) {
        // This will get called repeatedly up to eight times (in rare cases,
        // five times) when we ask for nearby points.  All we need to do is put
        // those points on the map, and stuff them in the map.  Two different
        // varieties of map.
        synchronized(mNearbyPoints) {
            // The title might be a wee bit unwieldy, as it also has to include
            // the graticule's location.  We DO know that this isn't a
            // Globalhash, though.
            String title;
            String gratString = info.getGraticule().getLatitudeString(false) + " " + info.getGraticule().getLongitudeString(false);
            if(info.isRetroHash()) {
                title = mCentralMap.getString(R.string.marker_title_nearby_retro_hashpoint,
                        DateFormat.getDateInstance(DateFormat.LONG).format(info.getDate()),
                        gratString);
            } else {
                title = mCentralMap.getString(R.string.marker_title_nearby_today_hashpoint,
                        gratString);
            }

            // Snippet!  Snippet good.
            String snippet = UnitConverter.makeFullCoordinateString(mCentralMap, info.getFinalLocation(), false, UnitConverter.OUTPUT_LONG);

            Marker nearby = mMap.addMarker(new MarkerOptions()
                    .position(info.getFinalDestinationLatLng())
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.final_destination_disabled))
                    .anchor(0.5f, 1.0f)
                    .title(title)
                    .snippet(snippet));

            mNearbyPoints.put(nearby, info);

            // Finally, make sure it should be visible.  Do this per-marker, as
            // we're not always sure we've got the full set of eight (edge case
            // involving the poles) or if all of them will come in at the same
            // time (edge cases involving 30W or 180E/W).
            checkMarkerVisibility(nearby);
        }
    }

    private void checkMarkerVisibility(Marker m) {
        // On a camera change, we need to determine if the nearby markers
        // (assuming they exist to begin with) need to be drawn.  If they're too
        // far away, they'll get in a jumbled mess with the final destination
        // flag, and we don't want that.  This is more or less similar to the
        // clustering support in the Google Maps API v2 utilities, but since we
        // always know the markers will be in a very specific cluster, we can
        // just simplify it all into this.

        // First, if we're not in the middle of an expedition, don't worry about
        // it.
        if(mCurrentInfo != null) {
            // Figure out how far this marker is from the final point.  Hooray
            // for Pythagoras!
            Point dest = mMap.getProjection().toScreenLocation(mDestination.getPosition());
            Point mark = mMap.getProjection().toScreenLocation(m.getPosition());

            // toScreenLocation gives us values as screen pixels, not display
            // pixels.  Let's convert that to display pixels for sanity's sake.
            double dist = Math.sqrt(Math.pow((dest.x - mark.x), 2) + Math.pow(dest.y - mark.y, 2)) / mMetrics.density;

            boolean visible = true;

            // 50dp should be roughly enough.  If I need to change this later,
            // it's going to be because the images will scale by pixel density.
            if(dist < 50)
                visible = false;

            m.setVisible(visible);
        }
    }

    private void doNearbyPoints(Info[] nearby) {
        removeNearbyPoints();

        // We should just be able to toss one point in for each Info here.
        if(nearby != null) {
            for(Info info : nearby)
                addNearbyPoint(info);
        }
    }

    private void removeNearbyPoints() {
        synchronized(mNearbyPoints) {
            for(Marker m : mNearbyPoints.keySet()) {
                m.remove();
            }
            mNearbyPoints.clear();
        }
    }

    private void setInfo(final Info info) {
        mCurrentInfo = info;

        if(!mInitComplete) return;

        removeDestinationPoint();

        // I suppose a null Info MIGHT come in.  I don't know how yet, but sure,
        // let's assume a null Info here means we just don't render anything.
        if(mCurrentInfo != null) {
            mCentralMap.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Marker!
                    addDestinationPoint(info);

                    // With an Info in hand, we can also change the title.
                    StringBuilder newTitle = new StringBuilder();
                    if(info.isGlobalHash())
                        newTitle.append(mCentralMap.getString(R.string.title_part_globalhash));
                    else
                        newTitle.append(info.getGraticule().getLatitudeString(false)).append(' ').append(info.getGraticule().getLongitudeString(false));
                    newTitle.append(", ");
                    newTitle.append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(info.getDate()));
                    setTitle(newTitle.toString());

                    // Now, the Mercator projection that the map uses clips at
                    // around 85 degrees north and south.  If that's where the
                    // point is (if that's the Globalhash or if the user
                    // legitimately lives in Antarctica), we'll still try to
                    // draw it, but we'll throw up a warning that the marker
                    // might not show up.  Sure is a good thing an extreme south
                    // Globalhash showed up when I was testing this, else I
                    // honestly might've forgot.
                    ErrorBanner banner = mCentralMap.getErrorBanner();
                    if(Math.abs(info.getLatitude()) > 85) {
                        banner.setErrorStatus(ErrorBanner.Status.WARNING);
                        banner.setText(mCentralMap.getString(R.string.warning_outside_of_projection));
                        banner.animateBanner(true);
                    }

                    // Finally, try to zoom the map to where it needs to be,
                    // assuming we're connected to the APIs and have a location.
                    // This is why you make sure things are ready before you
                    // call init.
                    doInitialZoom();
                }
            });

        } else {
            // Otherwise, make sure the title's back to normal.
            setTitle(R.string.app_name);
        }
    }

    private void zoomToIdeal(Location current) {
        // Where "current" means the user's current location, and we're zooming
        // relative to the final destination, if we have it yet.  Let's check
        // that latter part first.
        if(mCurrentInfo == null) {
            Log.i(DEBUG_TAG, "zoomToIdeal was called before an Info was set, ignoring...");
            return;
        }

        // As a side note, yes, I COULD probably mash this all down to one line,
        // but I want this to be readable later without headaches.
        LatLngBounds bounds = LatLngBounds.builder()
                .include(new LatLng(current.getLatitude(), current.getLongitude()))
                .include(mCurrentInfo.getFinalDestinationLatLng())
                .build();

        CameraUpdate cam = CameraUpdateFactory.newLatLngBounds(bounds, mCentralMap.getResources().getDimensionPixelSize(R.dimen.map_zoom_padding));

        mMap.animateCamera(cam);
    }

    private void doReloadZoom() {
        // This happens on every resume().  The only real difference is that
        // this is protected by a preference, while initial zoom happens any
        // time.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mCentralMap);
        boolean autoZoom = prefs.getBoolean(GHDConstants.PREF_AUTOZOOM, true);

        if(autoZoom) doInitialZoom();
    }

    private void doInitialZoom() {
        GoogleApiClient gClient = getGoogleClient();

        if(gClient == null)
            Log.w(DEBUG_TAG, "Tried calling doInitialZoom() when the Google API client was null or not connected!");

        Location lastKnown = LocationServices.FusedLocationApi.getLastLocation(gClient);

        // We want the last known location to be at least SANELY recent.
        if(LocationUtil.isLocationNewEnough(lastKnown)) {
            zoomToIdeal(lastKnown);
        } else {
            // Otherwise, wait for the first update and use that for an initial
            // zoom.
            ErrorBanner banner = mCentralMap.getErrorBanner();
            banner.setErrorStatus(ErrorBanner.Status.NORMAL);
            banner.setText(mCentralMap.getText(R.string.search_label).toString());
            banner.setCloseVisible(false);
            banner.animateBanner(true);

            LocationRequest lRequest = LocationRequest.create();
            lRequest.setInterval(1000);
            lRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            mWaitingOnInitialZoom = true;

            LocationServices.FusedLocationApi.requestLocationUpdates(gClient, lRequest, mInitialZoomListener);
        }
    }

    private void doEmptyStart() {
        mWaitingOnEmptyStart = true;

        // For an initial start, first things first, we ask for the current
        // location.  If it's new enough, we can go with that, as usual.
        Location loc = LocationServices.FusedLocationApi.getLastLocation(getGoogleClient());

        if(LocationUtil.isLocationNewEnough(loc)) {
            mInitialCheckLocation = loc;
            requestStock(new Graticule(loc), Calendar.getInstance(), StockService.FLAG_USER_INITIATED | StockService.FLAG_FIND_CLOSEST);
        } else {
            // Otherwise, it's off to the races.
            ErrorBanner banner = mCentralMap.getErrorBanner();
            banner.setErrorStatus(ErrorBanner.Status.NORMAL);
            banner.setText(mCentralMap.getText(R.string.search_label).toString());
            banner.setCloseVisible(false);
            banner.animateBanner(true);

            LocationRequest lRequest = LocationRequest.create();
            lRequest.setInterval(1000);
            lRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            LocationServices.FusedLocationApi.requestLocationUpdates(getGoogleClient(), lRequest, mEmptyStartListener);
        }
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        // If a nearby marker's info window was clicked, that means we can
        // switch to another point.
        if(mNearbyPoints.containsKey(marker)) {
            final Info newInfo = mNearbyPoints.get(marker);

            // Ask first!  Get the current location (if possible) and prompt the
            // user with a distance.
            Location lastKnown = null;
            GoogleApiClient gClient = getGoogleClient();
            if(gClient != null)
                lastKnown = LocationServices.FusedLocationApi.getLastLocation(gClient);

            // Then, we've got a fragment that'll do this sort of work for us.
            NearbyGraticuleDialogFragment frag = NearbyGraticuleDialogFragment.newInstance(newInfo, lastKnown);
            frag.setCallback(this);
            frag.show(mCentralMap.getFragmentManager(), NEARBY_DIALOG);
        }
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        // We're going to check visibility on each marker individually.  This
        // might make some of them vanish while others remain on, owing to our
        // good friend the Pythagorean Theorem and neat Mercator projection
        // tricks.
        for(Marker m : mNearbyPoints.keySet())
            checkMarkerVisibility(m);
    }

    @Override
    public void nearbyGraticuleClicked(Info info) {
        // Info!
        requestStock(info.getGraticule(), info.getCalendar(), StockService.FLAG_USER_INITIATED | (needsNearbyPoints() ? StockService.FLAG_INCLUDE_NEARBY_POINTS : 0));
    }

    @Override
    public void changeCalendar(@NonNull Calendar newDate) {
        // New Calendar!  That means we ask for more stock data!  It doesn't
        // necessarily mean a new point is coming in, but it does mean we're
        // making a request, at least.  The StockService broadcast will let us
        // know what's going on later.
        if(mCurrentInfo != null)
            requestStock(mCurrentInfo.getGraticule(), newDate, StockService.FLAG_USER_INITIATED | (needsNearbyPoints() ? StockService.FLAG_INCLUDE_NEARBY_POINTS : 0));
    }

    private boolean needsNearbyPoints() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mCentralMap);
        return prefs.getBoolean(GHDConstants.PREF_NEARBY_POINTS, true);
    }

}
