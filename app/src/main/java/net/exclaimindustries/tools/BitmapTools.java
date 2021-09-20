/*
 * BitmapTools.java
 * Copyright (C)2010 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.tools;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;

/**
 * BitmapTools are, as you probably guessed, tools for Bitmap manipulation.
 * Static tools, too.
 * 
 * @author Nicholas Killewald
 */
public class BitmapTools {
    private static final String DEBUG_TAG = "BitmapTools";

    /** Rotation enums.  Does not include mirroring the image. */
    public enum ImageRotation {
        ROTATE_0,
        ROTATE_90,
        ROTATE_180,
        ROTATE_270,
    }

    /** A convenient data class of simple image edits. */
    public static class ImageEdits {
        public ImageRotation rotation;
        public boolean flipX;
        public boolean flipY;

        public ImageEdits(ImageRotation rotation,
                          boolean flipX,
                          boolean flipY) {
            this.rotation = rotation;
            this.flipX = flipX;
            this.flipY = flipY;
        }
    }

    /**
     * Creates a new Bitmap that's a scaled version of the given Bitmap, but
     * with the aspect ratio preserved.  Note that this will only scale down; if
     * the image is already smaller than the given dimensions, this will return
     * the same bitmap that was given to it.
     *  
     * @param bitmap Bitmap to scale
     * @param maxWidth max width of new Bitmap, in pixels
     * @param maxHeight max height of new Bitmap, in pixels
     * @param reversible whether or not the ratio should be treated as
     *                   reversible; that is, if the maxWidth and maxHeight are
     *                   given as 800x600, but the image is 600x800, it will
     *                   leave the image as 600x800 instead of reduce it to 
     *                   450x600
     * @return a new, scaled Bitmap, or the old bitmap if no scaling took place, or null if it failed entirely
     */
    public static Bitmap createRatioPreservedDownscaledBitmap(Bitmap bitmap, int maxWidth, int maxHeight, boolean reversible) {
        if(bitmap == null) return null;

        // Make sure the width and height are properly reversed, if needed.
        if(reversible && shouldBeReversed(maxWidth, maxHeight, bitmap.getWidth(), bitmap.getHeight())) {
            int t = maxWidth;
            //noinspection SuspiciousNameCombination
            maxWidth = maxHeight;
            maxHeight = t;
        }

        if(bitmap.getHeight() > maxHeight || bitmap.getWidth() > maxWidth) {
            // So, we determine how we're going to scale this, mostly
            // because there's no method in Bitmap to maintain aspect
            // ratio for us.
            double scaledByWidthRatio = ((double)maxWidth) / (double)bitmap.getWidth();
            double scaledByHeightRatio = ((double)maxHeight) / (double)bitmap.getHeight();

            int newWidth;
            int newHeight;

            if (bitmap.getHeight() * scaledByWidthRatio <= maxHeight) {
                // Scale it by making the width the max, as scaling the
                // height by the same amount makes it less than or equal
                // to the max height.
                newWidth = maxWidth;
                newHeight = (int)Math.round(bitmap.getHeight() * scaledByWidthRatio);
            } else {
                // Otherwise, go by making the height its own max.
                newWidth = (int)Math.round(bitmap.getWidth() * scaledByHeightRatio);
                newHeight = maxHeight;
            }

            // Now, do the scaling!  The caller must take care of GCing the
            // original Bitmap.
            Log.d(DEBUG_TAG, "Scaling file down to " + newWidth + "x" + newHeight + "...");
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        } else {
            // If it's too small already, just return what came in.
            Log.d(DEBUG_TAG, "File is already small enough (" + bitmap.getWidth() + "x" + bitmap.getHeight() + ")");
            if(bitmap.isMutable())
                return bitmap;
            else
                return bitmap.copy(bitmap.getConfig(), true);
        }
    }

