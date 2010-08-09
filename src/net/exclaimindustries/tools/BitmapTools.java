/**
 * BitmapTools.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.tools;

import android.graphics.Bitmap;

/**
 * BitmapTools are, as you probably guessed, tools for Bitmap manipulation.
 * Static tools, too.
 * 
 * @author Nicholas Killewald
 */
public class BitmapTools {
    /**
     * Creates a new Bitmap that's a scaled version of the given Bitmap, but
     * with the aspect ratio preserved.  Note that this will only scale down; if
     * the image is already smaller than the given dimensions, this will return
     * the same bitmap that was given to it.
     *  
     * @param bitmap Bitmap to scale
     * @param maxWidth max width of new Bitmap, in pixels
     * @param maxHeight max height of new Bitmap, in pixels
     * @return a new, scaled Bitmap, or the old bitmap if no scaling took place
     */
    public static Bitmap createRatioPreservedDownScaledBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        if (bitmap.getHeight() > maxHeight || bitmap.getWidth() > maxWidth) {
            // So, we determine how we're going to scale this, mostly
            // because there's no method in Bitmap to maintain aspect
            // ratio for us.
            double scaledByWidthRatio = ((float)maxWidth) / bitmap.getWidth();
            double scaledByHeightRatio = ((float)maxHeight) / bitmap.getHeight();

            int newWidth = bitmap.getWidth();
            int newHeight = bitmap.getHeight();

            if (bitmap.getHeight() * scaledByWidthRatio <= maxHeight) {
                // Scale it by making the width the max, as scaling the
                // height by the same amount makes it less than or equal
                // to the max height.
                newWidth = maxWidth;
                newHeight = (int)(bitmap.getHeight() * scaledByWidthRatio);
            } else {
                // Otherwise, go by making the height its own max.
                newWidth = (int)(bitmap.getWidth() * scaledByHeightRatio);
                newHeight = maxHeight;
            }

            // Now, do the scaling!  The caller must take care of GCing the
            // original Bitmap.
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        } else {
            // If it's too small already, just return what came in.
            return bitmap;
        }
    }
}
