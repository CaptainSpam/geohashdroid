/*
 * KnownLocationsPicker.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.os.Bundle;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.KnownLocation;

import java.util.List;

/**
 * KnownLocationsPicker is another map-containing Activity.  This one allows the
 * user to set "known locations", which can trigger notifications if the day's
 * hashpoint is near one of them.  This isn't CentralMap, mind; it doesn't have
 * the entire CentralMapMode architecture or stock-grabbing functionality.  And
 * for that, I'm thankful.
 */
public class KnownLocationsPicker
        extends BaseMapActivity {

    private GoogleMap mMap;

    private boolean mMapIsReady = false;

    private List<KnownLocation> mLocations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // We've got a layout, so let's use the layout.
        setContentView(R.layout.known_locations);

        // Now, we'll need to get the list of KnownLocations right away so we
        // can put them on the map.  Well, I guess not RIGHT away.  We still
        // have to wait on the map callbacks, but still, let's fetch them now.
        mLocations = KnownLocation.getAllKnownLocations(this);

        // Prep a client (it'll get going during onStart)!
        mGoogleClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        // Our friend the map needs to get ready, too.
        MapFragment mapFrag = (MapFragment)getFragmentManager().findFragmentById(R.id.map);
        mapFrag.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;

                // I could swear you could do this in XML...
                UiSettings set = mMap.getUiSettings();

                // The My Location button has to go off, as the search bar sort
                // of takes up that space.
                set.setMyLocationButtonEnabled(false);

                // Same as CentralMap, we need to wait on both this AND the Maps
                // API to be ready.
                mMapIsReady = true;
                doReadyChecks();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

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
    public void onConnected(Bundle bundle) {
        if(!isFinishing())
            doReadyChecks();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    private boolean doReadyChecks() {
        if(mMapIsReady && mGoogleClient != null && mGoogleClient.isConnected()) {
            return true;
        } else {
            return false;
        }
    }
}
