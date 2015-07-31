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
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.UnitConverter;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.tools.BitmapTools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Calendar;

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
    /** The JPEG quality setting to be uploaded. */
    private static final int IMAGE_JPEG_QUALITY = 90;

    /**
     * Amount of time until we don't consider this to be a "live" picture.
     * Currently 15 minutes.  Note that there's no timeout for a "retro"
     * picture, as that's determined by when the user started the trek.
     */
    private static final int LIVE_TIMEOUT = 900000;

    // Padding around the sides of the icons/text.
    private static final int INFOBOX_BOX_PADDING = 8;
    // Padding between things in the infobox.
    private static final int INFOBOX_ITEM_PADDING = 8;

    private static Paint mBackgroundPaint;
    private static Paint mTextPaint;
    private static DecimalFormat mDistFormat = new DecimalFormat("###.######");

    /**
     * This is just a convenient holder for the various info related to an
     * image.  It's used when making image calls.
     */
    public static class ImageInfo {
        /** The image's URI.  Should not be null. */
        public Uri uri;
        /**
         * The image's local filename.  May be null if it's not stored on the
         * local filesystem.
         */
        public String filename;
        /**
         * The location of either the image or the user, depending on if the
         * geodata from the image could be read.  May be null.
         */
        public Location location;
        /** The timestamp of the image, if possible.  Defaults to -1. */
        public long timestamp = -1l;
    }

    /**
     * Gets the name of a particular image as it will appear on the wiki.  This
     * name should wind up being unique unless you made two images on the exact
     * same timestamp.  So don't do that.
     *
     * @param info Info object containing expedition data
     * @param imageInfo ImageInfo object, previously made by {@link #readImageInfo(Context, Uri, Location, Calendar)}
     * @param username current username (must not be null)
     * @return the name of the image on the wiki
     */
    public static String getImageWikiName(Info info, ImageInfo imageInfo, String username) {
        // Just to be clear, this is the wiki page name (expedition and all),
        // the username, and the image's timestamp (as millis past the epoch).
        return WikiUtils.getWikiPageName(info) + "_" + username + "_" + imageInfo.timestamp + ".jpg";
    }

    /**
     * <p>
     * Creates an {@link ImageInfo} object from the given Uri.  Note that as
     * this is written, the Uri should be something that can be read by
     * MediaStore, meaning you probably want to get something in from the
     * Photos or Gallery app.
     * </p>
     *
     * <p>
     * If MediaStore can't figure it out, this will return a new ImageInfo
     * pre-populated with as much data as given when called.
     * </p>
     *
     * @param context a Context from which ContentResolver comes
     * @param uri the URI of the image
     * @param locationIfNoneSet location to use if the image has no location metadata stored in it
     * @param timeIfNoneSet Calendar containing a timestamp to use if the image has no time metadata stored in it
     * @return a brand new ImageInfo
     */
    @NonNull
    public static ImageInfo readImageInfo(@NonNull Context context, @NonNull Uri uri, @Nullable Location locationIfNoneSet, @NonNull Calendar timeIfNoneSet) {
        // We're hoping this is something that MediaStore understands.  But,
        // we'll make this first anyway, just in case.
        ImageInfo toReturn = new ImageInfo();
        toReturn.uri = uri;
        toReturn.location = locationIfNoneSet;
        toReturn.timestamp = timeIfNoneSet.getTimeInMillis();

        Cursor cursor;
        cursor = context.getContentResolver().query(uri, new String[]
                        { MediaStore.Images.ImageColumns.DATA,
                                MediaStore.Images.ImageColumns.LATITUDE,
                                MediaStore.Images.ImageColumns.LONGITUDE,
                                MediaStore.Images.ImageColumns.DATE_TAKEN },
                null, null, null);

        if(cursor == null || cursor.getCount() < 1) {
            if(cursor != null) cursor.close();
            return toReturn;
        }

        cursor.moveToFirst();

        toReturn.filename = cursor.getString(0);
        toReturn.timestamp = cursor.getLong(3);

        if(toReturn.timestamp < 0) toReturn.timestamp = timeIfNoneSet.getTimeInMillis();

        // These two could very well be null or empty.  Nothing wrong with that.
        // But if they're good, make a Location out of them.
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
            // If we get an exception, we got it because of the number parser.
            // Assume it's invalid and we're using the user's current location,
            // if that's even known (that might ALSO be null, in which case we
            // just don't have any clue where the user is, which seems a bit
            // counterintuitive to how Geohashing is supposed to work).
            toSet = locationIfNoneSet;
        }

        // Now toss the location into the info.
        toReturn.location = toSet;

        cursor.close();

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
    @Nullable
    public static byte[] createWikiImage(@NonNull Context context, @NonNull Info info, @NonNull ImageInfo imageInfo, boolean drawInfobox) {
        // First, we want to scale the image to cut down on memory use and
        // upload time. The Geohashing wiki tends to frown upon images over
        // 150k, so scaling and compressing are the way to go.
        Bitmap bitmap = BitmapTools
                .createRatioPreservedDownscaledBitmapFromUri(
                        context, imageInfo.uri, MAX_UPLOAD_WIDTH,
                        MAX_UPLOAD_HEIGHT, true);

        // If the Bitmap wound up null, we're in trouble.
        if(bitmap == null) return null;

        // Then, put the infobox up if that's what we're into.
        if(drawInfobox)
            drawInfobox(context, info, imageInfo, bitmap);

        // Finally, compress it and away it goes!
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, bytes);
        byte[] toReturn = bytes.toByteArray();
        bitmap.recycle();
        return toReturn;
    }

    /**
     * Puts the handy infobox on a Bitmap.
     *
     * @param context a Context, for resources
     * @param info an Info object for the current expedition
     * @param imageInfo some ImageInfo
     * @param bitmap the Bitmap (must be read/write, will be edited)
     * @throws java.lang.IllegalArgumentException if you tried to pass an immutable Bitmap
     */
    public static void drawInfobox(@NonNull Context context, @NonNull Info info, @NonNull ImageInfo imageInfo, @NonNull Bitmap bitmap) {
        if (!bitmap.isMutable())
            throw new IllegalArgumentException("The Bitmap has to be mutable in order to draw an infobox on it!");

        // PAINT!
        makePaints(context);

        // First, we need to draw something.  Get a Canvas.
        Canvas c = new Canvas(bitmap);

        String[] strings;
        int[] icons;

        // I'm sure this could have less redundant code if I wasn't thinking too
        // hard about it...
        if (imageInfo.location != null) {
            // Assemble all our data.  Our three strings will be the final
            // destination, our current location, and the distance.
            strings = new String[3];

            strings[0] = UnitConverter.makeFullCoordinateString(context, info.getFinalLocation(), false, UnitConverter.OUTPUT_LONG);
            strings[1] = UnitConverter.makeFullCoordinateString(context, imageInfo.location, false, UnitConverter.OUTPUT_LONG);
            strings[2] = UnitConverter.makeDistanceString(context, mDistFormat, info.getDistanceInMeters(imageInfo.location));

            // Then, to the render method!
            icons = new int[3];
            icons[0] = R.drawable.final_destination_wiki_image;
            icons[1] = R.drawable.current_location_wiki_image;
            icons[2] = R.drawable.distance_wiki_image;
        } else {
            // Otherwise, just throw up an unknown.  Location's still there,
            // though.
            strings = new String[2];
            strings[0] = UnitConverter.makeFullCoordinateString(context, info.getFinalLocation(), false, UnitConverter.OUTPUT_LONG);
            strings[1] = context.getString(R.string.location_unknown);

            icons = new int[2];
            icons[0] = R.drawable.final_destination_wiki_image;
            icons[1] = R.drawable.current_location_wiki_image;
        }

        drawStrings(context, strings, icons, c, mTextPaint, mBackgroundPaint);
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

    private static void drawStrings(Context context, String[] strings, int[] icons, Canvas c, Paint textPaint, Paint backgroundPaint) {
        // All we do here is prepare the Drawables before tossing them to the
        // OTHER method.  Hence the need for a Context.
        Drawable[] drawables = new Drawable[icons.length];
        for(int i = 0; i < icons.length; i++) {
            drawables[i] = context.getResources().getDrawable(icons[i]);
        }

        drawStrings(strings, drawables, c, textPaint, backgroundPaint);
    }

    private static void drawStrings(String[] strings, Drawable[] icons, Canvas c, Paint textPaint, Paint backgroundPaint) {
        // We need SOME strings.  If we've got nothing, bail out.  This doesn't
        // apply to icons, though; if there's fewer icons than there are
        // strings, the strings just get placed on the left margin.
        if(strings.length < 1) return;
        if(icons == null) icons = new Drawable[0];

        // First, init our variables.  This is as good a place as any to do so.
        Rect textBounds = new Rect();
        int[] stringHeights = new int[strings.length];
        int[] iconHeights = new int[strings.length]; // Yes, strings.length.
        int totalHeight = INFOBOX_BOX_PADDING * 2;
        int longestWidth = 0;

        // The height of the box is the total heights of either the texts or
        // images (whichever is bigger), plus the margins.  The width of it is
        // the LONGEST icon/text combo.
        for(int i = 0; i < strings.length; i++) {
            String s = strings[i];
            textPaint.getTextBounds(s, 0, s.length(), textBounds);
            int textWidth = textBounds.width();
            int textHeight = textBounds.height();

            int iconWidth = 0;
            int iconHeight = 0;

            // If we even HAVE an icon for this...
            if(icons.length > i) {
                // With an extra shot of padding for the icon...
                iconWidth = icons[i].getIntrinsicWidth() + INFOBOX_ITEM_PADDING;
                iconHeight = icons[i].getIntrinsicHeight();
            }

            // Now, add the tallest of those into the height...
            totalHeight += (textHeight > iconHeight ? textHeight : iconHeight);

            // ...keep track of the individual heights so we don't have to keep
            // recalculating them...
            stringHeights[i] = textHeight;
            iconHeights[i] = iconHeight;

            // ...and see if the sum of the widths is wider than the widest we
            // found so far.
            if(textWidth + iconWidth > longestWidth) {
                longestWidth = textWidth + iconWidth;
            }
        }

        // With the total height and widest width, we've got us a rectangle.
        Rect drawBounds =  new Rect(c.getWidth() - longestWidth - (INFOBOX_BOX_PADDING * 2),
                0,
                c.getWidth(),
                totalHeight);

        c.drawRect(drawBounds, backgroundPaint);

        // Now, place each of the strings with their respective icons next to
        // them.  Topmost one is index 0, they're all left-justified, and the
        // icons go in first.
        int curHeight = INFOBOX_BOX_PADDING;
        for(int i = 0; i < strings.length; i++) {
            // Oh, and we want to center the text and the icons.  The way the
            // textBounds grabber works, it just gives us the rectangle around
            // the entire text.  That rectangle, however, doesn't account for
            // any font descenders or whatnot unless such glyphs are in the
            // string itself.  For what we're doing, however, that should work,
            // as the numbers and letters we use don't descend.  If that changes
            // at any point, we need to get more in-depth with font analysis to
            // make sure everything centers consistently on the same baseline.
            int iconOffset = 0;
            int textOffsetX = 0;
            int textOffsetY = 0;
            int heightOffset;

            if(stringHeights[i] > iconHeights[i]) {
                // String is taller, icon needs to adjust.
                iconOffset = (stringHeights[i] - iconHeights[i]) / 2;
                heightOffset = stringHeights[i];
            } else {
                // Icon is taller, string needs to adjust.
                textOffsetY = (iconHeights[i] - stringHeights[i]) / 2;
                heightOffset = iconHeights[i];
            }

            // textOffsetY must also be adjusted, since Android draws text from
            // the baseline, not the top-left corner of the text block.
            textOffsetY += stringHeights[i];

            // The icon and text need to be vertically centered.  The way that
            // happens depends on which is bigger.  At time of writing, the
            // icons were, but knowing me, this could change at any time.

            // Icon!
            if(icons.length > i) {
                icons[i].setBounds(drawBounds.left + INFOBOX_BOX_PADDING,
                        curHeight + iconOffset,
                        drawBounds.left + INFOBOX_BOX_PADDING + icons[i].getIntrinsicWidth(),
                        curHeight + iconOffset + icons[i].getIntrinsicHeight());
                icons[i].draw(c);

                textOffsetX = icons[i].getIntrinsicWidth() + INFOBOX_ITEM_PADDING;
            }

            // Text!
            c.drawText(strings[i],
                    drawBounds.left + INFOBOX_BOX_PADDING + textOffsetX,
                    curHeight + textOffsetY,
            textPaint);

            // Then set the height for the next row.
            curHeight += heightOffset;
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
    @NonNull
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
