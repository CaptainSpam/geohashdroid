/**
 * MainMap.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid;

import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

import net.exclaimindustries.tools.DateTools;
import net.exclaimindustries.tools.LocationTools;
import net.exclaimindustries.tools.ZoomChangeOverlay;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Toast;

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
public class MainMap extends MapActivity implements ZoomChangeOverlay.ZoomChangeObserver {
    // Final destination
    private GeoPoint mDestination;
    // Our location, overlayed
    private FixedMyLocationOverlay mMyLocation;
    // The view
    private MapView mMapView;
    // The graticule of choice
    private Graticule mGraticule;
    // Whether we auto-zoom or not
    private boolean mAutoZoom = true;
    // Our bucket o' info (some data is repeated for convenience)
    private Info mInfo;

    private PowerManager.WakeLock mWakeLock;

    private static final String DEBUG_TAG = "MainMap";

    // Here come the string keys!
    private static final String CENTERLAT = "centerLatitude";
    private static final String CENTERLON = "centerLongitude";
    private static final String LATSPAN = "latitudeSpan";
    private static final String LONSPAN = "longitudeSpan";
    private static final String INFO = "info";
//    private static final String LOCATION = "location";
    private static final String ORIENTATION = "orientation";
    private static final String ZOOM = "zoomLevel";
    private static final String AUTOZOOM = "autoZoom";

    // Menu constants
    private static final int MENU_RECENTER = 1;
    private static final int MENU_INFO = 2;
    private static final int MENU_SETTINGS = 3;
    private static final int MENU_MAP_MODE = 4;
    private static final int MENU_POST = 5;
    private static final int MENU_SEND_TO_MAPS = 6;
    private static final int MENU_QUIT = 7;

    private static final int MENU_RECENTER_DESTINATION = 10;
    private static final int MENU_RECENTER_MYLOCATION = 11;
    private static final int MENU_RECENTER_NORMALVIEW = 12;

    private static final int MENU_POST_MESSAGE = 20;
    private static final int MENU_POST_PICTURE = 21;
    private static final int MENU_POST_WIKI = 22;
    
    static final int DIALOG_SEND_TO_MAPS = 1;
    private static final int DIALOG_SWITCH_GRATICULE = 2;
    
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

    private static final DecimalFormat mDistFormat = new DecimalFormat("###.###");
    
    // The last location we managed to get.
    private Location mLastLoc;
    
    private GeohashServiceInterface mService;
    
    // Whether or not we should do restarting actions when the service connects.
    private boolean mCenterOnFirstFix = false;
    
    // Whether or not we should rezoom when the service connects.
    private boolean mAutoZoomOnFirstFix = false;
    
    /**
     * Callback!  We need a callback!
     */
    private GeohashServiceCallback.Stub mCallback = new GeohashServiceCallback.Stub() {

        @Override
        public void locationUpdate(Location location) throws RemoteException {
            // Location!  Start updating everything that needs updating (the
            // infobox and the indicator, mostly)
            if ((isAutoZoomOn() && !isZoomProper(location)) || mAutoZoomOnFirstFix || mCenterOnFirstFix) {
                resetNormalView(LocationTools.makeGeoPointFromLocation(location));
                mAutoZoomOnFirstFix = false;
                mCenterOnFirstFix = false;
            }
            
            mLastLoc = location;
            populateInfoBox();
        }

        @Override
        public void lostFix() throws RemoteException {
            // No location!  Remove the updated stuff.
            mLastLoc = null;
            populateInfoBox();
        }

        @Override
        public void trackingStarted(Info info) throws RemoteException {
            // New info!  Probably means we changed it.
            mInfo = info;
            changeInfo(info);
        }

        @Override
        public void trackingStopped() throws RemoteException {
            // Since we're supposed to be the only ones stopping the activity,
            // this should already be over.
            if(!isFinishing()) finish();
        }
        
        public String toString() {
            return "MainMap service callback";
        }
    };
    
    /**
     * And now, this word from our ServiceConnection.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            mService = GeohashServiceInterface.Stub.asInterface(service);
            
            try {
                // If the service isn't tracking, we've got no reason to be
                // here.
                if(!mService.isTracking()) {
                    Log.w(DEBUG_TAG, "I got a connection to the service, but it's not tracking?");
                    finish();
                    return;
                }
                
                // Get the most recent location and whether we know the location
                // right away or not.
                mInfo = mService.getInfo();
                mLastLoc = mService.getLastLocation();
                
                // Now, since this is the first thing we do upon resuming, but
                // AFTER onCreate time, we need to do the restarting mechanisms
                // if need be.
                if(mCenterOnFirstFix) {
                    // If there's a fix right away, use it.  If there isn't,
                    // wait until the first response from the service.
                    if(mService.hasLocation()) {
                        mCenterOnFirstFix = false;
                        
                        resetNormalView(mLastLoc);
                    } else {
                        Toast rye = Toast.makeText(MainMap.this, R.string.find_location,
                                Toast.LENGTH_LONG);
                        rye.show();
                    }
                } else if (mAutoZoomOnFirstFix) {
                    if(mService.hasLocation()) {
                        mAutoZoomOnFirstFix = false;
                        
                        resetNormalView(mLastLoc);
                    } else {
                        Toast rye = Toast.makeText(MainMap.this, R.string.find_location_again,
                                Toast.LENGTH_SHORT);
                        rye.show();
                    }
                }
                
                mService.registerCallback(mCallback);
            } catch (RemoteException e) {
                
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected.  All that means for us is that we
            // throw up standby.
            mService = null;
            mLastLoc = null;
        }
    };    

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

        // The Intent stays constant, so we can always get the proper info from
        // it.  This is better than the store-with-icicle method I used earlier,
        // since that sometimes persisted when the Intent should've overridden.
        assignNewInfo((Info)getIntent().getParcelableExtra(GeohashDroid.INFO));

        // Layout!
        setContentView(R.layout.map);

        mMapView = (MapView)findViewById(R.id.Map);
        mMapView.setBuiltInZoomControls(true);
        
        MapController mcontrol = mMapView.getController();
        
        if (icicle != null && icicle.containsKey(AUTOZOOM)) {
            try {
                // If our Bundle has an Autozoom boolean, we're coming back from
                // elsewhere. We can rebuild from there.
                mAutoZoom = icicle.getBoolean(AUTOZOOM);
                int lat = icicle.getInt(CENTERLAT);
                int lon = icicle.getInt(CENTERLON);
                int zoom = icicle.getInt(ZOOM);
                          
                mcontrol.setZoom(zoom);
                mcontrol.setCenter(new GeoPoint(lat, lon));
                
                // And we need to make sure we let the user know if we're
                // adjusting the view.
                if(mAutoZoom)
                    mAutoZoomOnFirstFix = true;
            } catch (Exception e) {
                // We failed to re-center (somehow the bundle was defined with
                // the HashMaker but without centering data), so just center
                // on the destination, zoom level 12.
                mcontrol.setZoom(12);
                mcontrol.setCenter(mDestination);
            }
        } else {
            // Otherwise, we need to make sure we center on the first fix we get
            // from the service.
            mCenterOnFirstFix = true;
            mcontrol.setZoom(12);
            mcontrol.setCenter(mDestination);
        }

        // Let's dance!  First, get the list of overlays.
        List<Overlay> overlays = mMapView.getOverlays();
        
        // Add in the zoom watcher...
        overlays.add(new ZoomChangeOverlay(this));

        // Then, we figure out where we are and plot it.
        mMyLocation = new FixedMyLocationOverlay(this, mMapView);

        overlays.add(mMyLocation);

        // Enabling location and such are done in onStart. We pass through
        // that in all cases.

        // Now, add the final destination.
        addFinalDestination();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Shut up any MyLocationOverlay stuff.
        mMyLocation.disableMyLocation();
        mMyLocation.disableCompass();

        // Release the wakelock.
        mWakeLock.release();
        
        // And release our binding.
        try {
            mService.unregisterCallback(mCallback);
        } catch (Exception e) {
            // We don't do anything here.
        }
        unbindService(mConnection);
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
            mMyLocation.disableCompass();
            infobox.setVisibility(View.INVISIBLE);
            infoboxbig.setVisibility(View.INVISIBLE);
        }

        populateInfoBox();
        
        // Now, bring in the nearby points, if needed.  If not needed, remove
        // them.  Only do either if it changed since last time we saw them.
        boolean nearbyOn = prefs.getBoolean(GHDConstants.PREF_NEARBY_POINTS, false);
        if(!mInfo.isGlobalHash())
        {
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
        } else {
            removeNearbyPoints();
        }

        // MyLocationOverlay comes right back on.
        mMyLocation.enableMyLocation();

        // As does the wakelock.
        mWakeLock.acquire();
    
        // And here comes the service binding...
        bindService(new Intent(MainMap.this, GeohashService.class), mConnection, Context.BIND_AUTO_CREATE);
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

        // And now for the wiki features!
        sub = menu.addSubMenu(Menu.NONE, MENU_POST, 3,
                R.string.menu_item_post);
        sub.setIcon(android.R.drawable.ic_menu_upload);
        sub.add(Menu.NONE, MENU_POST_MESSAGE, 0,
                R.string.menu_item_post_message);
        sub.add(Menu.NONE, MENU_POST_PICTURE, 1,
                R.string.menu_item_post_picture);
        sub.add(Menu.NONE, MENU_POST_WIKI, 2,
                R.string.menu_item_post_wiki);
        
        // And the export option!
        item = menu.add(Menu.NONE, MENU_SEND_TO_MAPS, 4,
                R.string.menu_item_send_to_maps);
        item.setIcon(android.R.drawable.ic_menu_myplaces);

        // Settings comes almost last!
        item = menu.add(Menu.NONE, MENU_SETTINGS, 5,
                R.string.menu_item_settings);
        item.setIcon(android.R.drawable.ic_menu_preferences);
        
        // Quit comes really last!  Yes, I know this pushes the menu into more
        // than six options, meaning the icons for this and Settings won't show
        // up, but it doesn't HURT anything, and besides, they might come up
        // with some new mechanism in a later version of the API that DOES show
        // the icons, so, y'know, plan ahead and all that.
        item = menu.add(Menu.NONE, MENU_QUIT, 6,
                R.string.menu_item_quit);
        item.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        
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

    private void assignNewInfo(Info i) {
        // Quick!  Assign mInfo and the repeated values!
        // TODO: Do I REALLY want to keep the whole repeated value thing?  Is
        // the matter of convenience THAT important, or am I actually saving
        // calls by not doing mInfo.getFinalDestination over and over?
        mInfo = i;
        mDestination = i.getFinalDestination();
        mGraticule = i.getGraticule();
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        super.onCreateDialog(id);
        
        switch(id) {
            case DIALOG_SEND_TO_MAPS: {
                // The maps dialog is a simple question.  We only have one Info
                // bundle, so this doesn't need to be reprepared or state-saved
                // with any fanciness later.
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                build.setMessage(R.string.dialog_send_to_maps_text);
                build.setTitle(R.string.dialog_send_to_maps_title);
                build.setIcon(android.R.drawable.ic_dialog_map);
                build.setNegativeButton(R.string.dialog_send_to_maps_no,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                MainMap.this
                                        .dismissDialog(DIALOG_SEND_TO_MAPS);
                            }
                        });
                build.setPositiveButton(R.string.dialog_send_to_maps_yes,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                MainMap.this
                                        .dismissDialog(DIALOG_SEND_TO_MAPS);
                                sendToMaps();
                            }
                        });
                return build.create();
            }
        }
        
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
    }

    private void addFinalDestination() {
        List<Overlay> overlays = mMapView.getOverlays();
        
        // Add in the final destination.  We make the drawable here because
        // otherwise we'd need to pass the context in.
        Drawable finalMarker = getResources().getDrawable(
                R.drawable.final_destination);
        finalMarker.setBounds(0, 0, finalMarker.getIntrinsicWidth(),
                finalMarker.getIntrinsicHeight());
        overlays.add(new FinalDestinationOverlay(finalMarker, mInfo, this));
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
                Info inf = HashBuilder.getStoredInfo(this, mInfo.getCalendar(), offset);
                
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
                    // Set these to terminate both loops.
                    i = 2;
                    j = 2;
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
                if(mNextNearbyX == 0 && mNextNearbyY == 0) {
                    mNextNearbyY++;
                    continue;
                }
                
                // Make an offset graticule and get some info from it.
                Graticule offset = Graticule.createOffsetFrom(mGraticule, mNextNearbyY, mNextNearbyX);
                Info inf = HashBuilder.getStoredInfo(this, mInfo.getCalendar(), offset);
                
                if(inf == null) {
                    // If this comes up twice, this is impossible.  This means
                    // the cache is bad somehow, so we're bailing out now.
                    Log.e(DEBUG_TAG, "HEY!  HashBuilder returned null info when making the nearby overlays TWICE!  What?");
                    mNextNearbyX = 2;
                    mNextNearbyY = 2;
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
        if (mLastLoc == null) {
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
                if (mLastLoc == null)
                    return true;
                
                // The zoom overlay callback will take care of things past this.
                resetNormalZoom(mLastLoc);
                resetNormalCenter(mLastLoc);
                
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

                if (mLastLoc != null)
                    mcontrol.animateTo(LocationTools.makeGeoPointFromLocation(mLastLoc));
                return true;
            }
            case MENU_MAP_MODE: {
                // Also easy, just change the map mode. The string and icon
                // will get changed on menu display.
                mMapView.setSatellite(!mMapView.isSatellite());
                return true;
            }
            case MENU_POST_MESSAGE: {
                // Pop up a dialog box which allows to enter a message to be sent to the wiki.
                Intent i = new Intent(this, WikiMessageEditor.class);
                i.putExtra(GeohashDroid.INFO, mInfo);

                startActivity(i);
                return true;
            }
            case MENU_POST_PICTURE: {
                // Pop up a dialog box which allows to enter a message to be
                // sent to the wiki.
                Intent i = new Intent(this, WikiPictureEditor.class);
                i.putExtra(GeohashDroid.INFO, mInfo);
                
                startActivity(i);
                return true;
            }
            case MENU_POST_WIKI: {
                // Head out to the browser to get the wiki page for this
                // expedition.  I know there's a lot of different Android
                // devices coming out, but I'm at least hoping that they all
                // have the Browser component or some way to properly handle
                // the URL Intent.
                Intent i = new Intent();
                i.setAction(Intent.ACTION_VIEW);
                
                // Assemble a URL.  On the wiki, they come in this format:
                // [WIKI_BASE_URL]YYYY-MM-DD LAT LON
                // (yes, spaces and all... sort of odd, but hey, it works)
                
                // We attach "index.php" to the end of it just to be absolutely
                // safe.
                String page = WikiUtils.getWikiPageName(mInfo);
                
                i.setData(Uri
                        .parse(WikiUtils.getWikiBaseUrl() + "index.php?title=" + page));
                startActivity(i);
                
                return true;
            }
            case MENU_SEND_TO_MAPS: {
                showDialog(DIALOG_SEND_TO_MAPS);
                
                return true;
            }
            case MENU_QUIT: {
                // Just quit.  That is all.
                finish();
                return true;
            }

        }

        return false;
    }

    @Override
    protected boolean isRouteDisplayed() {
        // No.  No, it's actually not.
        return false;
    }

    private boolean isZoomProper() {
        return isZoomProper(mLastLoc);
    }
    
    private boolean isZoomProper(Location loc) {
        return isZoomProper(LocationTools.makeGeoPointFromLocation(loc));
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

    public void resetNormalView(Location loc) {
        resetNormalView(LocationTools.makeGeoPointFromLocation(loc));
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
    
    private void resetNormalZoom(Location loc) {
        resetNormalZoom(LocationTools.makeGeoPointFromLocation(loc));
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

    private void resetNormalCenter(Location loc) {
        resetNormalCenter(LocationTools.makeGeoPointFromLocation(loc));
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
            infoboxbig.update(mInfo, mLastLoc);
        else if (setting.equals("Small"))
            infobox.update(mInfo, mLastLoc);
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
        final Info toSend = i;
        // Let's make us a dialog!
        AlertDialog.Builder build = new AlertDialog.Builder(this);
        
        // The title is the new graticule's number.
        Graticule incoming = i.getGraticule();
        build.setTitle(incoming.getLatitude() + (incoming.isSouth() ? "S" : "N") + " "
                + incoming.getLongitude() + (incoming.isWest() ? "W" : "N"));
        build.setIcon(android.R.drawable.ic_dialog_map);
        
        // The text is a question.
        GeoPoint curGeo = LocationTools.makeGeoPointFromLocation(mLastLoc);

        if(curGeo == null) {
            // We don't know the location yet, so we go with the 
            // message without any indication of distance.
            build.setMessage(R.string.dialog_switch_graticule_unknown);
        } else {
            // We DO know the location, and thus we need the distance.
            String distance = UnitConverter.makeDistanceString(this, mDistFormat, i.getDistanceInMeters(curGeo));
            build.setMessage(getString(R.string.dialog_switch_graticule_text, distance));
        }
        
        // The okay button has to be able to send the Info bundle.
        build.setPositiveButton(R.string.dialog_switch_graticule_okay,
                new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,
                        int whichButton) {
                    dialog.dismiss();
                    try {
                        // Send it out to the service.  We'll get the callback.
                        mService.changeInfo(toSend);
                    } catch (RemoteException e) {
                        // TODO: Report an error; this shouldn't ever happen.
                        e.printStackTrace();
                    }
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
        // Info changing should only occur at the user's behest.  That is, if
        // the user explicitly changed the graticule via a tap.  What we need
        // to do, in order, is:
        //
        // 1. Remove all current overlays.
        // 2. Update our concept of mInfo.
        // 3. Recreate all overlays.  TODO: Do we want logic to determine which
        //    can be recycled?
        // 4. Force the infobox to update with the new info.
        // 5. Recenter and rezoom to the new point.
        //
        // With that said...

        // Step One:
        removeNearbyPoints();
        removeFinalDestination();

        // Step Two:
        assignNewInfo(i);

        // Step Three:
        addFinalDestination();

        // Nearby points should always be on as per this writing, as that's the
        // only way this will get triggered.  But, just to be safe and somewhat
        // future-proof...
        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        boolean nearbyOn = prefs.getBoolean(GHDConstants.PREF_NEARBY_POINTS, false);
        if(nearbyOn) addNearbyPoints();

        // Step Four:
        populateInfoBox();

        // Step Five:
        // We don't check if zooming is appropriate in this case; we assume
        // that we're zooming, like it or not, as if we just restarted and we
        // got our first fix (assuming we know where we are now).
        GeoPoint curPoint = mMyLocation.getMyLocation();
        
        if(curPoint != null) {
            resetNormalZoom(curPoint);
            resetNormalCenter(curPoint);
        } else {
            mMapView.getController().animateTo(i.getFinalDestination());
        }
        
        // Done and done!
    }
    
    private void sendToMaps() {
        // Send out the final destination's latitude and longitude to
        // the Maps app (or anything else listening for this intent).
        // Should be fairly simple.
        Intent i = new Intent();
        i.setAction(Intent.ACTION_VIEW);
        
        // Assemble the URI line.  We'll use a slightly higher-than-
        // default zoom level (we don't have the ability to say "fit
        // this and the user's current location on screen" when we're
        // going to the Maps app).
        String location = mInfo.getLatitude() + "," + mInfo.getLongitude();
        
        // We use the "0,0?q=" form, because that'll put a marker on the
        // map.  If we just used the normal form, it would just center
        // the map to that location and not do anything with it.
        i.setData(Uri.parse("geo:0,0?q=" + location));
        startActivity(i);        
    }

    @Override
    public void zoomChanged(MapView mapView, int prevZoom, int newZoom) {
        if (isZoomProper())
            setAutoZoom(true);
        else
            setAutoZoom(false);
    }

    @Override
    public void finish() {
        // If the activity is being finished somehow, we want to stop the
        // service.  This shouldn't happen if MainMap is being destroyed due
        // to it being stuffed in the background.  At least I don't think it
        // will.  To be honest, I'm not entirely sure, but all the tests I've
        // run seem to indicate that's the way it goes.
        stopGeohashService();
        super.finish();
    }
    
    private boolean stopGeohashService() {
        Intent stopper = new Intent(MainMap.this, GeohashService.class);
        stopper.putExtra(INFO, mInfo);
        return stopService(stopper);
    }
}
