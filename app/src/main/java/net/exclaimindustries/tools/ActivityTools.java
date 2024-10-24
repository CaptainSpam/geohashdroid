/*
 * ActivityTools.java
 * Copyright (C) 2024 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.tools;

import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * <code>ActivityTools</code> encompasses some stuff that's handy for Activities
 * but isn't, y'know, IN an Activity.
 */
public final class ActivityTools {
    /**
     * Deal with window insets.  Geohash Droid isn't a Compose app yet, so this
     * will have to do for now.  Call this AFTER setContentView.
     * TODO: I guess I gotta migrate to Compose at some point...
     */
    public static void dealWithInsets(@NonNull AppCompatActivity activity, @IdRes int id) {
        // I guess we've got insets to deal with now?  Fine, let's deal with
        // them here.
        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(id), (v, windowInsets) ->
        {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.topMargin = insets.top;
            mlp.leftMargin = insets.left;
            mlp.bottomMargin = insets.bottom;
            mlp.rightMargin = insets.right;
            v.setLayoutParams(mlp);

            return WindowInsetsCompat.CONSUMED;
        });
    }
}
