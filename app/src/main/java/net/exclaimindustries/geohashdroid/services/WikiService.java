/*
 * WikiService.java
 * Copyright (C)2015 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.services;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.activities.LoginPromptDialog;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.wiki.WikiException;
import net.exclaimindustries.geohashdroid.wiki.WikiImageUtils;
import net.exclaimindustries.geohashdroid.wiki.WikiUtils;
import net.exclaimindustries.tools.AndroidUtil;
import net.exclaimindustries.tools.DateTools;
import net.exclaimindustries.tools.PlainSQLiteQueueService;
import net.exclaimindustries.tools.QueueService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Constraints;
import androidx.work.ListenableWorker;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClients;

/**
 * <code>WikiService</code> is a background service that handles all wiki
 * communication.  Note that you still need to come up with the actual DATA
 * yourself.  This just does the talking to the server and queueing things up
 * for later if need be.
 *
 * @author Nicholas Killewald
 */
public class WikiService extends PlainSQLiteQueueService {
    /**
     * This is only here because {@link NotificationCompat.Action} doesn't exist
     * in API 16, which is what I'm targeting.  Darn!  It works astonishingly
     * similar to it, if by that you accept simply calling the API 16 version of
     * {@link NotificationCompat.Builder#addAction(int, CharSequence, PendingIntent)}
     * with the appropriate data to be "astonishingly similar", which I do.
     */
    private static class NotificationAction {
        public int icon;
        public PendingIntent actionIntent;
        public CharSequence title;

        public NotificationAction(int icon, PendingIntent actionIntent, CharSequence title) {
            this.icon = icon;
            this.actionIntent = actionIntent;
            this.title = title;
        }
    }

    /**
     * This Worker does little more than try to fire off a RESUME command once
     * the network returns.
     */
    public static class ConnectivityWorker extends Worker {
        public ConnectivityWorker(@NonNull Context context,
                           @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        @NonNull
        @Override
        public Result doWork() {
            // Just launch into the service.  The scheduler ONLY should've woken
            // us up if we've got an internet connection, but if not, the queue
            // will just pause anyway.
            Intent i = new Intent(getApplicationContext(), WikiService.class);
            i.putExtra(QueueService.COMMAND_EXTRA, QueueService.COMMAND_RESUME);
            getApplicationContext().startService(i);

            // Whatever the case, this was a smashing success.  Well done.
            return ListenableWorker.Result.success();
        }
    }

    private static final String DEBUG_TAG = "WikiService";

    private NotificationManagerCompat mNotificationManager;
    private AlarmManager mAlarmManager;
    private WakeLock mWakeLock;

    private UUID mLastWikiConnectivityRequestId;

    /** Matches the gallery section. */
    private static final Pattern RE_GALLERY = Pattern.compile("^(.*<gallery[^>]*>)(.*?)(</gallery>.*)$",Pattern.DOTALL);
    /** Matches the gallery section header. */
    private static final Pattern RE_GALLERY_SECTION = Pattern.compile("^(.*== Photos ==)(.*)$",Pattern.DOTALL);
    /** Matches the expedition section. */
    private static final Pattern RE_EXPEDITION  = Pattern.compile("^(.*)(==+ ?Expedition ?==+.*?)(==+ ?.*? ?==+.*?)$",Pattern.DOTALL);

    /** How long we wait (in millis) before retrying a throttled edit. */
    private static final long THROTTLE_DELAY = 60000;

    /** The wakelock timeout (10 minutes). */
    private static final long WAKELOCK_TIMEOUT = 10 * 60 * 1000;

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
     */
    public static final String EXTRA_IMAGE = "net.exclaimindustries.geohashdroid.EXTRA_IMAGE";

    /**
     * Actual literal {@link android.graphics.Bitmap} image data to be uploaded.
     * This is needed because WikiService is a different Context from what
     * selected the image in the first place, causing a security exception.
     */
    public static final String EXTRA_IMAGE_DATA = "net.exclaimindustries.geohashdroid.EXTRA_IMAGE_DATA";

