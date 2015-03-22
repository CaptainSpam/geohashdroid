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
import java.util.Calendar;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.MediaStore;
import android.util.Log;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.wiki.WikiUtils;
import net.exclaimindustries.tools.AndroidUtil;
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

    /**
     * This is just a convenient holder for the various info related to an
     * image.
     */
    private class ImageInfo {
        public Uri uri;
        public String filename;
        public Location location;
        public long timestamp;
    }

    private static final String DEBUG_TAG = "WikiService";

    private NotificationManager mNotificationManager;
    private Notification.Builder mNotificationBuilder;
    private WakeLock mWakeLock;
    
    /**
     * The {@link Info} object for the current expedition.
     */
    public static final String EXTRA_INFO = "net.exclaimindustries.geohashdroid.EXTRA_INFO";

    /**
     * The timestamp when the original message was made (NOT when the message
     * ultimately gets posted).  Should be a {@link Calendar}.
     */
    public static final String EXTRA_TIMESTAMP = "net.exclaimindustries.geohashdroid.EXTRA_TIMESTAMP";

    /**
     * The message to add to the expedition page or image caption.  Should be a
     * String.
     */
    public static final String EXTRA_MESSAGE = "net.exclaimindustries.geohashdroid.EXTRA_MESSAGE";

    /**
     * Location of an image on the filesystem.  Should be a {@link Uri} to
     * something that Android can find with a ContentResolver, preferably the
     * MediaStore.  It'll be looking for DATA, LATITUDE, LONGITUDE, and
     * DATE_TAKEN from MediaStore.Images.ImageColumns.  Can be ignored if
     * there's no image to upload.
     *
     * TODO: Maybe some more flexible way of fetching an image?  Dunno.
     */
    public static final String EXTRA_IMAGE = "net.exclaimindustries.geohashdroid.EXTRA_IMAGE";

    /** 
     * The user's current geographic coordinates.  Should be a {@link Location}.
     * If not given, will assume the user's location is/was unknown.
     */
    public static final String EXTRA_LOCATION = "net.exclaimindustries.geohashdroid.EXTRA_LOCATION";

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
            .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.geohashing_logo));
        
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mNotificationBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);
    }
    
    @Override
    protected ReturnCode handleIntent(Intent i) {
        // First and foremost, if there's no network connection, just give up
        // now.
        if(!AndroidUtil.isConnected(this)) {
            showWaitingForConnectionNotification();
            return ReturnCode.PAUSE;
        }

        // Hey, there, Intent.  Got some extras for me?
        Info info = (Info)i.getSerializableExtra(EXTRA_INFO);
        Location loc = (Location)i.getSerializableExtra(EXTRA_LOCATION);
        String message = i.getStringExtra(EXTRA_MESSAGE);
        Calendar timestamp = (Calendar)i.getSerializableExtra(EXTRA_TIMESTAMP);
        Uri imageLocation = (Uri)i.getParcelableExtra(EXTRA_IMAGE);

        // If you're missing something vital, bail out.
        if(info == null || message == null || timestamp == null) {
            Log.e(DEBUG_TAG, "Intent was missing some vital data (either Info, message, or timestamp), giving up...");
            return ReturnCode.CONTINUE;
        }

        // Let's say there's an image specified.
        ImageInfo imageInfo;
        if(imageLocation != null) {
            // If so, we can try to look it up on the system.
            imageInfo = readImageInfo(imageLocation, loc);

            // But, if said info remains null, we've got a problem.  The user
            // wanted an image uploaded, but we can't do that, so we have to
            // abandon this intent.  However, I don't think that's a showstopper
            // in terms of continuing the queue.
            if(imageInfo == null) {
                Log.e(DEBUG_TAG, "The user was somehow allowed to choose an image that can't be accessed via MediaStore!");
                showImageErrorNotification();
                return ReturnCode.CONTINUE;
            }

            // Now, the location that we're going to send for the image SHOULD
            // match up with where the user thinks they are, so we'll read what
            // got stuffed into the ImageInfo.  Note that we just gave it the
            // user's current location in the event that MediaStore doesn't have
            // any idea, either, so we're not going to replace good data with a
            // null, if said good data exists.
            loc = imageInfo.location;
        }

        return ReturnCode.CONTINUE;
    }

    @Override
    protected void onQueueStart() {
        // WAKELOCK!  Front and center!
        mWakeLock.acquire();

        // If we're starting, that means we're not waiting anymore.  Makes
        // sense.
        hideWaitingForConnectionNotification();

        // Plus, throw up a NEW Notification.  This one should stick around
        // until we're done, one way or another.
        showActiveNotification();
    }

    @Override
    protected void onQueuePause(Intent i) {
        // Aaaaand wakelock stop.
        if(mWakeLock.isHeld()) mWakeLock.release();

        // Notification goes away, too.
        removeActiveNotification();
    }

    @Override
    protected void onQueueEmpty(boolean allProcessed) {
        // Done!  Wakelock go away now.
        if(mWakeLock.isHeld()) mWakeLock.release();

        // Notification go boom, too.
        removeActiveNotification();
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
        // TODO: If we're waiting on clearing up an error, don't resume.
        return false;
    }


    private void showActiveNotification() {
        mNotificationBuilder.setAutoCancel(false)
                .setOngoing(true)
                .setContentTitle(getString(R.string.wiki_notification_title))
                .setContentText("");

        mNotificationManager.notify(R.id.wiki_working_notification, mNotificationBuilder.build());
    }

    private void removeActiveNotification() {
        mNotificationManager.cancel(R.id.wiki_working_notification);
    }

    private void showImageErrorNotification() {
        // This shouldn't happen, but a spare notification to explain that an
        // image was canceled would be nice just in case it does.  It'll be an
        // auto-cancel, too, so the user can just remove it as need be, as we're
        // not going to touch it past this.  Also, the string says "one or more
        // images", so that'll cover it if we somehow get LOTS of broken image
        // URIs.
        mNotificationBuilder.setAutoCancel(true)
                .setOngoing(false)
                .setContentTitle(getString(R.string.wiki_notification_image_error_title))
                .setContentText(getString(R.string.wiki_notification_image_error_content));

        mNotificationManager.notify(R.id.wiki_image_error_notification, mNotificationBuilder.build());
    }

    private void showWaitingForConnectionNotification() {
        mNotificationBuilder.setAutoCancel(false)
                .setOngoing(true)
                .setContentTitle(getString(R.string.wiki_notification_waiting_for_connection_title))
                .setContentText("");

        mNotificationManager.notify(R.id.wiki_waiting_notification, mNotificationBuilder.build());
    }

    private void hideWaitingForConnectionNotification() {
        mNotificationManager.cancel(R.id.wiki_waiting_notification);
    }

    private ImageInfo readImageInfo(Uri uri, Location locationIfNoneSet) {
        // We're hoping this is something that MediaStore understands.  If not,
        // or if the image doesn't exist anyway, we're returning null, which is
        // interpreted by the intent handler to mean there's no image here, so
        // an error should be thrown.
        ImageInfo toReturn = null;

        if(uri != null) {
            Cursor cursor;
            cursor = getContentResolver().query(uri, new String[]
                            { MediaStore.Images.ImageColumns.DATA,
                                    MediaStore.Images.ImageColumns.LATITUDE,
                                    MediaStore.Images.ImageColumns.LONGITUDE,
                                    MediaStore.Images.ImageColumns.DATE_TAKEN },
                    null, null, null);

            if(cursor == null || cursor.getCount() < 1) {
                if(cursor != null) cursor.close();
                return null;
            }

            cursor.moveToFirst();

            toReturn = new ImageInfo();
            toReturn.uri = uri;
            toReturn.filename = cursor.getString(0);
            toReturn.timestamp = cursor.getLong(3);

            // These two could very well be null or empty.  Nothing wrong with
            // that.  But if they're good, make a Location out of them.
            String lat = cursor.getString(1);
            String lon = cursor.getString(2);

            Location toSet;
            try {
                double llat = Double.parseDouble(lat);
                double llon = Double.parseDouble(lon);
                toSet = new Location("");
                toSet.setLatitude(llat);
                toSet.setLongitude(llon);
            } catch (Exception ex) {
                // If we get an exception, we got it because of the number
                // parser.  Assume it's invalid and we're using the user's
                // current location, if that's even known (that might ALSO be
                // null, in which case we just don't have any clue where the
                // user is, which seems a bit counterintuitive to how
                // Geohashing is supposed to work).
                toSet = locationIfNoneSet;
            }

            // Now toss the location into the info.
            toReturn.location = toSet;

            cursor.close();
        }

        return toReturn;
    }

    private String getImageWikiName(Info info, ImageInfo imageInfo) {
        // We better have made at least two checks to make sure this is actually
        // defined (once in the Activity, once before we decided to upload the
        // image in the first place)...
        String username = getSharedPreferences(GHDConstants.PREFS_BASE, 0).getString(GHDConstants.PREF_WIKI_USER, "ERROR");
        return WikiUtils.getWikiPageName(info) + "_" + username + "_" + imageInfo.timestamp + ".jpg";
    }
}