    /**
     * Creates a new Bitmap that's a downscaled, ratio-preserved version of
     * a file on disk.  I'll admit there's probably a shorter name I could have
     * used, but none came to mind.  The major difference between this and the
     * Bitmap-oriented one is that it will attempt a rough downsampling before
     * it loads the original into memory, which should save tons of RAM and
     * avoid unsightly OutOfMemoryErrors.
     *
     * @param filename location of bitmap to open
     * @param maxWidth max width of new Bitmap, in pixels
     * @param maxHeight max height of new Bitmap, in pixels
     * @param reversible whether or not the ratio should be treated as
     *                   reversible; that is, if the maxWidth and maxHeight are
     *                   given as 800x600, but the image is 600x800, it will
     *                   leave the image as 600x800 instead of reduce it to 
     *                   450x600
     * @return a new, appropriately scaled Bitmap, or null if it failed entirely
     */
    public static Bitmap createRatioPreservedDownscaledBitmapFromFile(String filename, int maxWidth, int maxHeight, boolean reversible) {
        // First up, open the Bitmap ONLY for its size, if we can.
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        // This will always return null thanks to inJustDecodeBounds.
        BitmapFactory.decodeFile(filename, opts);

        // If the height or width are -1 in opts, we failed.
        if(opts.outHeight < 0 || opts.outWidth < 0) {
            Log.e(DEBUG_TAG, "Error opening file " + filename);
            return null;
        }
        
        // Make sure the width and height are properly reversed, if needed.
        if(reversible && shouldBeReversed(maxWidth, maxHeight, opts.outWidth, opts.outHeight)) {
            int t = maxWidth;
            //noinspection SuspiciousNameCombination
            maxWidth = maxHeight;
            maxHeight = t;
        }

        // Now, determine the best power-of-two to downsample by.  We
        // intentionally want it one level LOWER than the target; subsampling
        // doesn't do any sort of filtering or interpolation at all, meaning if
        // we wind up where it's a clean power-of-two to reduce it, the result
        // will be grainy and blocky.  This way, we wind up scaling it later
        // WITH filtering but with far less memory being used, which is a fair
        // tradeoff.
        int tempWidth = opts.outWidth;
        int tempHeight = opts.outHeight;
        int sampleFactor = 1;
        while(tempWidth / 2 >= maxWidth && tempHeight / 2 >= maxHeight) {
            tempWidth /= 2;
            tempHeight /= 2;
            sampleFactor *= 2;
        }
        
        Log.d(DEBUG_TAG, "Downsampling file to " + tempWidth + "x" + tempHeight + "...");

        // Good!  Now, let's pop it open and scale it the rest of the way.
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sampleFactor;

        // The reversible flag is always false here, as we've already applied
        // it beforehand.
        return createRatioPreservedDownscaledBitmap(BitmapFactory.decodeFile(filename, opts), maxWidth, maxHeight, false);
    }

    /**
     * Creates a new Bitmap that's a downscaled, ratio-preserved version of the
     * content at a URI.  This will need to be something that ContentResolver
     * can figure out, AND in a way that it can decode the image bounds properly
     * before a full load.  If it can't do that, it'll return a null.
     *
     * @param context Context from which a ContentResolver can be retrieved
     * @param uri URI of content to load
     * @param maxWidth max width of new Bitmap, in pixels
     * @param maxHeight max height of new Bitmap, in pixels
     * @param reversible whether or not the ratio should be treated as
     *                   reversible; that is, if the maxWidth and maxHeight are
     *                   given as 800x600, but the image is 600x800, it will
     *                   leave the image as 600x800 instead of reduce it to
     *                   450x600
     * @return a new, appropriately scaled Bitmap, or null if it failed entirely
     */
    public static Bitmap createRatioPreservedDownscaledBitmapFromUri(Context context, Uri uri, int maxWidth, int maxHeight, boolean reversible) {
        // Since any exception is grounds for an error, let's just try-catch
        // everything and return null if anything goes wrong.
        try {
            // Alright, ContentResolver.  You'd better do your job.
            InputStream input = context.getContentResolver().openInputStream(uri);

            if(input == null) return null;

            // Otherwise, it's the same as before.  Grab the bounds only.
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, opts);
            input.close();

            // I'm reasonably certain that there's no oddities possible with the
            // Uri in this case.  If the dimensions wind up -1, I'm just saying
            // it can't open it, and if there's some obscure case where a
            // legitimate Bitmap can be loaded whose dimensions are less than
            // zero, I don't care.
            if(opts.outHeight < 0 || opts.outWidth < 0) {
                Log.e(DEBUG_TAG, "Error opening URI " + uri.toString());
                return null;
            }

            // Do the same calculations as in the filename version...
            if(reversible && shouldBeReversed(maxWidth, maxHeight, opts.outWidth, opts.outHeight)) {
                int t = maxWidth;
                //noinspection SuspiciousNameCombination
                maxWidth = maxHeight;
                maxHeight = t;
            }

            int tempWidth = opts.outWidth;
            int tempHeight = opts.outHeight;
            int sampleFactor = 1;
            while(tempWidth / 2 >= maxWidth && tempHeight / 2 >= maxHeight) {
                tempWidth /= 2;
                tempHeight /= 2;
                sampleFactor *= 2;
            }

            Log.d(DEBUG_TAG, "Downsampling image to " + tempWidth + "x" + tempHeight + "...");

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleFactor;

            // Re-open the stream, as we closed it already.
            input = context.getContentResolver().openInputStream(uri);
            if(input == null) return null;

            // Read it into a Bitmap with the new options in hand.
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, opts);

