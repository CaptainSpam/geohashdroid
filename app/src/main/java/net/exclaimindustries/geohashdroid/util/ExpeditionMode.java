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
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;

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
    private static final String NEARBY_POINTS = "nearbyPoints";

    private boolean mInitComplete = false;

    // This will hold all the nearby points we come up with.  They'll be
    // removed any time we get a new Info in.  It's a map so that we have a
    // quick way to switch to a new Info without having to call StockService.
    private final Map<Marker, Info> mNearbyPoints = new HashMap<>();

    private Info mCurrentInfo;
    private DisplayMetrics mMetrics;

    private LocationListener mInitialZoomListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // Got it!
            LocationServices.FusedLocationApi.removeLocationUpdates(getGoogleClient(), this);
            mCentralMap.getErrorBanner().animateBanner(false);
            zoomToIdeal(location);
        }
    };

    @Override
    public void setCentralMap(CentralMap centralMap) {
        super.setCentralMap(centralMap);

        // Build up our metrics, too.
        mMetrics = new DisplayMetrics();
        centralMap.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    @Override
    public void init(Bundle bundle) {
        // We listen to the map.  A lot.  For many, many reasons.
        mMap.setOnInfoWindowClickListener(this);
        mMap.setOnCameraChangeListener(this);

        // Do we have a Bundle to un-Bundlify?
        if(bundle != null) {
            mCurrentInfo = bundle.getParcelable(INFO);
            setInfo(mCurrentInfo);

            Parcelable[] nearbys = bundle.getParcelableArray(NEARBY_POINTS);

            // mCurrentInfo also has to be not-null, as we can't have nearby
            // points if we don't have a point to begin with.  This is mostly a
            // sanity check.
            if(mCurrentInfo != null && nearbys != null) {
                for(Parcelable inf : nearbys) {
                    if(inf instanceof Info) {
                        handleInfo((Info)inf, StockService.FLAG_NEARBY_POINT);
                    }
                }
            }
        }

        mInitComplete = true;
    }

    @Override
    public void cleanUp(Bundle bundle) {
        // First, get rid of the callbacks.
        GoogleApiClient gClient = getGoogleClient();
        if(gClient != null)
            LocationServices.FusedLocationApi.removeLocationUpdates(gClient, mInitialZoomListener);

        // And the listens.
        mMap.setOnInfoWindowClickListener(null);
        mMap.setOnCameraChangeListener(null);

        // Now, if there's a Bundle handy, stash away the last Info we knew
        // about, as well as all the nearby points (the latter just for the sake
        // of efficiency, so we can load them back up without making calls to
        // StockService).  If we have anything at all, it'll always be an Info.
        // If we don't have one, we weren't displaying anything, and thus don't
        // need to stash a Calendar, Graticule, etc.
        if(bundle != null) {
            bundle.putParcelable(INFO, mCurrentInfo);
            Parcelable[] arr = new Parcelable[] {};
            bundle.putParcelableArray(NEARBY_POINTS, mNearbyPoints.values().toArray(arr));
        }
    }

    @Override
    public void handleInfo(Info info, int flags) {
        // PULL!
        if(mInitComplete) {
            if((flags & StockService.FLAG_NEARBY_POINT) != 0) {
                // It's a nearby point!
                addNearbyPoint(info);
            } else {
                setInfo(info);
                doNearbyPoints();
            }
        }
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

    private void doNearbyPoints() {
        if(mCurrentInfo == null) return;

        removeNearbyPoints();

        // If the user wants the nearby points (AND this isn't a Globalhash), we
        // need to request them.  Now, the way we're going to do this may seem
        // inefficient, firing off (up to) eight more Intents to StockService,
        // but it covers the bizarre cases of people trying to Geohash directly
        // on the 30W or 180E/W lines, as well as any oddities related to the
        // zero graticules.  Besides, it's best to keep StockService simple.
        // The cache will ensure the points will come back promptly in the
        // general case.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mCentralMap);
        if(!mCurrentInfo.isGlobalHash() && prefs.getBoolean(GHDConstants.PREF_NEARBY_POINTS, true)) {
            Graticule g = mCurrentInfo.getGraticule();

            for(int i = -1; i <= 1; i++) {
                for(int j = -1; j <= 1; j++) {
                    // Zero and zero isn't a nearby point, that's the very point
                    // we're at right now!
                    if(i == 0 && j == 0) continue;

                    // If the user's truly adventurous enough to go to the 90N/S
                    // graticules, there aren't any nearby points north/south of
                    // where they are.  Also, the nearby points aren't going to
                    // be drawn anyway due to the projection, but hey, that's
                    // nitpicking.
                    if(Math.abs((g.isSouth() ? -1 : 1) * g.getLatitude() + i) > 90)
                        continue;

                    // Make a new Graticule, properly offset...
                    Graticule offset = Graticule.createOffsetFrom(g, i, j);

                    // ...and make the request, WITH the appropriate flag set.
                    mCentralMap.requestStock(offset, mCurrentInfo.getCalendar(), StockService.FLAG_AUTO_INITIATED | StockService.FLAG_NEARBY_POINT);
                }
            }
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
                    mCentralMap.setTitle(newTitle.toString());

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
            mCentralMap.setTitle(R.string.app_name);
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

    private void doInitialZoom() {
        GoogleApiClient gClient = getGoogleClient();

        Location lastKnown = LocationServices.FusedLocationApi.getLastLocation(gClient);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mCentralMap);
        boolean autoZoom = prefs.getBoolean(GHDConstants.PREF_AUTOZOOM, true);

        if(!autoZoom) return;

        // We want the last known location to be at least SANELY recent.
        if(lastKnown != null && LocationUtil.isLocationNewEnough(lastKnown)) {
            zoomToIdeal(lastKnown);
        } else {
            // Otherwise, wait for the first update and use that for an initial
            // zoom.
            ErrorBanner banner = mCentralMap.getErrorBanner();
            banner.setErrorStatus(ErrorBanner.Status.NORMAL);
            banner.setText(mCentralMap.getText(R.string.search_label).toString());
            banner.animateBanner(true);

            LocationRequest lRequest = LocationRequest.create();
            lRequest.setInterval(1000);
            lRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            LocationServices.FusedLocationApi.requestLocationUpdates(gClient, lRequest, mInitialZoomListener);
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
        setInfo(info);
        doNearbyPoints();
    }
}
