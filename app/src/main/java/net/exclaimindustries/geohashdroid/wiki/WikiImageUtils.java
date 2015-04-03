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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.net.Uri;
import android.provider.MediaStore;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.UnitConverter;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.tools.BitmapTools;

import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;

/**
 * <code>WikiImageUtils</code> contains static methods that do stuff to
 * image files related to uploading them to the wiki.  It entails a decent
 * amount of the picturesque functionality formerly found in
 * <code>WikiPictureEditor</code>.  It does depend a bit on {@link WikiUtils}.
 *
 * @author Nicholas Killewald
 */
public class WikiImageUtils {
    /** The largest width we'll allow to be uploaded. */
    private static final int MAX_UPLOAD_WIDTH = 800;
    /** The largest height we'll allow to be uploaded. */
    private static final int MAX_UPLOAD_HEIGHT = 600;

    /**
     * Amount of time until we don't consider this to be a "live" picture.
     * Currently 15 minutes.  Note that there's no timeout for a "retro"
     * picture, as that's determined by when the user started the trek.
     */
    private static final int LIVE_TIMEOUT = 900000;

    private static final int INFOBOX_MARGIN = 16;
    private static final int INFOBOX_PADDING = 8;

    private static Paint mBackgroundPaint;
    private static Paint mTextPaint;
    private static DecimalFormat mDistFormat = new DecimalFormat("###.######");

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

    /**
     * Loads, shrinks, stamps, and JPEGifies an image for the wiki.  Call this
     * to get a byte array, then shove that out the door.  Do it quick, too, as
     * this might get sort of big on memory use.
     *
     * @param context a Context for getting necessary paints and resources
     * @param info an Info object for determining the distance to the destination
     * @param imageInfo ImageInfo containing image stuff to retrieve
     * @param drawInfobox true to draw the infobox, false to just shrink and compress
     * @return a byte array of JPEG data, or null if something went wrong
     */
    public static byte[] createWikiImage(Context context, Info info, ImageInfo imageInfo, boolean drawInfobox) {
        // First, we want to scale the image to cut down on memory use and
        // upload time. The Geohashing wiki tends to frown upon images over
        // 150k, so scaling and compressing are the way to go.
        Bitmap bitmap = BitmapTools
                .createRatioPreservedDownscaledBitmapFromFile(
                        imageInfo.filename, MAX_UPLOAD_WIDTH,
                        MAX_UPLOAD_HEIGHT, true);

        // If the Bitmap wound up null, we're in trouble.
        if(bitmap == null) return null;

        // Then, put the infobox up if that's what we're into.
        if(drawInfobox)
            drawInfobox(context, info, imageInfo, bitmap);

        // Finally, compress it and away it goes!
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, bytes);
        byte[] toReturn = bytes.toByteArray();
        bitmap.recycle();
        return toReturn;
    }

    /**
     * Puts the handy infobox on a Bitmap.
     *
     * @param context a Context, for resources
     * @param info an Info object for determining the distance to the destination
     * @param imageInfo some ImageInfo
     * @param bitmap the Bitmap (must be read/write, will be edited)
     * @throws java.lang.IllegalArgumentException if you tried to pass an immutable Bitmap
     */
    public static void drawInfobox(Context context, Info info, ImageInfo imageInfo, Bitmap bitmap) {
        if (!bitmap.isMutable())
            throw new IllegalArgumentException("The Bitmap has to be mutable in order to draw an infobox on it!");

        // PAINT!
        makePaints(context);

        // First, we need to draw something.  Get a Canvas.
        Canvas c = new Canvas(bitmap);

        if (imageInfo.location != null) {
            // Assemble all our data.  Our four strings will be the final
            // destination, our current location, and the distance.
            String infoTo = context.getString(R.string.infobox_final) + " " + UnitConverter.makeFullCoordinateString(context, info.getFinalLocation(), false, UnitConverter.OUTPUT_LONG);
            String infoYou = context.getString(R.string.infobox_you) + " " + UnitConverter.makeFullCoordinateString(context, imageInfo.location, false, UnitConverter.OUTPUT_LONG);
            String infoDist = context.getString(R.string.infobox_dist) + " " + UnitConverter.makeDistanceString(context, mDistFormat, info.getDistanceInMeters(imageInfo.location));

            // Then, to the render method!
            String[] strings = {infoTo, infoYou, infoDist};
            drawStrings(strings, c, mTextPaint, mBackgroundPaint);
        } else {
            // Otherwise, just throw up an unknown.
            String[] strings = {context.getString(R.string.location_unknown)};
            drawStrings(strings, c, mTextPaint, mBackgroundPaint);
        }
    }

    private static void makePaints(Context context) {
        // These are for efficiency's sake so we don't rebuild paints uselessly.
        if(mBackgroundPaint == null) {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setStyle(Paint.Style.FILL);
            mBackgroundPaint.setColor(context.getResources().getColor(R.color.infobox_background));
        }

        if(mTextPaint == null) {
            mTextPaint = new Paint();
            mTextPaint.setColor(context.getResources().getColor(R.color.infobox_text));
            mTextPaint.setTextSize(context.getResources().getDimension(R.dimen.infobox_picture_fontsize));
            mTextPaint.setAntiAlias(true);
        }
    }

    private static void drawStrings(String[] strings, Canvas c, Paint textPaint, Paint backgroundPaint) {
        // We need SOME strings.  If we've got nothing, bail out.
        if(strings.length < 1) return;

        // First, init our variables.  This is as good a place as any to do so.
        Rect textBounds = new Rect();
        int[] heights = new int[strings.length];
        int totalHeight = INFOBOX_MARGIN * 2;
        int longestWidth = 0;

        // Now, loop through the strings, adding to the height and keeping track
        // of the longest width.
        int i = 0;
        for(String s : strings) {
            textPaint.getTextBounds(s, 0, s.length(), textBounds);
            if(textBounds.width() > longestWidth) longestWidth = textBounds.width();
            totalHeight += textBounds.height();
            heights[i] = textBounds.height();
            i++;
        }

        // Now, we have us a rectangle.  Draw that.
        Rect drawBounds =  new Rect(c.getWidth() - longestWidth - (INFOBOX_MARGIN * 2),
                0,
                c.getWidth(),
                totalHeight);

        c.drawRect(drawBounds, backgroundPaint);

        // Now, place each of the strings.  We'll assume the topmost one is in
        // index 0.  They should all be left-justified, too.
        i = 0;
        int curHeight = 0;
        for(String s : strings) {
            c.drawText(s, drawBounds.left + INFOBOX_MARGIN, INFOBOX_MARGIN + (INFOBOX_PADDING * (i + 1)) + curHeight, textPaint);
            curHeight += heights[i];
            i++;
        }
    }

    /**
     * Gets the live/retro prefix for an image to upload to the wiki.  Note that
     * this might be an empty string (it's possible to have a non-live,
     * non-retro image).
     *
     * @param context a Context, for translation purposes
     * @param imageInfo the requisite ImageInfo
     * @param info the requisite Info
     * @return a prefix tag
     */
    public static String getImagePrefixTag(Context context, ImageInfo imageInfo, Info info) {
        if(info.isRetroHash()) {
            return context.getText(R.string.wiki_post_picture_summary_retro).toString();
        } else if(System.currentTimeMillis() - imageInfo.timestamp < LIVE_TIMEOUT) {
            // If the picture was WITHIN the timeout, post it with the
            // live title.  If not (and it's not retro), don't put any
            // title on it.
            return context.getText(R.string.wiki_post_picture_summary).toString();
        } else {
            // If it's neither live nor retro, just return a blank.
            return "";
        }
    }
}