    /** 
     * The user's current geographic coordinates.  Should be a {@link Location}.
     * If not given, will assume the user's location is/was unknown.  If posting
     * an image, any location metadata stored in that image will override this,
     * but if no such data exists there, this will be used instead.
     */
    public static final String EXTRA_LOCATION = "net.exclaimindustries.geohashdroid.EXTRA_LOCATION";

    /**
     * Whether or not the current location should be included with any upload.
     * That is, if this is false, the location won't be appended to messages and
     * infoboxes on images will claim the location is unknown.  Though the same
     * effect can be achieved in a message post by not passing in
     * {@link #EXTRA_LOCATION}, this also overrides any location metadata in
     * images.
     */
    public static final String EXTRA_INCLUDE_LOCATION = "net.exclaimindustries.geohashdroid.EXTRA_INCLUDE_LOCATION";

    /** The name of the queue. */
    public static final String QUEUE_NAME = "wikiservice";

    @Override
    public void onCreate() {
        super.onCreate();
        
        // WakeLock awaaaaaay!
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        assert pm != null;
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "geohashdroid:WikiService");
        
        // Also, get the NotificationManager on standby.
        mNotificationManager = NotificationManagerCompat.from(this);

        // How alarming.  We need the AlarmManager.
        mAlarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
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
        Info info;
        Location loc;
        String message;
        Calendar timestamp;
        Uri imageLocation;
        byte[] imageData;
        boolean includeLocation;

        try {
            info = i.getParcelableExtra(EXTRA_INFO);
            loc = i.getParcelableExtra(EXTRA_LOCATION);
            message = i.getStringExtra(EXTRA_MESSAGE);
            timestamp = (Calendar) i.getSerializableExtra(EXTRA_TIMESTAMP);
            imageLocation = i.getParcelableExtra(EXTRA_IMAGE);
            imageData = i.getByteArrayExtra(EXTRA_IMAGE_DATA);
            includeLocation = i.getBooleanExtra(EXTRA_INCLUDE_LOCATION, true);
        } catch(ClassCastException cce) {
            // If any of those threw a CCE, bail out.
            Log.e(DEBUG_TAG, "ClassCastException!  Check your casts!", cce);
            return ReturnCode.CONTINUE;
        }

