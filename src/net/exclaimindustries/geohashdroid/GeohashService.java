/**
 * GeohashService.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * The GeohashService is a background Service that keeps watching GPS for
 * location updates and keeps track of how far away a given final destination
 * is from the user.  In a way, it replaces the same functionality in MainMap
 * and DetailedInfoScreen, with the advantage that it can keep running even when
 * the app isn't up, and can later be extended to allow for a home screen widget
 * and tracklogs.
 * 
 * @author Nicholas Killewald
 */
public class GeohashService extends Service implements LocationListener {
    // The last Location we've seen (can be null)
    private Location mLastLocation;
    // The Info related to the current tracking job (this will be null if we're
    // not tracking)
    private Info mInfo;
    // The LocationManager that'll do our location managing
    private LocationManager mLocationManager;
    // The NotificationManager that'll do our notification managing
    private NotificationManager mNotificationManager;
    // The current Notification (for updating purposes)
    private Notification mNotification;
    // The providers known to be on right now (if all our providers are
    // disabled, then we don't have a location)
    private HashMap<String, Boolean> mEnabledProviders;
    // Whether or not we're actively tracking right now
    private boolean mIsTracking = false;
    // This is set to true if we've recieved a GPS fix so we know to ignore any
    // cell tower fixes insofar as handling is concerned. This is reset to
    // false if GPS is disabled for whatever reason, and starts out false so we
    // can go to the towers until we get our first fix. This is to solve what
    // I like to call the "Glasgow Problem", where for some reason when I tried
    // this in Lexington, KY, I kept getting network fixes somewhere in the
    // city of Glasgow, KY, some hundred or so miles away. I'm certain there's
    // already a name for this sort of problem, but I like naming a problem
    // after a city in Kentucky, mainly because I like to think most of my
    // problems are related to living in the state of Kentucky.
    private boolean mHaveGPSFix = false;
    
    private boolean mForeground = false;
    
    private static final int NOTIFICATION_ID = 1;
    
    private static final String DEBUG_TAG = "GeohashService";
    
    // Two minutes (in milliseconds). If the last known check is older than
    // that, we ignore it.
    private static final int LOCATION_VALID_TIME = 120000;
    
    /** The decimal format for distances. */
    private static final DecimalFormat mDistFormat = new DecimalFormat("###.##");
    
