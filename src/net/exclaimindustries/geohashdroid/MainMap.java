/**
 * MainMap.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid;

import java.util.LinkedList;
import java.util.List;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ZoomControls;
import android.widget.LinearLayout.LayoutParams;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.MapController;
import com.google.android.maps.Overlay;

/**
 * This displays and manipulates the map as the user sees fit.
 * 
 * @author Nicholas Killewald
 */
public class MainMap extends MapActivity {
    // Final destination
    private GeoPoint mDestination;
    // Our location, overlayed
    private AutoZoomingLocationOverlay mMyLocation;
    // The view
    private MapView mMapView;
    // The graticule of choice
    private Graticule mGraticule;
    // Whether we auto-zoom or not
    private boolean mAutoZoom = true;
    // Our bucket o' info (data is repeated for convenience)
    private Info mInfo;

    private PowerManager.WakeLock mWakeLock;

    private static final String DEBUG_TAG = "MainMap";

    // Here come the string keys!
    private static final String CENTERLAT = "centerLatitude";
    private static final String CENTERLON = "centerLongitude";
    private static final String LATSPAN = "latitudeSpan";
    private static final String LONSPAN = "longitudeSpan";
    private static final String INFO = "info";
    private static final String ORIENTATION = "orientation";
    private static final String ZOOM = "zoomLevel";
    private static final String AUTOZOOM = "autoZoom";

    // Menu constants
    private static final int MENU_RECENTER = 1;
    private static final int MENU_INFO = 2;
    private static final int MENU_SETTINGS = 3;
    private static final int MENU_MAP_MODE = 4;

    private static final int MENU_RECENTER_DESTINATION = 10;
    private static final int MENU_RECENTER_MYLOCATION = 11;
    private static final int MENU_RECENTER_NORMALVIEW = 12;
    
    // Activity request constants
    private static final int REQUEST_STOCK = 1;

    // The menu we're holding on to to disable
    private Menu mMenu;
    
    // Whatever the last state of the Nearby Points preference was.  This is
    // mostly for efficiency; we only need to act if this changed.
    private boolean mNearbyOn;
    
    // Set by onActivityResult, this indicates that onResume should resume
    // planting nearby flags.  This should ALWAYS be set to false unless
    // onActivityResult says so, and then set back to false right afterward.
    private boolean mResumeFlags;
    
    // The next nearby flag that needs planting.  These go from -1 to 1, and
    // both being zero is right out.
    private int mNextNearbyX;
    private int mNextNearbyY;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        mNearbyOn = false;
        mResumeFlags = false;