        // Prep an HttpClient for later...
        try(CloseableHttpClient client = HttpClients.createDefault()) {
            // To Preferences!
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String username = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
            String password = prefs.getString(GHDConstants.PREF_WIKI_PASS, "");
            if(info == null || message == null || timestamp == null) {
                // If we're missing something vital, bail out.
                Log.e(DEBUG_TAG, "Intent was missing some vital data (either Info, message, or timestamp), giving up...");
                return ReturnCode.CONTINUE;
            }
            if(imageLocation != null && username.isEmpty()) {
                // Also, if there's an image specified, make sure there's also a
                // username.  The wiki does not allow anonymous image uploads.
                // This one, unlike the previous one, produces an interruption
                // so the user can enter in a username and password.
                showPausingErrorNotification(getString(R.string.wiki_conn_anon_pic_error),
                        resolveWikiExceptionActions(new WikiException(R.string.wiki_conn_anon_pic_error)));
                return ReturnCode.PAUSE;
            }
            // Location becomes null if we're not including it.  Nothing should
            // need to care.
            if(!includeLocation) loc = null;
            // If we got a username/password combo, try to log in.  This throws
            // a WikiException if the login fails.
            if(!username.isEmpty() && !password.isEmpty()) {
                WikiUtils.login(client, username, password);
            }

            // Prep a page.  We want a populated formfields for later.
            HashMap<String, String> formfields = new HashMap<>();
            String expedition = WikiUtils.getWikiPageName(info);

            // This will be null if the page didn't exist to begin with.
            String page = WikiUtils.getWikiPage(client, expedition, formfields);

            // And if it IS null (or empty), then we ought to make said page.
            if(page == null || page.trim().isEmpty()) {
                // Aha!
                WikiUtils.putWikiPage(client, expedition,
                        WikiUtils.getWikiExpeditionTemplate(info, this),
                        formfields);

                // And once it's there, we pull it back, as we'll be futzing
                // about with it some more.
                page = WikiUtils.getWikiPage(client, expedition, formfields);
            }

            // I know this is making a monstrous, ugly method that's just a big
            // if statement, but I tried breaking this down into more specific
            // methods for image and not-image uploads, found there wasn't
            // enough in common between them, and wound up with methods with
            // ten or so arguments.  If anyone else has a better idea, feel free
            // to suggest.
            if(imageLocation != null) {
                // Let's say there's an image specified.  So, we try to look it
                // up via readImageInfo.
                WikiImageUtils.ImageInfo imageInfo;
                imageInfo = WikiImageUtils.readImageInfo(this, imageLocation, loc, timestamp);

                // Get the image's filename, too.  Well, that is, the name it'll
                // have on the wiki.
                String wikiName = WikiImageUtils.getImageWikiName(info, imageInfo, username);

                // Make sure the image doesn't already exist.  If it does, we
                // can skip the upload.
                if(!WikiUtils.doesWikiPageExist(client, wikiName)) {
                    if(imageData == null) {
                        // No image is a problem at this point...
                        Log.w(DEBUG_TAG, "Trying to upload an image, but imageData was null at upload time?");
                        showImageErrorNotification();
                        return ReturnCode.CONTINUE;
                    }

                    // Upload now!  Do it!
                    String description = message + "\n\n" + WikiUtils.getWikiCategories(info);
                    WikiUtils.putWikiImage(client, wikiName, description, formfields, imageData);
                } else {
                    Log.w(DEBUG_TAG, "Trying to upload an image, but it already exists on the wiki?");
                }

                // Good, good.  Now, let's get some tags for posting.
                String locationTag = WikiUtils.makeLocationTag(loc);
                String prefixTag = WikiImageUtils.getImagePrefixTag(this, imageInfo, info);

                // The message is now going to be surrounded by tags.
                message = message.trim() + locationTag;

                // And the gallery entry is the name of the file plus that
                // message.
                String galleryEntry = "\nImage:" + wikiName + "|" + message + "\n";

                // Then, add the gallery entry into the page...
                page = addGalleryEntryToPage(page, galleryEntry);

                // ...make a summary...
                formfields.put("summary", prefixTag + message);

                // ...and out it goes!
                WikiUtils.putWikiPage(client, expedition, page, formfields);

            } else {
                // If we DON'T have an image, it's just a plain message.  That's
                // a lot easier than an image, but the posting's different,
                // slightly.
                String locationTag = WikiUtils.makeLocationTag(loc);

                // The summary gets a prefix depending on if it's a retro or
                // live post.  Unlike images, "live" always applies if it's not
                // a retrohash.
                String summaryPrefix;
                if(info.isRetroHash())
                    summaryPrefix = getString(R.string.wiki_post_message_summary_retro);
                else
                    summaryPrefix = getString(R.string.wiki_post_message_summary);

                formfields.put("summary", summaryPrefix + " " + message);

                // And now, insert text where need be on the page.
                String before;
                String after;

                if(page == null) {
                    // This shouldn't happen.  If it did, there's something very
                    // wrong with the wiki.
                    throw new WikiException(R.string.wiki_error_unknown);
                }

                Matcher expeditionq = RE_EXPEDITION.matcher(page);
                if(expeditionq.matches()) {
                    before = expeditionq.group(1) + expeditionq.group(2);
                    after = expeditionq.group(3);
                } else {
                    // If the expedition section doesn't exist, well, just slap
                    // it onto the end of the page.  This shouldn't happen
                    // unless someone's mucking about with the page on the web.
                    before = page;
                    after = "";
                }

                String localtime = DateTools.getWikiDateString(timestamp);

                // Attach requisite tags to the message...
                message = "\n*" + message + "  -- ~~~" + locationTag + " "
                        + localtime + "\n";

                // And go!
                WikiUtils.putWikiPage(client, expedition, before + message
                        + after, formfields);
            }

            return ReturnCode.CONTINUE;
        } catch(WikiException we) {
            // There's two possible exceptions we want to keep an eye on, both
            // of them related to throttling.  Since we're potentially posting
            // numerous edits one right after another (i.e. if the user's been
            // away from a network connection and has ten or so live updates
            // queued up), throttling IS possible, and that can be handled by
            // waiting it out for a minute or so.
            if(we.getErrorTextId() == R.string.wiki_error_throttled || we.getErrorTextId() == R.string.wiki_error_rate_limit) {
                showThrottleNotification();
            } else {
                // Otherwise, throw a normal notification.
                showPausingErrorNotification(getString(we.getErrorTextId()), resolveWikiExceptionActions(we));
            }

            return ReturnCode.PAUSE;
        } catch(Exception e) {
            // Okay, first off, are we still connected?  An Exception will get
            // thrown if the connection just goes poof while we're trying to do
            // something.
            if(!AndroidUtil.isConnected(this)) {
                // We're not!  Go to disconnected mode and wait.
                showWaitingForConnectionNotification();
            } else {
                // Otherwise, we're kinda stumped.  Maybe the user will know
                // what to do?
                Log.e(DEBUG_TAG, "Unknown wiki problem", e);
                showPausingErrorNotification(getString(R.string.wiki_notification_general_error), resolveWikiExceptionActions(null));
            }

            return ReturnCode.PAUSE;
        }
        // Eh, forget it.
    }

