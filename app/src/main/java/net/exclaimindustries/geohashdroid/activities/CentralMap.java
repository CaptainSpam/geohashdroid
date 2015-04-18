/**
 * CentralMap.java
 * Copyright (C)2015 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.UnitConverter;
import net.exclaimindustries.geohashdroid.services.StockService;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.widgets.ErrorBanner;
import net.exclaimindustries.tools.LocationUtil;

import java.text.DateFormat;
import java.util.Calendar;

/**
 * CentralMap replaces MainMap as the map display.  Unlike MainMap, it also
 * serves as the entry point for the entire app.  These comments are going to
 * make so much sense later when MainMap is little more than a class that only
 * exists on the legacy branch.
 */
public class CentralMap extends Activity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String DEBUG_TAG = "CentralMap";

    private boolean mSelectAGraticule = false;
    private Info mCurrentInfo;
    private Marker mDestination;
    private GoogleMap mMap;
    private boolean mMapIsReady = false;
    private GoogleApiClient mGoogleClient;

    private ErrorBanner mBanner;

    private class StockReceiver extends BroadcastReceiver {
        private long mWaitingOnThisOne = -1;

        public void setWaitingId(long id) {
            mWaitingOnThisOne = id;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // A stock result arrives!  Let's make sure it's really what we're
            // looking for.  We're assuming this is already an
            // ACTION_STOCK_RESULT, else this would just be broken.
            long reqId = intent.getLongExtra(StockService.EXTRA_REQUEST_ID, -1);
            if(reqId != mWaitingOnThisOne) return;

            // Well, it's what we're looking for.  What was the result?
            int responseCode = intent.getIntExtra(StockService.EXTRA_RESPONSE_CODE, StockService.RESPONSE_NETWORK_ERROR);

            switch(responseCode) {
                case StockService.RESPONSE_OKAY:
                    // Hey, would you look at that, it actually worked!  So, get
                    // the Info out of it and fire it away!
                    setInfo((Info)intent.getParcelableExtra(StockService.EXTRA_INFO));
                    break;
                case StockService.RESPONSE_NOT_POSTED_YET:
                    mBanner.setText(getString(R.string.error_not_yet_posted));
                    mBanner.setErrorStatus(ErrorBanner.Status.ERROR);
                    mBanner.animateBanner(true);
                    break;
                case StockService.RESPONSE_NO_CONNECTION:
                    mBanner.setText(getString(R.string.error_no_connection));
                    mBanner.setErrorStatus(ErrorBanner.Status.ERROR);
                    mBanner.animateBanner(true);
                    break;
                case StockService.RESPONSE_NETWORK_ERROR:
                    mBanner.setText(getString(R.string.error_server_failure));
                    mBanner.setErrorStatus(ErrorBanner.Status.ERROR);
                    mBanner.animateBanner(true);
                    break;
                default:
                    break;
            }
        }
    }

    private StockReceiver mStockReceiver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load up!
        if(savedInstanceState != null) {
            if(savedInstanceState.containsKey("info")) {
                mCurrentInfo = savedInstanceState.getParcelable("info");
            }
        }

        setContentView(R.layout.centralmap);

        // We deal with locations, so we deal with the GoogleApiClient.  It'll
        // connect during onStart.
        mGoogleClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mBanner = (ErrorBanner)findViewById(R.id.error_banner);
        mStockReceiver = new StockReceiver();

        // Get a map ready.  We'll know when we've got it.  Oh, we'll know.
        MapFragment mapFrag = (MapFragment)getFragmentManager().findFragmentById(R.id.map);
        mapFrag.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;

                // I could swear you could do this in XML...
                UiSettings set = mMap.getUiSettings();

                // The My Location button has to go off, as we're going to have the
                // infobox right around there.
                set.setMyLocationButtonEnabled(false);

                mMap.setMyLocationEnabled(true);

                // Now, set the flag that tells everything else we're ready.
                // We'll need this because we're calling the very methods that
                // depend on it, as noted in the next comment.
                mMapIsReady = true;

                // The entire point of this async callback is that we don't have
                // any clue when it COULD come back.  This means, in theory,
                // that it MIGHT come back after the user asks for a stock or
                // whatnot, meaning an Info is waiting to be acted upon.  In
                // fact, the user might've also asked for Select-A-Graticule
                // mode.
                if(mSelectAGraticule) {
                    // TODO: Make Select-A-Graticule mode.
                } else {
                    setInfo(mCurrentInfo);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        // The receiver goes right off as soon as we pause.
        unregisterReceiver(mStockReceiver);

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // The receiver goes on during onResume, even though we might not be
        // waiting for anything yet.
        IntentFilter filt = new IntentFilter();
        filt.addAction(StockService.ACTION_STOCK_RESULT);

        registerReceiver(mStockReceiver, filt);
    }

    @Override
    protected void onStart() {
        super.onRestart();

        // Service up!
        mGoogleClient.connect();
    }

    @Override
    protected void onStop() {
        // Service down!
        mGoogleClient.disconnect();

        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Also, keep the latest Info around.
        // TODO: Later, we'll need to know NOT to reload the Info at startup
        // time.  Determine the correct way to determine that.
        outState.putParcelable("info", mCurrentInfo);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        if(mSelectAGraticule)
            inflater.inflate(R.menu.centralmap_selectagraticule, menu);
        else
            inflater.inflate(R.menu.centralmap_expedition, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_whatisthis: {
                // The everfamous and much-beloved "What's Geohashing?" button,
                // because honestly, this IS sort of confusing if you're
                // expecting something for geocaching.
                Intent i = new Intent();
                i.setAction(Intent.ACTION_VIEW);
                i.setData(Uri.parse("http://wiki.xkcd.com/geohashing/How_it_works"));
                startActivity(i);
                return true;
            }
            case R.id.action_preferences: {
                // Preferences!  To the Preferencemobile!
                Intent i = new Intent(this, PreferencesActivity.class);
                startActivity(i);
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setInfo(final Info info) {
        mCurrentInfo = info;

        // If we're not ready for the map yet, give up.  When we DO get ready,
        // we'll be called again.
        if(!mMapIsReady) return;

        // In any case, a new Info means the old one's invalid, so the old
        // Marker goes away.
        if(mDestination != null) {
            mDestination.remove();
        }

        // I suppose a null Info MIGHT come in.  I don't know how yet, but sure,
        // let's assume a null Info here means we just don't render anything.
        if(mCurrentInfo != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // We need a marker!  And that marker needs a title.  And
                    // that title depends on globalhashiness and retroness.
                    String title;

                    if(!info.isRetroHash()) {
                        // Non-retro hashes don't have today's date on them.
                        // They just have "today's [something]".
                        if(info.isGlobalHash()) {
                            title = getString(R.string.marker_title_today_globalpoint);
                        } else {
                            title = getString(R.string.marker_title_today_hashpoint);
                        }
                    } else {
                        // Retro hashes, however, need a date string.
                        String date = DateFormat.getDateInstance(DateFormat.LONG).format(info.getDate());

                        if(info.isGlobalHash()) {
                            title = getString(R.string.marker_title_retro_globalpoint, date);
                        } else {
                            title = getString(R.string.marker_title_retro_hashpoint, date);
                        }
                    }

                    // The snippet's just the coordinates in question.  Further
                    // details will go in the infobox.
                    String snippet = UnitConverter.makeFullCoordinateString(CentralMap.this, info.getFinalLocation(), false, UnitConverter.OUTPUT_LONG);

                    // Under the current marker image, the anchor is the very
                    // bottom, halfway across.  Presumably, that's what the
                    // default icon also uses, but we're not concerned with the
                    // default icon, now, are we?
                    mDestination = mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(info.getLatitude(), info.getLongitude()))
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.final_destination))
                            .anchor(0.5f, 1.0f)
                            .title(title)
                            .snippet(snippet));

                    // With an Info in hand, we can also change the title.
                    StringBuilder newTitle = new StringBuilder();
                    if(info.isGlobalHash()) newTitle.append(getString(R.string.title_part_globalhash));
                    else newTitle.append(info.getGraticule().getLatitudeString(false)).append(' ').append(info.getGraticule().getLongitudeString(false));
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
                    if(Math.abs(info.getLatitude()) > 85) {
                        mBanner.setErrorStatus(ErrorBanner.Status.WARNING);
                        mBanner.setText(getString(R.string.warning_outside_of_projection));
                        mBanner.animateBanner(true);
                    }

                    // Finally, try to zoom the map to where it needs to be,
                    // assuming we're connected to the APIs and have a location.
                    // Note that when the APIs connect, this'll be called, so we
                    // don't need to set up a callback or whatnot.
                    if(mGoogleClient != null && mGoogleClient.isConnected()) {
                        Location lastKnown = LocationServices.FusedLocationApi.getLastLocation(mGoogleClient);

                        // Also, we want the last known location to be at least
                        // SANELY recent.
                        if(lastKnown != null && LocationUtil.isLocationNewEnough(lastKnown)) {
                            zoomToIdeal(lastKnown);
                        } else {
                            // Otherwise, wait for the first update and use that
                            // for an initial zoom.
                            mBanner.setErrorStatus(ErrorBanner.Status.NORMAL);
                            mBanner.setText(getText(R.string.search_label).toString());
                            mBanner.animateBanner(true);
                            LocationRequest lRequest = LocationRequest.create();
                            lRequest.setInterval(1000);
                            lRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleClient, lRequest, new LocationListener() {
                                @Override
                                public void onLocationChanged(Location location) {
                                    // Got it!
                                    LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleClient, this);
                                    mBanner.animateBanner(false);
                                    zoomToIdeal(location);
                                }
                            });
                        }
                    }
                }
            });

        } else {
            // Otherwise, make sure the title's back to normal.
            setTitle(R.string.app_name);
        }
    }

    private void requestStock(Graticule g, Calendar cal, int flags) {
        // Make sure the banner's going away!
        mBanner.animateBanner(false);

        // As a request ID, we'll use the current date.
        long date = cal.getTimeInMillis();

        Intent i = new Intent(this, StockService.class)
                .putExtra(StockService.EXTRA_DATE, cal)
                .putExtra(StockService.EXTRA_GRATICULE, g)
                .putExtra(StockService.EXTRA_REQUEST_ID, date)
                .putExtra(StockService.EXTRA_REQUEST_FLAGS, flags);

        mStockReceiver.setWaitingId(date);

        WakefulIntentService.sendWakefulWork(this, i);
    }

    @Override
    public void onConnected(Bundle bundle) {
        // If we're coming back from somewhere, reset the marker.  This is just
        // in case the user changes coordinate preferences, as the marker only
        // updates its internal info when it's created.
        setInfo(mCurrentInfo);
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Since the location API doesn't appear to connect back to the network,
        // I'm not sure I need to do anything special here.  I'm not even
        // entirely convinced the connection CAN become suspended after it's
        // made unless things are completely hosed.
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // I'm not really certain how this can fail to connect, and so I'm not
        // really certain what to do if it does.
    }

    private void zoomToIdeal(Location current) {
        // Where "current" means the user's current location, and we're zooming
        // relative to the final destination, if we have it yet.  Let's check
        // that latter part first.
        if(mCurrentInfo == null) {
            Log.i(DEBUG_TAG, "zoomToIdeal was called before an Info was set, ignoring...");
            return;
        }

        if(mGoogleClient == null || !mGoogleClient.isConnected()) {
            Log.i(DEBUG_TAG, "zoomToIdeal was called when the Google API client wasn't connected, ignoring...");
            return;
        }

        // As a side note, yes, I COULD probably mash this all down to one line,
        // but I want this to be readable later without headaches.
        LatLngBounds bounds = LatLngBounds.builder()
                .include(new LatLng(current.getLatitude(), current.getLongitude()))
                .include(new LatLng(mCurrentInfo.getLatitude(), mCurrentInfo.getLongitude()))
                .build();

        CameraUpdate cam = CameraUpdateFactory.newLatLngBounds(bounds, getResources().getDimensionPixelSize(R.dimen.map_zoom_padding));

        mMap.animateCamera(cam);
    }
}
