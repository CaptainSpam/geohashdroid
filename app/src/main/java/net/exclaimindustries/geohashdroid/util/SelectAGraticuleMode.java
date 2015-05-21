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
import android.os.Bundle;
import android.support.annotation.Nullable;

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

import java.util.Calendar;

/**
 * <code>SelectAGraticuleMode</code> encompasses the user selecting a Graticule
 * from the map.
 */
public class SelectAGraticuleMode
        extends CentralMap.CentralMapMode
        implements GoogleMap.OnMapClickListener,
                   GraticulePickerFragment.GraticulePickerListener {
    private static final double CLOSENESS_X = 2.5;
    private static final double CLOSENESS_Y_UP = 1.5;
    private static final double CLOSENESS_Y_DOWN = 3.5;

    private Polygon mPolygon;

    private GraticulePickerFragment mFrag;

    private boolean mInitComplete = false;

    private Calendar mCalendar;

    @Override
    public void init(@Nullable Bundle bundle) {
        // Hi, map!
        mMap.setOnMapClickListener(this);

        // The fragment might already be there if the Activity's being rebuilt.
        // If not, we need to place it there.
        FragmentManager manager = mCentralMap.getFragmentManager();
        if(manager.findFragmentById(R.id.graticulepicker) == null) {
            // If we need to build the fragment, we might also want whatever
            // graticule the user started with.
            Graticule g = null;
            boolean globalHash = false;

            if(bundle != null) {
                if(bundle.containsKey(INFO)) {
                    Info i = bundle.getParcelable(INFO);
                    if(i != null) {
                        g = i.getGraticule();
                        mCalendar = i.getCalendar();
                        globalHash = i.isGlobalHash();
                    }
                } else {
                    if(bundle.containsKey(GRATICULE)) {
                        g = bundle.getParcelable(GRATICULE);
                    }

                    if(bundle.containsKey(CALENDAR)) {
                        mCalendar = (Calendar) bundle.getSerializable(CALENDAR);
                    }

                    if(bundle.containsKey(GLOBALHASH)) {
                        globalHash = bundle.getBoolean(GLOBALHASH, false);
                    }
                }
            }

            if(mCalendar == null)
                mCalendar = Calendar.getInstance();

            FragmentTransaction transaction = manager.beginTransaction();
            transaction.setCustomAnimations(R.animator.slide_in_from_bottom,
                    R.animator.slide_out_to_bottom,
                    R.animator.slide_in_from_bottom,
                    R.animator.slide_out_to_bottom);

            mFrag = new GraticulePickerFragment();
            mFrag.setListener(this);

            // Toss in the current Graticule so the thing knows where to start.
            Bundle args = new Bundle();
            args.putParcelable(GraticulePickerFragment.GRATICULE, g);
            args.putBoolean(GraticulePickerFragment.GLOBALHASH, globalHash);
            mFrag.setArguments(args);

            transaction.replace(R.id.graticulepicker, mFrag, "GraticulePicker");
            transaction.addToBackStack(CentralMap.GRATICULE_PICKER_STACK);
            transaction.commit();
        }

        mInitComplete = true;
    }

    @Override
    public void cleanUp(@Nullable Bundle bundle) {

    }

    @Override
    public void handleInfo(Info info, int flags) {
        if(mInitComplete) {
            // If we get an Info in, plant a flag where it needs to be.
            addDestinationPoint(info);

            // TODO: Also, zoom to the point if it's a Globalhash.
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        // Okay, so now we've got a Graticule.  Well, we will right here:
        Graticule g = new Graticule(latLng);

        // We can update the fragment with that.  We'll get updateGraticule back
        // so we can add the outline.
        mFrag.setNewGraticule(g);
    }

    @Override
    public void updateGraticule(@Nullable Graticule g) {
        // New graticule!
        outlineGraticule(g);

        // Fetch the stock, too.
        mCentralMap.requestStock(g, mCalendar, StockService.FLAG_USER_INITIATED | StockService.FLAG_SELECT_A_GRATICULE);
    }

    @Override
    public void findClosest() {

    }

    @Override
    public void graticulePickerClosing() {

    }

    private void outlineGraticule(Graticule g) {
        if(mPolygon != null)
            mPolygon.remove();

        if(g == null) return;

        // And with that Graticule, we can get a Polygon.
        PolygonOptions opts = g.getPolygon()
                .strokeColor(mCentralMap.getResources().getColor(R.color.graticule_stroke))
                .strokeWidth(2)
                .fillColor(mCentralMap.getResources().getColor(R.color.graticule_fill));

        if(mMap != null) {
            mPolygon = mMap.addPolygon(opts);

            // Also, move the map.  Zoom in as need be, cover an area of
            // roughly... oh... two graticules in any direction.
            LatLngBounds.Builder builder = LatLngBounds.builder();
            LatLng basePoint = g.getCenterLatLng();
            LatLng point = new LatLng(basePoint.latitude - CLOSENESS_Y_DOWN, basePoint.longitude - CLOSENESS_X);
            builder.include(point);

            point = new LatLng(basePoint.latitude - CLOSENESS_Y_DOWN, basePoint.longitude + CLOSENESS_X);
            builder.include(point);

            point = new LatLng(basePoint.latitude + CLOSENESS_Y_UP, basePoint.longitude + CLOSENESS_X);
            builder.include(point);

            point = new LatLng(basePoint.latitude + CLOSENESS_Y_UP, basePoint.longitude - CLOSENESS_X);
            builder.include(point);

            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 0));
        }
    }
}