    @Override
    protected void onQueueStart() {
        // WAKELOCK!  Front and center!
        mWakeLock.acquire(WAKELOCK_TIMEOUT);

        // If we're starting, that means we're not waiting anymore.  Makes
        // sense.
        hideWaitingForConnectionNotification();
        hideThrottleNotification();
        hidePausingErrorNotification();

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

        // Notifications go boom, too.
        removeActiveNotification();

        // We might get an abort during pause, so...
        hidePausingErrorNotification();
    }

    @Override
    protected String serializeIntent(@NonNull Intent i) {
        Log.d(DEBUG_TAG, "Now serializing an intent...");
        try {
            // Let's mash this all down into JSON.  It's a reasonably right
            // thing to do, more or less.
            JSONObject toReturn = new JSONObject();

            // For the date, just use the timestamp.
            Calendar cal = (Calendar) i.getSerializableExtra(EXTRA_TIMESTAMP);
            if(cal != null) {
                toReturn.put("timestamp",
                        Long.valueOf(cal.getTimeInMillis()).toString());
            }

            // The location, if known, is two doubles.  Also easy.
            Location loc = i.getParcelableExtra(EXTRA_LOCATION);
            if(loc != null) {
                JSONObject location = new JSONObject();
                location.put("latitude", loc.getLatitude());
                location.put("longitude", loc.getLongitude());
                toReturn.put("location", location);
            }

            // The image is a URI...
            Uri uri = i.getParcelableExtra(EXTRA_IMAGE);
            if(uri != null) {
                toReturn.put("image", uri.toString());
            }

            // ...and a byte array.  That's the troublesome one, as it's large.
            byte[] imageData = i.getByteArrayExtra(EXTRA_IMAGE_DATA);
            if(imageData != null) {
                toReturn.put("imageData",
                        Base64.encodeToString(imageData, Base64.DEFAULT));
            }

            // Info time!
            Info info = i.getParcelableExtra(EXTRA_INFO);
            if(info != null) {
                JSONObject infoObj = new JSONObject();
                infoObj.put("latitude", info.getLatitude());
                infoObj.put("longitude", info.getLongitude());
                infoObj.put("timestamp",
                        Long.valueOf(info.getDate().getTime()).toString());

                Graticule g = info.getGraticule();
                if(g != null) {
                    JSONObject graticule = new JSONObject();
                    graticule.put("latitude", g.getLatitude());
                    graticule.put("longitude", g.getLongitude());
                    graticule.put("isSouth", g.isSouth());
                    graticule.put("isWest", g.isWest());

                    infoObj.put("graticule", graticule);
                }

                toReturn.put("info", infoObj);
            }

            // Finally, the message.
            String message = i.getStringExtra(EXTRA_MESSAGE);
            if(message != null) {
                toReturn.put("message", message);
            }

            // And out it goes!
            return toReturn.toString();
        } catch(Exception e) {
            // If we got an exception, we're in deep trouble.
            Log.e(DEBUG_TAG, "Exception when serializing an Intent!", e);
            return "{}";
        }
    }