            // Close 'er up.
            input.close();

            // Let 'er rip.
            return createRatioPreservedDownscaledBitmap(bitmap, maxWidth, maxHeight, false);
        } catch (IOException ioe) {
            // Aaaaaand something went wrong, so we return null.
            return null;
        }
    }

    private static boolean shouldBeReversed(int inWidth, int inHeight, int outWidth, int outHeight) {
        // If this ratio is 1.0, we never need to reverse it.
        if(inWidth == inHeight) return false;

        // If the original is more wide than tall but the second isn't, we can
        // reverse it.  Same with the other way around.
        return (inWidth < inHeight && outWidth > outHeight) || (inWidth > inHeight && outWidth < outHeight);
    }

    /**
     * Creates a new Bitmap that is a reoriented version of the input Bitmap,
     * according to the {@link ExifInterface} orientation constant given.
     *
     * @param source the input Bitmap
     * @param orientation an {@link ExifInterface} orientation constant
     * @return a new, reoriented Bitmap
     */
    @NonNull
    public static Bitmap createReorientedBitmap(@NonNull Bitmap source, int orientation) {
        switch(orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
            case ExifInterface.ORIENTATION_UNDEFINED:
                // Nothing happens, return an immutable copy of the original.
                return source.copy(source.getConfig(), false);
            case ExifInterface.ORIENTATION_ROTATE_90:
                return createRotatedBitmap(source, 90f);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return createRotatedBitmap(source, 180f);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return createRotatedBitmap(source, 270f);
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                return createFlippedBitmap(source, true, false);
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                return createFlippedBitmap(source, false, true);
            case ExifInterface.ORIENTATION_TRANSPOSE: {
                // I suppose I COULD just make a convoluted fallthrough to force
                // this into the 270 option, but let's keep things simple.
                Bitmap flipped = createFlippedBitmap(source, true, false);
                Bitmap toReturn = createRotatedBitmap(flipped, 270f);
                flipped.recycle();
                return toReturn;
            }
            case ExifInterface.ORIENTATION_TRANSVERSE: {
                Bitmap flipped = createFlippedBitmap(source, true, false);
                Bitmap toReturn = createRotatedBitmap(flipped, 90f);
                flipped.recycle();
                return toReturn;
            }
            default:
                // None of the constants match, throw an exception.
                throw new IllegalArgumentException("The integer " + orientation + " isn't an ExifInterface orientation constant!");
        }
    }

    /**
     * Creates a new Bitmap that is a version of the input Bitmap with the given
     * {@link ImageEdits} applied.
     *
     * @param source the input Bitmap
     * @param imageEdits the edits in question
     * @return a new, edited Bitmap
     */
    public static Bitmap createEditedBitmap(@NonNull Bitmap source, @NonNull ImageEdits imageEdits) {
        Bitmap flippedBitmap = createFlippedBitmap(source, imageEdits.flipX, imageEdits.flipY);
        if(imageEdits.rotation != ImageRotation.ROTATE_0)
            return createRotatedBitmap(
                    flippedBitmap,
                    imageEdits.rotation == ImageRotation.ROTATE_90
                            ? 90f
                            : imageEdits.rotation == ImageRotation.ROTATE_180
                                ? 180f
                                : 270f);
        else
            return flippedBitmap;
    }

    /**
     * Creates a new Bitmap that is version of the input Bitmap flipped along at
     * least one axis.
     *
     * @param source the input Bitmap
     * @param horizontal horizontal flip
     * @param vertical vertical flip
     * @return a new, flipped Bitmap
     */
    public static Bitmap createFlippedBitmap(@NonNull Bitmap source, boolean horizontal, boolean vertical) {
        Matrix flipper = new Matrix();
        flipper.postScale(horizontal ? -1 : 1, vertical ? -1 : 1, (float)(source.getWidth() / 2), (float)(source.getHeight() / 2));
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), flipper, false);
    }

    /**
     * Creates a new Bitmap that is a rotated version of the input Bitmap.
     *
     * @param source the input Bitmap
     * @param angle the angle to rotate
     * @return a new, rotated Bitmap
     */
    @NonNull
    public static Bitmap createRotatedBitmap(@NonNull Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, false);
    }

    /**
     * Merge an ImageEdits with the int value from ExifInterface's orientation
     * options.
     *
     * @param imageEdits source ImageEdits
     * @param exifOrientation source ExifInterface options
     * @return a new, merged ImageEdits
     */
    public static ImageEdits mergeWithExif(ImageEdits imageEdits,
                                           int exifOrientation) {
        ImageEdits exifEdits = new ImageEdits(ImageRotation.ROTATE_0, false, false);

        // First, decompose the EXIF orientation to an ImageEdits.  That sounds
        // trivial until you get into the Transpose and Transverse options.
        switch(exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                exifEdits.rotation = ImageRotation.ROTATE_90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                exifEdits.rotation = ImageRotation.ROTATE_180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                exifEdits.rotation = ImageRotation.ROTATE_270;
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                exifEdits.flipX = true;
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                exifEdits.flipY = true;
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                exifEdits.flipX = true;
                exifEdits.rotation = ImageRotation.ROTATE_90;
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                exifEdits.flipX = true;
                exifEdits.rotation = ImageRotation.ROTATE_270;
                break;
            // No default case; anything else has no effect on the output.
        }

        // Now, turn that into something new.  To the user, the EXIF version is
        // "normal", so we have to apply our changes in relation to that.
        // TODO: On second thought, I don't think this does that at all.
        switch(exifEdits.rotation) {
            case ROTATE_0:
                exifEdits.rotation = imageEdits.rotation;
                break;
            case ROTATE_90:
                switch(imageEdits.rotation) {
                    case ROTATE_90:
                        exifEdits.rotation = ImageRotation.ROTATE_180;
                        break;
                    case ROTATE_180:
                        exifEdits.rotation = ImageRotation.ROTATE_270;
                        break;
                    case ROTATE_270:
                        exifEdits.rotation = ImageRotation.ROTATE_0;
                        break;
                    // ROTATE_0 doesn't affect things.
                }
                break;
            case ROTATE_180:
                switch(imageEdits.rotation) {
                    case ROTATE_90:
                        exifEdits.rotation = ImageRotation.ROTATE_270;
                        break;
                    case ROTATE_180:
                        exifEdits.rotation = ImageRotation.ROTATE_0;
                        break;
                    case ROTATE_270:
                        exifEdits.rotation = ImageRotation.ROTATE_90;
                        break;
                    // ROTATE_0 doesn't affect things.
                }
                break;
            case ROTATE_270:
                switch(imageEdits.rotation) {
                    case ROTATE_90:
                        exifEdits.rotation = ImageRotation.ROTATE_0;
                        break;
                    case ROTATE_180:
                        exifEdits.rotation = ImageRotation.ROTATE_90;
                        break;
                    case ROTATE_270:
                        exifEdits.rotation = ImageRotation.ROTATE_180;
                        break;
                    // ROTATE_0 doesn't affect things.
                }
                break;
        }

        // Finish off with the flips.
        exifEdits.flipX = imageEdits.flipX != exifEdits.flipX;
        exifEdits.flipY = imageEdits.flipY != exifEdits.flipY;

        // exifEdits contains the final result.  Away it goes!
        return exifEdits;
    }
}