        // First, reset the wakelock. The last one, if one was there in the
        // first place, was released on the last onStop. Thus, this is safe.
        PowerManager pl = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pl.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE, DEBUG_TAG);
        // The first call to onResume will acquire.

        boolean restarting = false;

        if (icicle != null && icicle.containsKey(INFO)) {
            // If our Bundle has an Info object, we're coming back from
            // elsewhere. We can rebuild from there.
            // TODO: Do I really need to do this? Or is the Intent constant?
            mInfo = (Info)icicle.getSerializable(INFO);
            mAutoZoom = icicle.getBoolean(AUTOZOOM);
            restarting = true;
        } else {
            mInfo = (Info)getIntent().getSerializableExtra(GeohashDroid.INFO);
        }

        // Now, gather up our data and do anything we need to that's common to
        // all cases.
        mDestination = mInfo.getFinalDestination();
        mGraticule = mInfo.getGraticule();

        setContentView(R.layout.map);

        mMapView = (MapView)findViewById(R.id.Map);

        // Let's dance! First, we want our zoom controls.
        LinearLayout zoomLayout = (LinearLayout)findViewById(R.id.ZoomLayout);
        ZoomControls zoomView = (ZoomControls)mMapView.getZoomControls();

        OnClickListener zoomOutListener = new OnClickListener() {

            @Override
            public void onClick(View v) {
                // When a click happens, we turn off autozoom. Aaaaand then do
                // the usual zooming stuff (as far as I know). If, however, we
                // wind up back around the right zoom level, turn autozoom back
                // on.
                MapController control = mMapView.getController();
                control.zoomOut();
                if (isZoomProper())
                    setAutoZoom(true);
                else
                    setAutoZoom(false);
            }

        };

        OnClickListener zoomInListener = new OnClickListener() {

            @Override
            public void onClick(View v) {
                // See above.
                MapController control = mMapView.getController();
                control.zoomIn();
                if (isZoomProper())
                    setAutoZoom(true);
                else
                    setAutoZoom(false);
            }

        };

        zoomView.setOnZoomInClickListener(zoomInListener);
        zoomView.setOnZoomOutClickListener(zoomOutListener);

        // I managed to get a couple different ways to put the zoom controls on
        // a map view by searching for tutorials. I read that the way I'm
        // doing it now doesn't work, which, strangely, appears to be a lie (it
        // works perfectly for me, and the suggested way doesn't). Huh.
        zoomLayout.addView(zoomView, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        mMapView.displayZoomControls(true);

        // Next, get the list of overlays.
        List<Overlay> overlays = mMapView.getOverlays();

        // Then, we figure out where we are and plot it.
        mMyLocation = new AutoZoomingLocationOverlay(this, mMapView);
        // Set up the location handler for later use...
        mMyLocation.setHandler(new AutoZoomingLocationOverlayHandler(Looper
                .myLooper()));
        overlays.add(mMyLocation);

        // Enabling location and such are done in onStart. We pass through
        // that in all cases.

        // Now, add the final destination.
        addFinalDestination();
        
        // If we are restarting, we need to check if we were autozooming when
        // we last left off. If we were, recenter the view on the next update
        // (that is, the FIRST update). If we weren't, use the centering and
        // zooming data from the bundle.
        if (restarting) {
            MapController mcontrol = mMapView.getController();

            try {
                // If any of this fails, we fall back to mDestination.
                int lat = icicle.getInt(CENTERLAT);
                int lon = icicle.getInt(CENTERLON);
                int zoom = icicle.getInt(ZOOM);

                mcontrol.setZoom(zoom);
                mcontrol.setCenter(new GeoPoint(lat, lon));
            } catch (Exception e) {
                // We failed to re-center (somehow the bundle was defined with
                // the HashMaker but without centering data), so just center
                // on the destination, zoom level 12.
                mcontrol.setZoom(12);
                mcontrol.setCenter(mDestination);
            }

            // If autozoom was on, indicate that we're readjusting back to
            // where we were.
            if (mAutoZoom
                    && !mMyLocation.runOnFirstFix(new InitialAutoZoomSetter())) {
                Toast rye = Toast.makeText(this, R.string.find_location_again,
                        Toast.LENGTH_SHORT);
                rye.show();
            }
        } else {
            // Otherwise, we center to the destination. Unless we already have
            // a fix (somehow), then we center to where that ought to be.
            if (!mMyLocation.runOnFirstFix(new InitialLocationAdjuster())) {
                MapController mcontrol = mMapView.getController();
                mcontrol.setZoom(12);
                mcontrol.setCenter(mDestination);

                Toast rye = Toast.makeText(this, R.string.find_location,
                        Toast.LENGTH_LONG);
                rye.show();
            } else {
                resetNormalView(mMyLocation.getMyLocation());
            }
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

        // Shut up any MyLocationOverlay stuff.
        mMyLocation.disableMyLocation();
        mMyLocation.disableCompass();

        // Release the wakelock.
        mWakeLock.release();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Determine what sort of infobox gets displayed. Make the other one
        // invisible, too. Or both.
        MainMapInfoBoxView infobox = (MainMapInfoBoxView)findViewById(R.id.InfoBox);
        MainMapInfoBoxView infoboxbig = (MainMapJumboInfoBoxView)findViewById(R.id.JumboInfoBox);

        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE,
                0);
        String setting = prefs.getString(GHDConstants.PREF_INFOBOX_SIZE, "Small");

        // And now, check it.
        if (setting.equals("Jumbo")) {
            // Jumbo disables the compass!
            mMyLocation.disableCompass();
            infobox.setVisibility(View.INVISIBLE);
            infoboxbig.setVisibility(View.VISIBLE);
        } else if (setting.equals("Small")) {
            mMyLocation.enableCompass();
            infobox.setVisibility(View.VISIBLE);
            infoboxbig.setVisibility(View.INVISIBLE);
        } else {
            mMyLocation.enableCompass();
            infobox.setVisibility(View.INVISIBLE);
            infoboxbig.setVisibility(View.INVISIBLE);
        }

        populateInfoBox();
        
        // Now, bring in the nearby points, if needed.  If not needed, remove
        // them.  Only do either if it changed since last time we saw them.
        boolean nearbyOn = prefs.getBoolean(GHDConstants.PREF_NEARBY_POINTS, false);
        
        if(mResumeFlags && nearbyOn)
        {
            // If we're coming back from stock grabbing, plant 'em.
            mResumeFlags = false;
            resumeNearbyPoints();
        } else if(nearbyOn != mNearbyOn) {
            // Otherwise, if the preference changed, alter the map.
            if(nearbyOn)
                addNearbyPoints();
            else
                removeNearbyPoints();
            mNearbyOn = nearbyOn;
        }

        // MyLocationOverlay comes right back on.
        mMyLocation.enableMyLocation();

        // As does the wakelock.
        mWakeLock.acquire();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // By the life cycle of an Activity, we've already paused, so we don't
        // need to worry about making sure mMyLocation is stopped. First, put
        // the center in so that we know where to look when we come back.
        GeoPoint center = mMapView.getMapCenter();
        outState.putInt(CENTERLAT, center.getLatitudeE6());
        outState.putInt(CENTERLON, center.getLongitudeE6());

        // Stash the old orientation. I'm not at all sure if this will be
        // useful. I had a plan for it a bit ago, but that didn't work out
        // right.
        outState.putInt(ORIENTATION,
                getResources().getConfiguration().orientation);

        // Stash the current spans and the associated zoom.
        outState.putInt(LATSPAN, mMapView.getLatitudeSpan());
        outState.putInt(LONSPAN, mMapView.getLongitudeSpan());
        outState.putInt(ZOOM, mMapView.getZoomLevel());
        outState.putBoolean(AUTOZOOM, mAutoZoom);

        // We can rebuild the destination marker from the stored Info object.
        outState.putSerializable(INFO, mInfo);

        // Autozoom is a curious problem. We can't seem to hold onto our
        // MyLocationOverlay object (and with good reason), so we'll need to
        // reconstruct it when this Activity comes back. In that time, the
        // user may have moved and invalidated our previous concept of
        // Autozoom, so we'll need to figure it out on first update when we
        // come back.

        // Other than that, though, everything can be reconstructed from the
        // HashMaker object.
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem item;

        // Build us up a menu.
        SubMenu sub = menu.addSubMenu(Menu.NONE, MENU_RECENTER, 0,
                R.string.menu_item_recenter);
        sub.setIcon(android.R.drawable.ic_menu_mylocation);
        sub.add(Menu.NONE, MENU_RECENTER_DESTINATION, 0,
                R.string.menu_item_recenter_destination);
        sub.add(Menu.NONE, MENU_RECENTER_MYLOCATION, 1,
                R.string.menu_item_recenter_mylocation);
        sub.add(Menu.NONE, MENU_RECENTER_NORMALVIEW, 2,
                R.string.menu_item_recenter_normalview);
        // This gets reset at prepare time anyway, but we'll just put it here
        // for the time being. It allows us to simply find it and change what
        // it says later.
        item = menu.add(Menu.NONE, MENU_MAP_MODE, 1,
                R.string.menu_item_mode_sat);
        item.setIcon(android.R.drawable.ic_menu_mapmode);
        item = menu.add(Menu.NONE, MENU_INFO, 2, R.string.menu_item_details);
        item.setIcon(android.R.drawable.ic_menu_info_details);
        item = menu.add(Menu.NONE, MENU_SETTINGS, 3,
                R.string.menu_item_settings);
        item.setIcon(android.R.drawable.ic_menu_preferences);

        mMenu = menu;

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // Reset our menu items as need be.
        resetRecenterMenuItem();
        resetMapModeMenuItem(menu);

        return true;
    }
    
    private void addFinalDestination() {
        List<Overlay> overlays = mMapView.getOverlays();
        
        // Add in the final destination.  We make the drawable here because
        // otherwise we'd need to pass the context in.
        Drawable finalMarker = getResources().getDrawable(
                R.drawable.final_destination);
        finalMarker.setBounds(0, 0, finalMarker.getIntrinsicWidth(),
                finalMarker.getIntrinsicHeight());
        overlays.add(new FinalDestinationOverlay(finalMarker, mInfo));
    }

    private void removeFinalDestination() {
        // This should be simple.  But, to be sure, dig through the entire list
        // first.
        List<Overlay> overlays = mMapView.getOverlays();

        List<Overlay> toRemove = new LinkedList<Overlay>();

        for(Overlay o : overlays) {
            if(o instanceof FinalDestinationOverlay)
                toRemove.add(o);
        }
        
        // YOINK!
        if(!toRemove.isEmpty()) {
            for(Overlay o : toRemove)
                overlays.remove(o);
        }
    }
    
    private void addNearbyPoints() {
        List<Overlay> overlays = mMapView.getOverlays();
        
        Drawable nearbyMarker = getResources().getDrawable(
                R.drawable.final_destination_disabled);
        nearbyMarker.setBounds(0, 0, nearbyMarker.getIntrinsicWidth(),
                nearbyMarker.getIntrinsicHeight());
        
        for(int i = -1; i <= 1; i++) {
            for(int j = -1; j <= 1; j++) {
                if(i == 0 && j == 0)
                    continue;
                
                // Make an offset graticule and get some info from it.
                Graticule offset = Graticule.createOffsetFrom(mGraticule, j, i);
                Info inf = HashBuilder.getStoredInfo(mInfo.getCalendar(), offset);
                
                if(inf == null) {
                    Log.d(DEBUG_TAG, "HashBuilder returned null info when making the nearby overlays, trying to get new data...");
                    // Set the nearby variables for next time.
                    mNextNearbyX = i;
                    mNextNearbyY = j;
                    // Fire off the new activity.
                    Intent in = new Intent(MainMap.this, StockGrabber.class);
                    in.putExtra(GeohashDroid.GRATICULE, offset);
                    in.putExtra(GeohashDroid.CALENDAR, mInfo.getCalendar());
                    startActivityForResult(in, REQUEST_STOCK);
                    break;
                }
                
                // Then, make us a disabled destination...
                overlays.add(new FinalDestinationDisabledOverlay(nearbyMarker, inf, this));
            }
        }
    }
    
    private void resumeNearbyPoints() {
        // This is called after addNearbyPoints fails once due to not having a
        // stock value ready.
        List<Overlay> overlays = mMapView.getOverlays();
        
        Drawable nearbyMarker = getResources().getDrawable(
                R.drawable.final_destination_disabled);
        nearbyMarker.setBounds(0, 0, nearbyMarker.getIntrinsicWidth(),
                nearbyMarker.getIntrinsicHeight());
        
        // Since we don't reinitialize the nearby variables, it makes more sense
        // to use while loops this time around.
        while(mNextNearbyX <= 1) {
            while(mNextNearbyY <= 1) {
                if(mNextNearbyX == 0 && mNextNearbyY == 0)
                    continue;
                
                // Make an offset graticule and get some info from it.
                Graticule offset = Graticule.createOffsetFrom(mGraticule, mNextNearbyY, mNextNearbyX);
                Info inf = HashBuilder.getStoredInfo(mInfo.getCalendar(), offset);
                
                if(inf == null) {
                    Log.e(DEBUG_TAG, "HEY!  HashBuilder returned null info when making the nearby overlays TWICE!  What?");
                    break;
                }
                
                // Then, make us a disabled destination...
                overlays.add(new FinalDestinationDisabledOverlay(nearbyMarker, inf, this));
                mNextNearbyY++;
            }
            mNextNearbyY = -1;
            mNextNearbyX++;
        }
    }
    
    private void removeNearbyPoints() {
        List<Overlay> overlays = mMapView.getOverlays();
        
        List<Overlay> toRemove = new LinkedList<Overlay>();
        // Iterate the list and remove any FinalDestinationDisabledOverlays.
        for(Overlay o : overlays) {
            if(o instanceof FinalDestinationDisabledOverlay)
                toRemove.add(o);
        }
        
        // Now, if we found anything, yoink 'em.
        if(!toRemove.isEmpty()) {
            for(Overlay o : toRemove)
                overlays.remove(o);
        }
    }

    private void resetRecenterMenuItem() {
        if (mMenu == null)
            return;

        // The normal view entry needs to be disabled if we don't have a fix.
        // This is purely for looks; if the view reset is called with an
        // invalid location, it'll just return.
        if (mMyLocation.getMyLocation() == null) {
            mMenu.findItem(MENU_RECENTER_NORMALVIEW).setEnabled(false);
            mMenu.findItem(MENU_RECENTER_MYLOCATION).setEnabled(false);
        } else {
            mMenu.findItem(MENU_RECENTER_NORMALVIEW).setEnabled(true);
            mMenu.findItem(MENU_RECENTER_MYLOCATION).setEnabled(true);
        }
    }

    private void resetMapModeMenuItem(Menu menu) {
        // We want it to say the opposite of whatever's currently in action.
        if (mMapView.isSatellite()) {
            menu.findItem(MENU_MAP_MODE).setTitle(
                    R.string.menu_item_mode_street);
        } else {
            menu.findItem(MENU_MAP_MODE).setTitle(R.string.menu_item_mode_sat);
        }
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        super.onMenuItemSelected(featureId, item);

        switch (item.getItemId()) {
            case MENU_RECENTER_NORMALVIEW: {
                // If this got selected but we don't have a location (rare in
                // real-world situations, but possible), just bail out. We
                // can't do anything with it.
                GeoPoint point = mMyLocation.getMyLocation();
                if (point == null)
                    return true;
                resetNormalZoom(point);
                resetNormalCenter(point);

                // Soooooo, if autozoom was off and changing this makes it on,
                // THEN we toast back up.
                boolean wasAutoZoomOn = isAutoZoomOn();
                mAutoZoom = true;
                if (!wasAutoZoomOn && isAutoZoomOn()) {
                    Toast wheat = Toast.makeText(MainMap.this,
                            R.string.autozoom_turned_on, Toast.LENGTH_SHORT);
                    wheat.show();
                }
                return true;
            }
            case MENU_INFO: {
                // Pop up our detail window!
                Intent i = new Intent(this, DetailedInfoScreen.class);
                i.putExtra(GeohashDroid.INFO, mInfo);
                startActivity(i);
                return true;
            }
            case MENU_SETTINGS: {
                // Pop up our settings window!
                startActivity(new Intent(this, PreferenceEditScreen.class));
                return true;
            }
            case MENU_RECENTER_DESTINATION: {
                // This one's easy. Just pop over to the final destination,
                // current zoom level.
                MapController mcontrol = mMapView.getController();
                mcontrol.animateTo(mDestination);
                return true;
            }

            case MENU_RECENTER_MYLOCATION: {
                // This one's also easy, with the caveat that if we don't have
                // a valid location yet, we bail out.
                MapController mcontrol = mMapView.getController();

                GeoPoint point = mMyLocation.getMyLocation();

                if (point != null)
                    mcontrol.animateTo(point);
                return true;
            }
            case MENU_MAP_MODE: {
                // Also easy, just change the map mode. The string and icon
                // will get changed on menu display.
                mMapView.setSatellite(!mMapView.isSatellite());
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }

    private boolean isZoomProper() {
        return isZoomProper(mMyLocation.getMyLocation());
    }

    private boolean isZoomProper(GeoPoint point) {
        // Check the zoom range, compare it to the span between where we are
        // and where the final destination is. And return as need be.

        // If we haven't had a fix yet, we're in range. Since we only have
        // one point and all.
        if (point == null)
            return true;

        // Figure out the span of the current view and see if the
        // distance left is in a zoomable range. We know that if we
        // zoom in by one level, we cut the span in half for each
        // dimension. So, if we're not already at max zoom and need to
        // zoom in, OR if we're not already at min zoom and need to
        // zoom out, readjust normal view.
        //
        // We would just pass this off to resetNormalView in and of
        // itself, but that also re-centers the view, something we
        // don't need to do unless the zoom changes.
        int curLatSpan = mMapView.getLatitudeSpan();
        int curLonSpan = mMapView.getLongitudeSpan();

        int zoomLevel = mMapView.getZoomLevel();

        // Distance between the points.
        int latSpan = Math.abs(mDestination.getLatitudeE6()
                - point.getLatitudeE6());
        int lonSpan = Math.abs(mDestination.getLongitudeE6()
                - point.getLongitudeE6());

        // The multipliers are to nudge the data a bit to make sure we're not
        // right against the edges of the screen on an auto-zoom.

        // If either of lat or lon are greater than the view, we're too close.
        if ((latSpan * 1.1 > curLatSpan || lonSpan * 1.1 > curLonSpan)
                && zoomLevel != 1) {
            return false;
        }

        // Otherwise, if BOTH are less than half the span we're looking at,
        // we're too far away.
        if ((latSpan < curLatSpan * 0.45 && lonSpan < curLonSpan * 0.45)
                && zoomLevel < mMapView.getMaxZoomLevel()) {
            return false;
        }

        // If all else fails, we're in range.
        return true;
    }

    private boolean isAutoZoomOn() {
        // Grab the preference and compare to that, too.
        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE,
                0);

        // The AutoZoom key is guaranteed to exist due to the main activity's
        // startup. We shouldn't need to worry about it, but we'll try/catch
        // the hell out of it anyway.
        boolean prefAutoZoom = true;
        try {
            prefAutoZoom = prefs.getBoolean(GHDConstants.PREF_AUTOZOOM, true);
        } catch (Exception e) {
            prefAutoZoom = true;
        }

        return mAutoZoom && prefAutoZoom;
    }

    private void setAutoZoom(boolean flag) {
        // Track what isAutoZoomOn was beforehand so we know if we want to
        // toast. If the preference overrides to false, don't display.
        boolean wasAutoZoomOn = isAutoZoomOn();

        // We don't use isAutoZoomOn here because we only want the message
        // displayed if there's a notable change from the user's perspective.
        if (!flag) {
            if (mAutoZoom) {
                mAutoZoom = false;
                if (isAutoZoomOn() != wasAutoZoomOn) {
                    Toast wheat = Toast.makeText(MainMap.this,
                            R.string.autozoom_turned_off, Toast.LENGTH_SHORT);
                    wheat.show();
                }
            }
        } else {
            if (!mAutoZoom) {
                mAutoZoom = true;
                if (isAutoZoomOn() != wasAutoZoomOn) {
                    Toast wheat = Toast.makeText(MainMap.this,
                            R.string.autozoom_turned_on, Toast.LENGTH_SHORT);
                    wheat.show();
                }
            }
        }
    }

    /**
     * Reset the map to normal view. That is, ensuring both the current location
     * and the destination are visible and centered.
     * 
     * <i>o/~ Normal view, normal view, normal view, <b>NORMAL VIEW!!!!!</b>
     * o/~</i>
     * 
     * @param curLocation
     *            where the user is right now (this is compared to mDestination)
     */
    public void resetNormalView(GeoPoint curLocation) {
        // ONLY act if auto-zoom is still on.
        if (!isAutoZoomOn())
            return;

        resetNormalCenter(curLocation);
        resetNormalZoom(curLocation);
    }

    private void resetNormalZoom(GeoPoint curLocation) {
        MapController mcontrol = mMapView.getController();

        // Determine the span from the destination to where we are now. We
        // want to add a slight bit of extra space so that the edges don't fall
        // right where the points are.
        int latSpan = (int)(Math.abs(mDestination.getLatitudeE6()
                - curLocation.getLatitudeE6()) * 1.1);
        int lonSpan = (int)(Math.abs(mDestination.getLongitudeE6()
                - curLocation.getLongitudeE6()) * 1.1);

        // And zoom us there.
        mcontrol.zoomToSpan(latSpan, lonSpan);
    }

    private void resetNormalCenter(GeoPoint curLocation) {
        MapController mcontrol = mMapView.getController();

        // First, figure out the midway point between where we are and
        // where we need to go.
        int latMid = (mDestination.getLatitudeE6() + curLocation
                .getLatitudeE6()) / 2;
        int lonMid = (mDestination.getLongitudeE6() + curLocation
                .getLongitudeE6()) / 2;

        // Then, set us to that point.
        mcontrol.animateTo(new GeoPoint(latMid, lonMid));
    }

    private void populateInfoBox() {
        // Populates the InfoBoxes with the needed information. Note that this
        // just gets skipped if the box isn't being displayed. We only send
        // the data to whatever's visible, if anything.
        MainMapInfoBoxView infobox = (MainMapInfoBoxView)findViewById(R.id.InfoBox);
        MainMapInfoBoxView infoboxbig = (MainMapJumboInfoBoxView)findViewById(R.id.JumboInfoBox);

        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE,
                0);
        String setting = prefs.getString(GHDConstants.PREF_INFOBOX_SIZE, "Small");

        if (setting.equals("Jumbo"))
            infoboxbig.update(mInfo, mMyLocation.getLastFix());
        else if (setting.equals("Small"))
            infobox.update(mInfo, mMyLocation.getLastFix());
    }

    /**
     * This class handles the initial "move the map to see both the final
     * destination and you" adjustment when the Activity is first created. If
     * need be, it will also do the same job at any other time, too.
     * 
     * This is a separate internal class because it's otherwise really really
     * ugly to define this entire thing in the space of an if statement.
     */
    protected class InitialLocationAdjuster implements Runnable {
        public void run() {
            resetNormalView(mMyLocation.getMyLocation());
        }
    }

    /**
     * This class comes into play if we're coming back from the Activity being
     * paused or whatnot (and not immediately restored due to a config change).
     * This just acts on autozoom. If we were autozooming, we want to zoom back
     * where we were.
     */
    protected class InitialAutoZoomSetter implements Runnable {
        public void run() {
            // If we last left off with autozoom on, zoom right on in. If we
            // didn't, keep it however it was.
            if (mAutoZoom) {
                resetNormalView(mMyLocation.getMyLocation());
            }
        }
    }

    private class AutoZoomingLocationOverlayHandler extends Handler {
        public AutoZoomingLocationOverlayHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            // Check over the message type.
            switch (message.what) {
                case AutoZoomingLocationOverlay.LOCATION_CHANGED:
                    // We know that it's a GeoPoint.
                    GeoPoint point = (GeoPoint)(message.obj);

                    // And go right up to isZoomProper!
                    if (isAutoZoomOn() && !isZoomProper(point))
                        resetNormalView(point);

                    populateInfoBox();

                    break;
                case AutoZoomingLocationOverlay.FIRST_FIX:
                case AutoZoomingLocationOverlay.LOST_FIX:
                    // On the first fix or a complete signal loss, we reset the
                    // Normal View menu item and populate the info box. It works
                    // for both.
                    resetRecenterMenuItem();
                    populateInfoBox();
                    break;
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_STOCK: {
                // The stock grabber would ONLY be called if the user is on the
                // 30W or 179E/W lines.  Since we're this far, the initially
                // requested result must be okay, meaning that the stock must
                // also exist for whatever end of the 30W line we need to check.
                // RESULT_NOT_POSTED_YET should NEVER happen.  Regardless, we'll
                // just treat it as an abort and not redraw the nearby points.
                // Granted, this may result in repeated problems if the user
                // keeps on switching between landscape and portrait modes or if
                // the stock cache has been set to zero, but that's an edge case
                // I'm not going to worry about just yet.
                switch(resultCode) {
                    case RESULT_OK: {
                        // Stock data came back.  Thus, we can resume planting
                        // nearby meetup point markers.  We don't need the Info
                        // bundle returned, since we'll just go to HashBuilder
                        // anyway.
                        mResumeFlags = true;
                        Log.d(DEBUG_TAG, "Got new data, resuming flag-planting...");
                        break;
                    }
                    // In all other cases, we bail out and ignore the remaining
                    // points.
                    case StockGrabber.RESULT_NOT_POSTED_YET:
                    case StockGrabber.RESULT_SERVER_FAILURE:
                    case RESULT_CANCELED:
                        mResumeFlags = false;
                        break;
                }
                break;
            }

        }
    }
    
    /**
     * Displays the "Switch to X graticule?" prompt.  This should happen as the
     * result of the user tapping a disabled final destination point.
     *  
     * @param i new Info to use
     */
    void showSwitchGraticulePrompt(Info i) {
        // Let's make us a dialog!
        AlertDialog.Builder build = new AlertDialog.Builder(this);
        
        // The title is the new graticule's number.
        Graticule incoming = i.getGraticule();
        build.setTitle(incoming.getLatitude() + (incoming.isSouth() ? "S" : "N") + " "
                + incoming.getLongitude() + (incoming.isWest() ? "W" : "N"));
        build.setIcon(android.R.drawable.ic_dialog_map);
        
        // The text is a question.
        build.setMessage("EENEY OONEY WAH-NAH!");
        
        // The okay button has to be able to send the Info bundle.
        build.setPositiveButton(R.string.dialog_switch_graticule_okay,
                new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,
                        int whichButton) {
                	// TODO: Send a message back to change the Info bundle.
                    dialog.dismiss();
                }
            });
        
        // The cancel button is pretty base.
        build.setNegativeButton(R.string.dialog_switch_graticule_cancel,
                new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,
                        int whichButton) {
                    dialog.cancel();
                }
            });
        
        build.show();
    }
    
    private void changeInfo(Info i) {
        // TODO: Implement!
    }
}
