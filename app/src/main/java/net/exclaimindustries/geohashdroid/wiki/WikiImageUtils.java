/*
 * WikiImageUtils.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.wiki;

import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.provider.MediaStore;

import net.exclaimindustries.geohashdroid.util.Info;

/**
 * <code>WikiImageUtils</code> contains static methods that do stuff to
 * image files related to uploading them to the wiki.  It entails a decent
 * amount of the picturesque functionality formerly found in
 * <code>WikiPictureEditor</code>.  It does depend a bit on {@link WikiUtils}.
 *
 * @author Nicholas Killewald
 */
public class WikiImageUtils {
    /**
     * This is just a convenient holder for the various info related to an
     * image.  It's used when making image calls.
     */
    public static class ImageInfo {
        public Uri uri;
        public String filename;
        public Location location;
        public long timestamp;
    }

    /**
     * Gets the name of a particular image as it will appear on the wiki.  This
     * name should wind up being unique unless you made two images on the exact
     * same timestamp.  So don't do that.
     *
     * @param info Info object containing expedition data
     * @param imageInfo ImageInfo object, previously made by {@link #readImageInfo(Context, Uri, Location)}
     * @param username current username (must not be null)
     * @return the name of the image on the wiki
     */
    public static String getImageWikiName(Info info, ImageInfo imageInfo, String username) {
        // Just to be clear, this is the wiki page name (expedition and all),
        // the username, and the image's timestamp (as millis past the epoch).
        return WikiUtils.getWikiPageName(info) + "_" + username + "_" + imageInfo.timestamp + ".jpg";
    }

    /**
     * Creates an {@link ImageInfo} object from the given Uri.  Note that as
     * this is written, the Uri should be something that can be read by
     * MediaStore, meaning you probably want to get something in from the
     * Photos or Gallery app.
     *
     * @param context a Context from which ContentResolver comes
     * @param uri the URI of the image
     * @param locationIfNoneSet location to use if the image has no location metadata stored in it
     * @return a brand new ImageInfo, or null if there were problems
     */
    public static ImageInfo readImageInfo(Context context, Uri uri, Location locationIfNoneSet) {
        // We're hoping this is something that MediaStore understands.  If not,
        // or if the image doesn't exist anyway, we're returning null, which is
        // interpreted by the intent handler to mean there's no image here, so
        // an error should be thrown.
        ImageInfo toReturn = null;

        if(uri != null) {
            Cursor cursor;
            cursor = context.getContentResolver().query(uri, new String[]
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
}