    @Override
    protected Intent deserializeIntent(@NonNull String input) {
        // Now we go the other way around.
        Intent toReturn = new Intent();

        try {
            // Since this should be all JSON, all the time, this simplifies
            // quite a bit, it turns out.
            JSONObject incoming = new JSONObject(input);

            // Date, as a long (as a String).
            String timestamp = incoming.optString("timestamp");
            if(!timestamp.isEmpty()) {
                try {
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(Long.parseLong(timestamp));
                    toReturn.putExtra(EXTRA_TIMESTAMP, cal);
                } catch(NumberFormatException nfe) {
                    Log.w(DEBUG_TAG, "Couldn't parse post date " +
                            timestamp + " as a long, ignoring...", nfe);
                }
            }

            // Location, as two doubles.
            JSONObject location = incoming.optJSONObject("location");
            if(location != null) {
                try {
                    Location loc = new Location("");
                    loc.setLatitude(location.getDouble("latitude"));
                    loc.setLongitude(location.getDouble("longitude"));
                    toReturn.putExtra(EXTRA_LOCATION, loc);
                } catch(JSONException je) {
                    Log.w(DEBUG_TAG, "Couldn't parse location from " +
                            location.toString() + ", ignoring...", je);
                }
            }

            // Image URI, as a string.
            String image = incoming.optString("image");
            if(!image.isEmpty()) {
                toReturn.putExtra(EXTRA_IMAGE, Uri.parse(image));
            }

            // Image data, as a byte array.
            String imageDataBase64 = incoming.optString("imageData");
            if(!imageDataBase64.isEmpty()) {
                toReturn.putExtra(EXTRA_IMAGE_DATA,
                        Base64.decode(imageDataBase64, Base64.DEFAULT));
            }

            // The Info object, as a mess of things.
            JSONObject infoObj = incoming.optJSONObject("info");
            if(infoObj != null) {
                try {
                    double lat = infoObj.getDouble("latitude");
                    double lon = infoObj.getDouble("longitude");
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(
                            Long.parseLong(infoObj.getString("timestamp")));

                    Graticule grat = null;
                    JSONObject gratObj = infoObj.optJSONObject("graticule");
                    if(gratObj != null) {
                        // Notably, this doesn't have to have a graticule.  It
                        // could be a globalhash.
                        grat = new Graticule(gratObj.getInt("latitude"),
                                gratObj.getBoolean("isSouth"),
                                gratObj.getInt("longitude"),
                                gratObj.getBoolean("isWest"));
                    }

                    toReturn.putExtra(EXTRA_INFO, new Info(lat, lon, grat, cal));
                } catch(JSONException je) {
                    Log.w(DEBUG_TAG, "Couldn't parse something from the Info object, giving up and ignoring...", je);
                } catch(NumberFormatException nfe) {
                    Log.w(DEBUG_TAG, "Couldn't parse info date " +
                            infoObj.getString("timestamp") +
                            " as a long, giving up and ignoring...", nfe);
                }
            }

            // Finally, the message.
            String message = incoming.optString("message");
            if(!message.isEmpty()) {
                toReturn.putExtra(EXTRA_MESSAGE, message);
            }

            // There!  Rebuilt!
            return toReturn;
        } catch(JSONException je) {
            Log.e(DEBUG_TAG, "Something went really wrong deserializing a JSON blob, returning null...", je);
            return null;
        }
    }

    @Override
    protected boolean resumeOnNewIntent() {
        // Try to resume the queue on a new intent.  If it fails again, it'll
        // just pause again.
        return true;
    }

