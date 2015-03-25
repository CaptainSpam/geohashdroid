/**
 * WikiService.java
 * Copyright (C)2015 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.services;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.MediaStore;
import android.util.Log;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.wiki.WikiException;
import net.exclaimindustries.geohashdroid.wiki.WikiUtils;
import net.exclaimindustries.tools.AndroidUtil;
import net.exclaimindustries.tools.QueueService;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Calendar;

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

    /**
     * This is only here because {@link Notification.Action} doesn't exist in
     * API 16, which is what I'm targeting.  Darn!  It works astonishingly
     * similar to it, if by that you accept simply calling the API 16 version of
     * {@link Notification.Builder#addAction(int, CharSequence, android.app.PendingIntent)}
     * with the appropriate data to be "astonishingly similar", which I do.
     */
    private class NotificationAction {
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
     * This listens for the connectivity broadcasts so we know if it's safe to
     * kick the queue back in action after a disconnect.  Well... I guess not so
     * much "safe" as "possible".
     */
    public static class WikiServiceConnectivityListener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // Ding!  Are we back yet?
            if(AndroidUtil.isConnected(context)) {
                // Aha!  We're up!  Send off a command to resume the queue!
                Intent i = new Intent(context, WikiService.class);
                i.putExtra(QueueService.COMMAND_EXTRA, QueueService.COMMAND_RESUME);
                context.startService(i);
            }

        }
    }

    private static final String DEBUG_TAG = "WikiService";

    private NotificationManager mNotificationManager;
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
     * If not given, will assume the user's location is/was unknown.  If posting
     * an image, any location metadata stored in that image will override this,
     * but if no such data exists there, this will be used instead.
     */
    public static final String EXTRA_LOCATION = "net.exclaimindustries.geohashdroid.EXTRA_LOCATION";

    @Override
    public void onCreate() {
        super.onCreate();
        
        // WakeLock awaaaaaay!
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WikiService");
        
        // Also, get the NotificationManager on standby.
        mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
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

        // Prep an HttpClient for later...
        HttpClient client = new DefaultHttpClient();

        // To Preferences!
        SharedPreferences prefs = getSharedPreferences(GHDConstants.PREFS_BASE, 0);
        String username = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
        String password = prefs.getString(GHDConstants.PREF_WIKI_PASS, "");

        // If you're missing something vital, bail out.
        if(info == null || message == null || timestamp == null) {
            Log.e(DEBUG_TAG, "Intent was missing some vital data (either Info, message, or timestamp), giving up...");
            return ReturnCode.CONTINUE;
        }

        try {
            // If we got a username/password combo, try to log in.
            if(!username.isEmpty() && !password.isEmpty()) {
                WikiUtils.login(client, username, password);
            }

            // Let's say there's an image specified.
            ImageInfo imageInfo;
            if (imageLocation != null) {
                // If so, see if the user's even specified a login.  The wiki does
                // not allow anonymous uploads.

                if (username.equals("")) {
                    // Aww.  Failure.
                    // TODO: Need a real PendingIntent here!
                    showPausingErrorNotification(getText(R.string.wiki_conn_anon_pic_error).toString(), null, null, null);
                    return ReturnCode.PAUSE;
                }

                // If that's all set, we can try to look it up on the system.
                imageInfo = readImageInfo(imageLocation, loc);

                // But, if said info remains null, we've got a problem.  The user
                // wanted an image uploaded, but we can't do that, so we have to
                // abandon this intent.  However, I don't think that's a showstopper
                // in terms of continuing the queue.
                if (imageInfo == null) {
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

                // Make sure the image doesn't already exist.  If it does, we
                // can skip the entire "shrink image, annotate it, and upload
                // it" steps.
                if(!WikiUtils.doesWikiPageExist(client, getImageWikiName(info, imageInfo, username))) {
                    // TODO: Create bitmap and upload it.
                }
            }

            return ReturnCode.CONTINUE;
        } catch (WikiException we) {
            // TODO: Handle wiki exceptions.
        } catch (Exception e) {
            // Okay, first off, are we still connected?  An Exception will get
            // thrown if the connection just goes poof while we're trying to do
            // something.
            if(!AndroidUtil.isConnected(this)) {
                // We're not!  Go to disconnected mode and wait.
                showWaitingForConnectionNotification();
                return ReturnCode.PAUSE;
            } else {
                // Otherwise, we're kinda stumped.  Maybe the user will know
                // what to do?
                // TODO: Handle other exceptions.
            }
        }

        // We shouldn't be here.
        return ReturnCode.PAUSE;
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
        // We'll encode one line per object, with the last lines reserved for
        // the entire message (the only thing of these that can have multiple
        // lines).
        OutputStreamWriter osw = new OutputStreamWriter(os);
        StringBuilder builder = new StringBuilder();

        // Always write out the \n, even if it's null.  An empty line will be
        // deserialized as a null.  Yes, even if that'll cause an error later.

        // The date can come in as a long.
        Calendar c = (Calendar)i.getParcelableExtra(EXTRA_TIMESTAMP);
        if(c != null)
            builder.append(c.getTimeInMillis());
        builder.append('\n');

        // The location is just two doubles.  Split 'em with a colon.
        Location loc = (Location)i.getParcelableExtra(EXTRA_LOCATION);
        if(loc != null)
            builder.append(Double.toString(loc.getLatitude()))
                    .append(':')
                    .append(Double.toString(loc.getLongitude()));
        builder.append('\n');

        // The image is just a URI.  Easy so far.
        Uri uri = (Uri)i.getParcelableExtra(EXTRA_IMAGE);
        if(uri != null)
            builder.append(uri.toString());
        builder.append('\n');

        // And now comes Info.  It encompasses two doubles (the destination),
        // a Date (the date of the expedition), and a Graticule (two ints
        // and two booleans).  The Graticule part can be null if this is a
        // globalhash.
        Info info = (Info)i.getParcelableExtra(EXTRA_INFO);
        if(info != null) {
            builder.append(Double.toString(info.getLatitude()))
                    .append(':')
                    .append(Double.toString(info.getLongitude()))
                    .append(':')
                    .append(Long.toString(info.getDate().getTime()))
                    .append(':');

            if(!info.isGlobalHash()) {
                Graticule g = info.getGraticule();
                builder.append(Integer.toString(g.getLatitude()))
                        .append(':')
                        .append(g.isSouth() ? '1' : '0')
                        .append(':')
                        .append(Integer.toString(g.getLongitude()))
                        .append(':')
                        .append((g.isWest() ? '1' : '0'));
            }
        }
        builder.append('\n');

        // The rest of it is the message.  We'll URI-encode it so it comes out
        // as a single string without line breaks.
        String message = i.getStringExtra(EXTRA_MESSAGE);
        if(message != null)
            builder.append(Uri.encode(message));

        // Right... let's write it out.
        try {
            osw.write(builder.toString());
        } catch (IOException e) {
            // If we got an exception, we're in deep trouble.
            Log.e(DEBUG_TAG, "Exception when serializing an Intent!", e);
        }
    }

    @Override
    protected Intent deserializeFromDisk(InputStream is) {
        // Now we go the other way around.
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        Intent toReturn = new Intent();

        try {
            // Date, as a long.
            String read = br.readLine();
            if(read != null && !read.isEmpty()) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(Long.parseLong(read));
                toReturn.putExtra(EXTRA_TIMESTAMP, cal);
            }

            // Location, as two doubles.
            read = br.readLine();
            if(read != null && !read.isEmpty()) {
                String parts[] = read.split(":");
                Location loc = new Location("");
                loc.setLatitude(Double.parseDouble(parts[0]));
                loc.setLongitude(Double.parseDouble(parts[1]));
                toReturn.putExtra(EXTRA_LOCATION, loc);
            }

            // Image URI, as a string.
            read = br.readLine();
            if(read != null && !read.isEmpty()) {
                Uri file = Uri.parse(read);
                toReturn.putExtra(EXTRA_IMAGE, file);
            }

            // The Info object, as a mess of things.
            read = br.readLine();
            if(read != null && !read.isEmpty()) {
                String parts[] = read.split(":");
                double lat = Double.parseDouble(parts[0]);
                double lon = Double.parseDouble(parts[1]);
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(Long.parseLong(parts[2]));

                Graticule grat = null;

                // If there's less than seven elements, this is a null Graticule
                // and thus a globalhash.  Otherwise...
                if(parts.length >= 7) {
                    int glat = Integer.parseInt(parts[3]);
                    boolean gsouth = parts[4].equals("1");
                    int glon = Integer.parseInt(parts[5]);
                    boolean gwest = parts[6].equals("1");
                    grat = new Graticule(glat, gsouth, glon, gwest);
                }

                // And now we can form an Info.
                toReturn.putExtra(EXTRA_INFO, new Info(lat, lon, grat, cal));
            }

            // Finally, the message.  This is just one URI-encoded string.
            read = br.readLine();
            if(read != null && !read.isEmpty())
                toReturn.putExtra(EXTRA_MESSAGE, Uri.decode(read));

            // There!  Rebuilt!
            return toReturn;

        } catch (IOException e) {
            Log.e(DEBUG_TAG, "Exception when deserializing an Intent!" , e);
            return null;
        }
    }

    @Override
    protected boolean resumeOnNewIntent() {
        return false;
    }


    private void showActiveNotification() {
        Notification.Builder builder = getFreshNotificationBuilder()
                .setOngoing(true)
                .setContentTitle(getString(R.string.wiki_notification_title))
                .setContentText("");

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
        Notification.Builder builder = getFreshNotificationBuilder()
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentTitle(getString(R.string.wiki_notification_image_error_title))
                .setContentText(getString(R.string.wiki_notification_image_error_content));

        mNotificationManager.notify(R.id.wiki_image_error_notification, builder.build());
    }

    private void showWaitingForConnectionNotification() {
        Notification.Builder builder = getFreshNotificationBuilder()
                .setOngoing(true)
                .setContentTitle(getString(R.string.wiki_notification_waiting_for_connection_title))
                .setContentText("");

        mNotificationManager.notify(R.id.wiki_waiting_notification, builder.build());

        // Make sure the connectivity listener's waiting for a connection.
        AndroidUtil.setPackageComponentEnabled(this, WikiServiceConnectivityListener.class, true);
    }

    private void hideWaitingForConnectionNotification() {
        mNotificationManager.cancel(R.id.wiki_waiting_notification);
        AndroidUtil.setPackageComponentEnabled(this, WikiServiceConnectivityListener.class, false);
    }

    private void showPausingErrorNotification(String reason,
                                              NotificationAction action1,
                                              NotificationAction action2,
                                              NotificationAction action3) {
        // This one (hopefully) gets its own PendingIntent (preferably something
        // that'll help solve the problem, like a username prompt).
        Notification.Builder builder = getFreshNotificationBuilder()
                .setAutoCancel(true)
                .setContentTitle(getString(R.string.wiki_notification_error_title))
                .setContentText(reason);

        if (action1 != null) {
            builder.setContentIntent(action1.actionIntent);
            builder.addAction(action1.icon, action1.title, action1.actionIntent);
        }

        if (action2 != null) builder.addAction(action2.icon, action2.title, action2.actionIntent);
        if (action3 != null) builder.addAction(action3.icon, action3.title, action3.actionIntent);

        mNotificationManager.notify(R.id.wiki_error_notification, builder.build());
    }

    @SuppressLint("NewApi")
    private Notification.Builder getFreshNotificationBuilder() {
        // This just returns a fresh new Notification.Builder with the default
        // images.  We're resetting everything on each notification anyway, so
        // sharing the object is sort of a waste.
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.geohashing_logo_notification);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            builder.setVisibility(Notification.VISIBILITY_PUBLIC);

        return builder;
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

    private String getImageWikiName(Info info, ImageInfo imageInfo, String username) {
        // Just to be clear, this is the wiki page name (expedition and all),
        // the username, and the image's timestamp (as millis past the epoch).
        return WikiUtils.getWikiPageName(info) + "_" + username + "_" + imageInfo.timestamp + ".jpg";
    }
}
