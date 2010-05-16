/**
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.text.DateFormat;
import java.text.DecimalFormat;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * The <code>DetailedInfoScreen</code> displays, in detail, just where the user
 * is right now. With big ol' text and lots of decimal places. This is ideal
 * for, say, taking pictures of the phone when at the point, at least until the
 * picture-taking-and-tagging function is coded up.
 * 
 * @author Nicholas Killewald
 */
public class DetailedInfoScreen extends Activity {
    private static final String INFO = "info";

    private Info mInfo;

    private PowerManager.WakeLock mWakeLock;

    private static final String DEBUG_TAG = "DetailedInfoScreen";

    /** The decimal format for distances. */
    protected static final DecimalFormat DIST_FORMAT = new DecimalFormat(
            "###.######");
    /** The decimal format for accuracy. */
    protected static final DecimalFormat ACCURACY_FORMAT = new DecimalFormat(
            "###.##");

    private static final int MENU_SETTINGS = 3;
    private static final int MENU_SEND_TO_MAPS = 6;
    
    private Location mLastLoc;
    
    private GeohashServiceInterface mService;

    
    /**
     * What we need is a callback.  Yeah.  Riiiiiiiiight here.
     */
    private GeohashServiceCallback.Stub mCallback = new GeohashServiceCallback.Stub() {

        @Override
        public void locationUpdate(Location location) throws RemoteException {
            // Location!
            mLastLoc = location;
            updateDisplay(location);
        }

        @Override
        public void lostFix() throws RemoteException {
            // Ack!  No location!
            mLastLoc = null;
            updateDisplay(null);
            
        }

        @Override
        public void trackingStarted(Info info) throws RemoteException {
            // New info?  Weird.  Well, update it and redraw.
            mInfo = info;
            updateDest();
            updateDisplay(mLastLoc);
        }

        @Override
        public void trackingStopped() throws RemoteException {
            // If tracking stopped explicitly, this Activity has no reason to
            // stay running.
            updateDisplay(null);
            finish();
        }
        
        public String toString() {
            return "DetailedInfoScreen service callback";
        }
    };
    
    /**
     * How about a little ServiceConnection, scarecrow?
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            mService = GeohashServiceInterface.Stub.asInterface(service);
            
            
            try {
                if(!mService.isTracking()) {
                    updateDisplay(null);
                    finish();
                    return;
                }
                
                // Get the most recent location and then start listening.
                mInfo = mService.getInfo();
                mLastLoc = mService.getLastLocation();
                updateDisplay(mLastLoc);
                mService.registerCallback(mCallback);
            } catch (RemoteException e) {

            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected.  All that means for us is that we
            // throw up standby.
            mService = null;
            updateDisplay(null);
        }
    };
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Lay 'er out!
        setContentView(R.layout.detail);

        // Get me a wakelock! Since we're dealing with something the
        // user might want to take pictures of outside the phone, this
        // one keeps the screen bright at all times, hence why we don't
        // just try to keep the same lock from MainMap.
        PowerManager pl = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pl.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE, DEBUG_TAG);

        // Get us some info!
        if (icicle != null && icicle.containsKey(INFO)) {
            mInfo = (Info)icicle.getParcelable(INFO);
        } else {
            mInfo = (Info)getIntent().getParcelableExtra(GeohashDroid.INFO);
        }

        // Lay out the initial info. The rest remains on standby for now.

        // Today's date, in long form.
        TextView tv = (TextView)findViewById(R.id.Date);
        tv.setText(DateFormat.getDateInstance(DateFormat.LONG).format(
                mInfo.getCalendar().getTime()));

        // The actual updates are requested at onResume.

        // And make sure we quit when told.
        Button button = (Button)findViewById(R.id.Okay);
        button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                finish();
            }

        });
    }

    @Override
    protected void onPause() {
        super.onPause();
    
        // Stop getting location updates.
        try {
            if(mService != null) mService.unregisterCallback(mCallback);
        } catch (RemoteException e) {
            
        }
        unbindService(mConnection);
    
        // SLEEEEEEEP!
        mWakeLock.release();
    }

    @Override
    protected void onResume() {
        super.onResume();
    
        // Don't sleeeeeeep!
        mWakeLock.acquire();
        
        updateDest();
        
        // Register with the service and let's get cracking!
        bindService(new Intent(DetailedInfoScreen.this, GeohashService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    
        // All we need to do is store the info object. Simple!
        outState.putParcelable(INFO, mInfo);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem item;

        // We've got Send To Maps, and we've got settings.  Simple.
        item = menu.add(Menu.NONE, MENU_SEND_TO_MAPS, 0,
                R.string.menu_item_send_to_maps);
        item.setIcon(android.R.drawable.ic_menu_myplaces);
        
        item = menu.add(Menu.NONE, MENU_SETTINGS, 1,
                R.string.menu_item_settings);
        item.setIcon(android.R.drawable.ic_menu_preferences);

        
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        super.onMenuItemSelected(featureId, item);

        switch (item.getItemId()) {
            case MENU_SETTINGS: {
                // Pop up our settings window!
                startActivity(new Intent(this, PreferenceEditScreen.class));
                return true;
            }
            case MENU_SEND_TO_MAPS: {
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
                
                return true;
            }
        }
        return false;
    }

    private void updateDest() {
        // This is called during onResume, either on first run or when we get
        // back from the preferences screen, possibly with a new coordinate
        // format.
        TextView tv;
        
        // The final destination.
        tv = (TextView)findViewById(R.id.DestLat);
        tv.setText(UnitConverter.makeLatitudeCoordinateString(this, mInfo.getFinalLocation().getLatitude(), false, UnitConverter.OUTPUT_DETAILED));

        tv = (TextView)findViewById(R.id.DestLon);
        tv.setText(UnitConverter.makeLongitudeCoordinateString(this, mInfo.getFinalLocation().getLongitude(), false, UnitConverter.OUTPUT_DETAILED));
    }

    private void updateDisplay(Location loc) {
        // This updates the current location and distance info. Unless loc is
        // null, in which case we use the standby label.
        if (loc == null) {
            String s = getResources().getString(R.string.standby_title);

            TextView tv = (TextView)findViewById(R.id.YouLat);
            tv.setText(s);
            tv = (TextView)findViewById(R.id.YouLon);
            tv.setText(s);
            tv = (TextView)findViewById(R.id.Distance);
            tv.setText(s);
            tv = (TextView)findViewById(R.id.Accuracy);
            tv.setText("");
        } else {
            TextView tv = (TextView)findViewById(R.id.YouLat);
            tv.setText(UnitConverter.makeLatitudeCoordinateString(this, loc.getLatitude(), false, UnitConverter.OUTPUT_DETAILED));
            tv = (TextView)findViewById(R.id.YouLon);
            tv.setText(UnitConverter.makeLongitudeCoordinateString(this, loc.getLongitude(), false, UnitConverter.OUTPUT_DETAILED));
            tv = (TextView)findViewById(R.id.Distance);
            tv.setText(UnitConverter.makeDistanceString(this, DIST_FORMAT,
                    mInfo.getDistanceInMeters(loc)));
            tv = (TextView)findViewById(R.id.Accuracy);
            tv.setText(getResources().getString(R.string.details_accuracy,
                    UnitConverter.makeDistanceString(this,
                            ACCURACY_FORMAT, loc.getAccuracy())));
        }

    }

}