    private void showActiveNotification() {
        NotificationCompat.Builder builder = getFreshNotificationBuilder()
                .setOngoing(true)
                .setContentTitle(getString(R.string.wiki_notification_title))
                .setContentText("")
                .setSmallIcon(R.drawable.ic_stat_file_file_upload);

        mNotificationManager.notify(R.id.wiki_working_notification, builder.build());
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
        NotificationCompat.Builder builder = getFreshNotificationBuilder()
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentTitle(getString(R.string.wiki_notification_image_error_title))
                .setContentText(getString(R.string.wiki_notification_image_error_content))
                .setSmallIcon(R.drawable.ic_stat_alert_warning);

        mNotificationManager.notify(R.id.wiki_image_error_notification, builder.build());
    }

    private void showWaitingForConnectionNotification() {
        NotificationCompat.Builder builder = getFreshNotificationBuilder()
                .setOngoing(true)
                .setContentTitle(getString(R.string.wiki_notification_waiting_for_connection_title))
                .setContentText(getString(R.string.wiki_notification_waiting_for_connection_content))
                .setSmallIcon(R.drawable.ic_stat_navigation_more_horiz)
                .setContentIntent(getBasicCommandIntent(QueueService.COMMAND_RESUME));

        mNotificationManager.notify(R.id.wiki_waiting_notification, builder.build());

        WorkRequest connectivityWorkRequest =
                new OneTimeWorkRequest.Builder(ConnectivityWorker.class)
                        .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                        .build();

        mLastWikiConnectivityRequestId = connectivityWorkRequest.getId();
        WorkManager.getInstance(this).enqueue(connectivityWorkRequest);
    }

    private void hideWaitingForConnectionNotification() {
        mNotificationManager.cancel(R.id.wiki_waiting_notification);

        // If there's still a request waiting, cancel it.  This really shouldn't
        // matter (worst case, it just means a stray RESUME will be shot off),
        // and chances are the ID will be lost as soon as this service ends,
        // but let's just try anyway.
        if(mLastWikiConnectivityRequestId != null) {
            WorkManager.getInstance(this)
                    .cancelWorkById(mLastWikiConnectivityRequestId);
        }
    }

    private void showPausingErrorNotification(String reason, NotificationAction[] actions) {
        // This one (hopefully) gets its own PendingIntent (preferably something
        // that'll help solve the problem, like a username prompt).
        NotificationCompat.Builder builder = getFreshNotificationBuilder()
                .setContentTitle(getString(R.string.wiki_notification_error_title))
                .setContentText(reason)
                .setSmallIcon(R.drawable.ic_stat_alert_error);

        if (actions.length >= 1 && actions[0] != null) {
            builder.setContentIntent(actions[0].actionIntent);
            builder.addAction(actions[0].icon, actions[0].title, actions[0].actionIntent);
        }

        if (actions.length >= 2 && actions[1] != null) builder.addAction(actions[1].icon, actions[1].title, actions[1].actionIntent);
        if (actions.length >= 3 && actions[2] != null) builder.addAction(actions[2].icon, actions[2].title, actions[2].actionIntent);

        mNotificationManager.notify(R.id.wiki_error_notification, builder.build());
    }

    private void hidePausingErrorNotification() {
        mNotificationManager.cancel(R.id.wiki_error_notification);
    }

    private void showThrottleNotification() {
        // Throttling just means we wait a minute before we try again.  The user
        // is free to force the issue, however.
        NotificationCompat.Builder builder = getFreshNotificationBuilder()
                .setAutoCancel(true)
                .setOngoing(true)
                .setContentTitle(getString(R.string.wiki_notification_throttle_title))
                .setContentText(getString(R.string.wiki_notification_throttle_content))
                .setContentIntent(getBasicCommandIntent(QueueService.COMMAND_RESUME))
                .setSmallIcon(R.drawable.ic_stat_av_av_timer);

        mNotificationManager.notify(R.id.wiki_throttle_notification, builder.build());

        // Also, get the alarm ready.
        mAlarmManager.set(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + THROTTLE_DELAY,
                getBasicCommandIntent(QueueService.COMMAND_RESUME));
    }

