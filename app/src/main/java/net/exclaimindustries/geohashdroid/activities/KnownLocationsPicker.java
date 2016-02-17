/*
 * KnownLocationsPicker.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.common.api.GoogleApiClient;
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
import net.exclaimindustries.geohashdroid.util.KnownLocation;
import net.exclaimindustries.geohashdroid.util.UnitConverter;

import java.util.List;

/**
 * KnownLocationsPicker is another map-containing Activity.  This one allows the
 * user to set "known locations", which can trigger notifications if the day's
 * hashpoint is near one of them.  This isn't CentralMap, mind; it doesn't have
 * the entire CentralMapMode architecture or stock-grabbing functionality.  And
 * for that, I'm thankful.
 */
public class KnownLocationsPicker
        extends BaseMapActivity
        implements GoogleMap.OnMapClickListener,
                   GoogleMap.OnMarkerClickListener,
                   GoogleMap.OnInfoWindowClickListener,
                   GoogleMap.OnInfoWindowCloseListener {

    private GoogleMap mMap;

    private boolean mMapIsReady = false;

    private List<KnownLocation> mLocations;
    private Marker mMapClickMarker;

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

                // Get ready to listen for clicks!
                mMap.setOnMapClickListener(KnownLocationsPicker.this);

                // Activate My Location if permissions are right.
                if(checkLocationPermissions(0))
                    permissionsGranted();

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(permissions.length <= 0 || grantResults.length <= 0)
            return;

        // CentralMap will generally be handling location permissions.  So...
        if(grantResults[0] == PackageManager.PERMISSION_DENIED) {
            // If permissions get denied here, we ignore them and just don't
            // enable My Location support.
            mPermissionsDenied = true;
        } else {
            // Permissions... HO!!!!
            permissionsGranted();
            mPermissionsDenied = false;
        }
    }

    private void permissionsGranted() {
        mMap.setMyLocationEnabled(true);
    }

    private boolean doReadyChecks() {
        if(mMapIsReady && mGoogleClient != null && mGoogleClient.isConnected()) {
            // The map should be centered on the currently-known locations.
            // Otherwise, well, default to dead zero, I guess.
            if(!mLocations.isEmpty()) {
                LatLngBounds.Builder builder = LatLngBounds.builder();

                for(KnownLocation kl : mLocations) {
                    builder.include(kl.getLatLng());
                }

                LatLngBounds bounds = builder.build();
                CameraUpdate cam = CameraUpdateFactory.newLatLngBounds(bounds, getResources().getDimensionPixelSize(R.dimen.map_zoom_padding));
                mMap.animateCamera(cam);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        // If there's already a marker, just clear it out.
        if(mMapClickMarker != null) {
            mMapClickMarker.remove();
            mMapClickMarker = null;
        }

        // If the user taps the map (and NOT a marker or info window), we place
        // a marker on the map and offer the user the option to add that as a
        // known location.
        MarkerOptions options = createMarker(latLng, null);

        mMapClickMarker = mMap.addMarker(options);
        mMapClickMarker.showInfoWindow();
    }

    @Override
    public void onInfoWindowClick(Marker marker) {

    }

    @Override
    public void onInfoWindowClose(Marker marker) {
        // An info window closes if the user taps away when the window is open.
        // If that was the current map click marker, we also want that marker to
        // go away.
        if(marker.equals(mMapClickMarker)) {
            mMapClickMarker.remove();
            mMapClickMarker = null;
        }
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        return false;
    }

    private MarkerOptions createMarker(@NonNull LatLng latLng, @Nullable String title) {
        // This builds up the basic marker for a potential KnownLocation.  By
        // "potential", I mean something that isn't stored yet as a
        // KnownLocation, such as search results or map taps.  KnownLocation
        // ITSELF has a makeMarker method.
        if(title == null || title.isEmpty())
            title = UnitConverter.makeFullCoordinateString(this, latLng, false, UnitConverter.OUTPUT_SHORT);

        return new MarkerOptions()
                .position(latLng)
                .flat(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.known_location_tap_marker))
                .anchor(0.5f, 0.5f)
                .title(title)
                .snippet(getString(R.string.known_locations_tap_to_add));
    }
}
