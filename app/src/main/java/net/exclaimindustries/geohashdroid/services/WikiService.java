/**
 * WikiService.java
 * Copyright (C)2015 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.services;

import java.io.InputStream;
import java.io.OutputStream;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.tools.QueueService;

/**
 * <code>WikiService</code> is a background service that handles all wiki
 * communication.  Note that you still need to come up with the actual DATA
 * yourself.  This just does the talking to the server and queueing things up
 * for later if need be.
 *
 * @author Nicholas Killewald
 */
public class WikiService extends QueueService {

    private static final String DEBUG_TAG = "WikiService";

    private NotificationManager mNotificationManager;
    private Notification.Builder mNotificationBuilder;
    private WakeLock mWakeLock;
    
    /** The {@link Info} object for the current expedition. */
    public static final String EXTRA_INFO = "net.exclaimindustries.geohashdroid.EXTRA_INFO";
    /**
     * The timestamp when the original message was made (NOT when the message
     * ultimately gets posted).  Should be a {@link Calendar}.
     */
    public static final String EXTRA_TIMESTAMP = "net.exclaimindustries.geohashdroid.EXTRA_TIMESTAMP";
    /** The message to add to the expedition page or image caption. */
    public static final String EXTRA_MESSAGE = "net.exclaimindustries.geohashdroid.EXTRA_MESSAGE";
    /**
     * Location of an image on the filesystem.  Should be a String.  Can be
     * ignored if there's no image to upload.
     */
    public static final String EXTRA_IMAGE = "net.exclaimindustries.geohashdroid.EXTRA_IMAGE";
    /** 
     * The user's current geographic coordinates.  Should be a {@link Location}.
     * If not given, will assume the user's location is/was unknown.
     */
    public static final String EXTRA_LOCATION = "net.exclaimindustries.geohashdroid.EXTRA_LOCATION";
    
    /**
     * The progress made in a given Intent.  This is only used if an Intent has
     * to bail out midway due to errors so it can be picked up again later.
     * Don't set this outside of WikiService.  That's why it's private.
     */
    private static final String EXTRA_PROGRESS = "net.exclaimindustries.geohashdroid.EXTRA_PROGRESS";

    @SuppressLint("NewApi")
    @Override
    public void onCreate() {
        super.onCreate();
        
        // WakeLock awaaaaaay!
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WikiService");
        
        // Also, get the NotificationManager on standby with a builder.
        mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        
        mNotificationBuilder = new Notification.Builder(this)
            .setSmallIcon(R.drawable.geohashing_logo_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setOngoing(true);
        
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mNotificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);
    }
    
    @Override
    protected ReturnCode handleIntent(Intent i) {
        // TODO Auto-generated method stub
        return ReturnCode.CONTINUE;
    }

    @Override
    protected void onQueueStart() {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected void onQueuePause(Intent i) {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected void onQueueEmpty(boolean allProcessed) {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected void serializeToDisk(Intent i, OutputStream os) {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected Intent deserializeFromDisk(InputStream is) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected boolean resumeOnNewIntent() {
        // TODO Auto-generated method stub
        return false;
    }
}