    private void hideThrottleNotification() {
        mNotificationManager.cancel(R.id.wiki_throttle_notification);
        mAlarmManager.cancel(getBasicCommandIntent(QueueService.COMMAND_RESUME));
    }

    @SuppressLint("NewApi")
    private NotificationCompat.Builder getFreshNotificationBuilder() {
        // This just returns a fresh new NotificationCompat.Builder with the
        // default images.  We're resetting everything on each notification
        // anyway, so sharing the object is sort of a waste.
        return new NotificationCompat.Builder(this, GHDConstants.CHANNEL_WIKI)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
    }

    private String addGalleryEntryToPage(String page, String galleryEntry) {
        String before;
        String after;

        Matcher galleryq = RE_GALLERY.matcher(page);
        if (galleryq.matches()) {
            before = galleryq.group(1) + galleryq.group(2);
            after = galleryq.group(3);
        } else {
            // If we didn't match the gallery, find the Photos section
            // and create a new gallery in it.
            Matcher photosq = RE_GALLERY_SECTION.matcher(page);
            if(photosq.matches()) {
                before = photosq.group(1) + "\n<gallery>";
                after = "</gallery>\n" + photosq.group(2);
            } else {
                // If we STILL can't find it, just tack it on to the end
                // of the page.
                before = page + "\n<gallery>";
                after = "</gallery>\n";
            }
        }

        // Mash it all together.
        return before + galleryEntry + after;
    }

    private NotificationAction[] resolveWikiExceptionActions(WikiException we) {
        // This'll get the (up to) three NotificationActions associated with a
        // given WikiException (identified by string ID).
        int id = -1;

        if(we != null)
            id = we.getErrorTextId();

        NotificationAction[] toReturn = new NotificationAction[]{null,null,null};
        switch(id) {
            case R.string.wiki_conn_anon_pic_error:
            case R.string.wiki_error_bad_password:
            case R.string.wiki_error_bad_username:
            case R.string.wiki_error_username_nonexistant:
            case R.string.wiki_error_bad_login:
                toReturn[0] = new NotificationAction(
                        0,
                        PendingIntent.getActivity(this,
                                0,
                                new Intent(this, LoginPromptDialog.class),
                                PendingIntent.FLAG_UPDATE_CURRENT),
                        getString(R.string.wiki_notification_action_update_login)
                );

                toReturn[1] = getBasicNotificationAction(COMMAND_ABORT);
                break;
            default:
                // As a general case (or if a null was passed in), we just use
                // the standard retry, skip, or abort choices.  This works for a
                // surprising amount of cases, it turns out.  Simplicity wins!
                toReturn[0] = getBasicNotificationAction(COMMAND_RESUME);
                toReturn[1] = getBasicNotificationAction(COMMAND_RESUME_SKIP_FIRST);
                toReturn[2] = getBasicNotificationAction(COMMAND_ABORT);
        }

        return toReturn;
    }

    private PendingIntent getBasicCommandIntent(int command) {
        // This will just call back to the service with the given command.
        return PendingIntent.getService(this,
                command,
                new Intent(this, WikiService.class).putExtra(QueueService.COMMAND_EXTRA, command),
                0);
    }

    private NotificationAction getBasicNotificationAction(int command) {
        switch(command) {
            case COMMAND_RESUME:
                return new NotificationAction(
                        0,
                        getBasicCommandIntent(QueueService.COMMAND_RESUME),
                        getString(R.string.wiki_notification_action_retry)
                );
            case COMMAND_RESUME_SKIP_FIRST:
                return new NotificationAction(
                        0,
                        getBasicCommandIntent(QueueService.COMMAND_RESUME_SKIP_FIRST),
                        getString(R.string.wiki_notification_action_skip)
                );
            case COMMAND_ABORT:
                return new NotificationAction(
                        0,
                        getBasicCommandIntent(QueueService.COMMAND_ABORT),
                        getString(R.string.wiki_notification_action_abort)
                );
            default:
                return null;
        }
    }

    @NonNull
    @Override
    protected String getQueueName() {
        return QUEUE_NAME;
    }
}