    final RemoteCallbackList<GeohashServiceCallback> mCallbacks = new RemoteCallbackList<GeohashServiceCallback>();
    
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                String key) {
            if(key.equals(GHDConstants.PREF_COORD_UNITS) || key.equals(GHDConstants.PREF_DIST_UNITS)) {
                updateNotification();
            } else if(key.equals(GHDConstants.PREF_POWER_SAVER)) {
                // If the power saver option got flipped, we're clearly in
                // background mode already (the preferences screen doesn't
                // listen for updates).  We want to update the state of the
                // tracker.  Forcibly, if need be.
                if(!mForeground) {
                    mForeground = !mForeground;
                    setMode(!mForeground);
                }
            }
        }
    };
    
    /* (non-Javadoc)
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    public IBinder onBind(Intent arg0) {
        Log.d(DEBUG_TAG, "Service is being bound to something...");
        return mBinder;
    }
    
    @Override
    public void onCreate() {
        Log.i(DEBUG_TAG, "GeohashService now being created...");
        
        // We've got stuff, it needs setting up.
        mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        
        // We also need to listen for preference changes.
        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        // Start tracking immediately!  The Intent better have the Info bundle
        // we need, or we have a right to crash.
        startTracking((Info)(intent.getParcelableExtra(GeohashDroid.INFO)));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        
        mCallbacks.kill();
        stopTrackingService();
    }

    private final GeohashServiceInterface.Stub mBinder = new GeohashServiceInterface.Stub() {

        @Override
        public float getLastAccuracyInMeters() throws RemoteException {
            if(isTracking() && hasLocation())
                return mLastLocation.getAccuracy();
            else
                return Float.MAX_VALUE;
        }

        @Override
        public float getLastDistanceInMeters() throws RemoteException {
            if(isTracking() && hasLocation())
                return mLastLocation.distanceTo(mInfo.getFinalLocation());
            else
                return Float.MAX_VALUE;
        }

        @Override
        public Location getLastLocation() throws RemoteException {
            // First, make sure the last fix is new enough.  If it's too old,
            // reset it to null.
            if(mLastLocation != null && System.currentTimeMillis() - mLastLocation.getTime() < LOCATION_VALID_TIME)
                return mLastLocation;
            else {
                mLastLocation = null;
                return null;
            }
        }

        @Override
        public boolean hasLocation() throws RemoteException {
            // TODO: Might need to rethink this.
            return (mLastLocation != null);
        }

        @Override
        public boolean isTracking() throws RemoteException {
            if(mInfo == null)
            {
                // If mInfo is null at this check, we're not tracking.
                mIsTracking = false;
            }
            
            return mIsTracking;
        }

        @Override
        public void changeInfo(Info info) throws RemoteException {
            // New info!  Set ourselves up again and send out new data.
            startTracking(info);
        }

        @Override
        public void stopTracking() throws RemoteException {
            stopTrackingService();
        }

        @Override
        public void registerCallback(GeohashServiceCallback callback)
                throws RemoteException {
            // In you go!
            Log.d(DEBUG_TAG, "New callback: " + callback);
            if(callback != null) {
                mCallbacks.register(callback);
                // A new callback automatically means we're in foreground mode.
                setMode(true);
            }
        }

        @Override
        public void unregisterCallback(GeohashServiceCallback callback)
                throws RemoteException {
            // Out you go!
            Log.d(DEBUG_TAG, "Unregistering callback: " + callback);
            if(callback != null) mCallbacks.unregister(callback);
        }

        @Override
        public Info getInfo() throws RemoteException {
            return mInfo;
        }
    };
    
    private boolean areAnyProvidersStillAlive()
    {
        // Hey, it's this again!
        if(mEnabledProviders.isEmpty()) return false;
        
        for (String s : mEnabledProviders.keySet()) {
            if (mEnabledProviders.get(s)) {
                return true;
            }
        }
        
        return false;
    }

    @Override
    public void onLocationChanged(Location location) {
        // New location!
        mLastLocation = location;
        
        if(location != null) {
            // First, set the fix flag if we need to.
            if (location.getProvider().equals(LocationManager.GPS_PROVIDER))
                mHaveGPSFix = true;
    
            
            // If we have an update, AND we've been getting GPS fixes, BUT this
            // update didn't come from GPS, ignore it.
            if(mHaveGPSFix && !location.getProvider().equals(LocationManager.GPS_PROVIDER))
                return;
        }
        
        // Otherwise, it's a valid update, so send it off.
        updateNotification();
        notifyLocation();
    }

    @Override
    public void onProviderDisabled(String provider) {
        // First off, see if this was GPS going down.  If it is, mark that we
        // DON'T have GPS any more, and that cell tower updates are okay.
        if (provider.equals(LocationManager.GPS_PROVIDER))
        {
            Log.d(DEBUG_TAG, "GPS provider just died, turning off mHaveGPSFix");
            mHaveGPSFix = false;
        }
        
        boolean wereAnyProvidersStillAlive = areAnyProvidersStillAlive();
        
        mEnabledProviders.put(provider, false);
        if(wereAnyProvidersStillAlive && !areAnyProvidersStillAlive()) {
            // If that was the last of the providers, set the location to null
            // and notify everyone that we're no longer providing useful data
            // until the providers come back up.
            Log.d(DEBUG_TAG, "Last provider just died, notifying...");
            mLastLocation = null;
            updateNotification();
            notifyLostFix();
        }
        
    }

    @Override
    public void onProviderEnabled(String provider) {
        mEnabledProviders.put(provider, true);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (status == LocationProvider.OUT_OF_SERVICE || !mLocationManager.isProviderEnabled(provider)) {
            // OUT_OF_SERVICE implies the provider is down for the count.
            // Anything else means the provider is available, but maybe not
            // enabled.
            onProviderDisabled(provider);
        } else {
            onProviderEnabled(provider);
        }
    }
    
    private void updateNotificationDestination() {
        // This might get called if the coordinate format changes.
        if(mNotification != null) {
            mNotification.contentView.setTextViewText(R.id.Destination, getText(R.string.infobox_final)
                    + " " + UnitConverter.makeLatitudeCoordinateString(this, mInfo.getLatitude(), false, UnitConverter.OUTPUT_SHORT)
                    + " " + UnitConverter.makeLongitudeCoordinateString(this, mInfo.getLongitude(), false, UnitConverter.OUTPUT_SHORT));
        }
    }
    
    private void updateNotification() {
        // This redraws the whole notification.  Including the destination.
        
        // There's a really minor chance mInfo can be null here.  If it is, we
        // SHOULD be shutting down anyway.  Also, if the notification's null,
        // we should also ignore it.
        if(mInfo == null || mNotification == null) return;

        updateNotificationDestination();
        
        // The destination output looks like an infobox.
        mNotification.contentView.setTextViewText(R.id.YourLocation, getText(R.string.infobox_you)
            + " "
            + (mLastLocation != null
                    ? (UnitConverter.makeLatitudeCoordinateString(this, mLastLocation.getLatitude(), false, UnitConverter.OUTPUT_SHORT)
                            + " "
                            + UnitConverter.makeLongitudeCoordinateString(this, mLastLocation.getLongitude(), false, UnitConverter.OUTPUT_SHORT))
                    : this.getString(R.string.standby_title)));
        
        // Update the distance AND accuracy (if need be)...
        mNotification.contentView.setTextViewText(R.id.Distance, this.getString(R.string.details_dist)
            + " "
            + (mLastLocation != null
                    ? (UnitConverter.makeDistanceString(this, mDistFormat, mInfo.getDistanceInMeters(mLastLocation)))
                    : this.getString(R.string.standby_title)));
        
        if (mLastLocation == null) {
            mNotification.contentView.setTextViewText(R.id.Accuracy, "");
        } else {
            float accuracy = mLastLocation.getAccuracy();
            if (accuracy >= GHDConstants.REALLY_LOW_ACCURACY_THRESHOLD) {
                mNotification.contentView.setTextViewText(R.id.Accuracy, getString(R.string.infobox_accuracy_really_low));
            } else if (accuracy >= GHDConstants.LOW_ACCURACY_THRESHOLD) {
                mNotification.contentView.setTextViewText(R.id.Accuracy, getString(R.string.infobox_accuracy_low));
            } else {
                mNotification.contentView.setTextViewText(R.id.Accuracy, "");
            }
        }
        
        // Now, fire!
        mNotificationManager.notify(NOTIFICATION_ID, mNotification);
    }

    private boolean startTracking(Info info) {
        // This starts tracking with the given data.  It also STOPS tracking
        // whatever we were tracking before (if anything) and sends out and/or
        // updates the notification.
        if(mIsTracking) {
            // If we're already going, just switch the Info and keep going.
            mInfo = info;
            updateNotification();
        } else {
            // Otherwise, start it anew.
            List<String> providers = mLocationManager.getProviders(false);
            if(providers.isEmpty()) {
                // FAIL!  No providers are available!  In that case, just return
                // and don't start anything.  isTracking will let callers know
                // what's going on.
                mIsTracking = false;
                return false;
            }
            
            mInfo = info;
            
            mEnabledProviders = new HashMap<String, Boolean>();
            
            // Stuff all the providers into the HashMap, along with their current,
            // respective statuses.
            for(String s : providers)
                mEnabledProviders.put(s, mLocationManager.isProviderEnabled(s));
            
            // Then, register for responses and get ready for fun!
            setMode(true);
            
            // Just for kicks, get the last known location.  This is allowed to
            // be null (it's treated as "don't have a fix yet").
            Location tempLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            
            // Check if this is really really old data or not.  Anything past...
            // say... two minutes is too old to use and thus ignored.
            if(tempLocation != null && System.currentTimeMillis() - tempLocation.getTime() < LOCATION_VALID_TIME)
                mLastLocation = tempLocation;       
            
            // Create and fire off our notification.
            mNotification = new Notification(R.drawable.notification_service, null, System.currentTimeMillis());
            mNotification.flags = Notification.FLAG_ONGOING_EVENT;
            
            RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification_service_layout);
            views.setImageViewResource(R.id.Icon, R.drawable.notification_service);
            mNotification.contentView = views;
            
            // We want to start the MainMap activity to put the user directly in the
            // middle of the action.
            Intent go = new Intent(this, MainMap.class);
            go.putExtra(GeohashDroid.INFO, mInfo);
            go.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, go, PendingIntent.FLAG_CANCEL_CURRENT);
            
            mNotification.contentIntent = contentIntent;
            updateNotification();
            
            // There!  Let's go!
            mIsTracking = true;
        }
        
        notifyTrackingStarted();
        return true;
    }
    
    private void notifyTrackingStarted() {
        // Let all the registered callbacks know we've started tracking.
        final int N = mCallbacks.beginBroadcast();
        
        boolean isAnyoneOutThere = false;
        for (int i=0; i<N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).trackingStarted(mInfo);
                
                // If we survived to this point, the remote is alive.
                isAnyoneOutThere = true;
            } catch (RemoteException e) {

            }
        }
        mCallbacks.finishBroadcast();
        
        // Now, set foreground/background appropriately.  If there were any
        // listeners, we're in foreground mode.  If not, only the notification
        // bar is listening.
        setMode(isAnyoneOutThere);
    }
    
    private void notifyTrackingStopped() {
        // Hey!  We stopped!
        final int N = mCallbacks.beginBroadcast();
        
        boolean isAnyoneOutThere = false;
        for (int i=0; i<N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).trackingStopped();
                
                // If we survived to this point, the remote is alive.
                isAnyoneOutThere = true;
            } catch (RemoteException e) {

            }
        }
        mCallbacks.finishBroadcast();
        
        // We shouldn't need to do this, since tracking stopped sets mIsTracking
        // to be false, which makes setMode do nothing.  But still, best to make
        // sure and all.
        setMode(isAnyoneOutThere);
    }
    
    private void notifyLocation() {
        // New location update!
        Log.d(DEBUG_TAG, "Location update!");
        final int N = mCallbacks.beginBroadcast();
        
        boolean isAnyoneOutThere = false;
        for (int i=0; i<N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).locationUpdate(mLastLocation);
                
                // If we survived to this point, the remote is alive.
                isAnyoneOutThere = true;
            } catch (RemoteException e) {

            }
        }
        mCallbacks.finishBroadcast();
        
        setMode(isAnyoneOutThere);
    }
    
    private void notifyLostFix() {
        // Oh no!  We've lost all our providers!
        final int N = mCallbacks.beginBroadcast();
        for (int i=0; i<N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).lostFix();
            } catch (RemoteException e) {

            }
        }
        mCallbacks.finishBroadcast();
    }
    
    private void stopTrackingService() {
        // Stop doing whatever it is we're doing.
        mIsTracking = false;
        mLocationManager.removeUpdates(GeohashService.this);
        mNotificationManager.cancel(NOTIFICATION_ID);
        notifyTrackingStopped();
        mInfo = null;
    }
    
    private void setMode(boolean foreground) {
        // This sets whether we're in foreground or background mode.  If we're
        // not changing anything, though, don't do anything.
        if(foreground == mForeground || !mIsTracking) return;
        
        // Set the current mode...
        mForeground = foreground;
        
        // ...and switch!  If we're supposed to.  Since I still can't get this
        // working right on the Droid (which seems to be a hardware-specific
        // issue), I'll have to have a preference for now.
        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        if(mForeground || !prefs.getBoolean(GHDConstants.PREF_POWER_SAVER, false)) {
            // Foreground mode means we go full tilt.
            Log.i(DEBUG_TAG, "Switching to foregroud mode...");
            for(String s : mEnabledProviders.keySet())
                mLocationManager.requestLocationUpdates(s, 0, 0, GeohashService.this);
        } else {
            // Background mode means we slow down.  Like, say, 30 seconds.
            Log.i(DEBUG_TAG, "Switching to background mode...");
            for(String s : mEnabledProviders.keySet())
                mLocationManager.requestLocationUpdates(s, 30000, 0, GeohashService.this);
        }
    }
}
