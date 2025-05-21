/*
 * ActivityTools.java
 * Copyright (C) 2024 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.tools;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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
        View view = activity.findViewById(id);
        ViewGroup.MarginLayoutParams originalLayoutParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();

        // I guess we've got insets to deal with now?  Fine, let's deal with
        // them here.
        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(id), (v, windowInsets) ->
        {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.topMargin = originalLayoutParams.topMargin + insets.top;
            mlp.leftMargin = originalLayoutParams.leftMargin + insets.left;
            mlp.bottomMargin = originalLayoutParams.bottomMargin + insets.bottom;
            mlp.rightMargin = originalLayoutParams.rightMargin + insets.right;
            v.setLayoutParams(mlp);

            return WindowInsetsCompat.CONSUMED;
        });

        // Also, API 35 does some... weird things with the status/navigation bar
        // colors.  We'll force the proper situation here.
        boolean dayMode = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO;
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(activity.getWindow(), activity.findViewById(id));
        insetsController.setAppearanceLightStatusBars(dayMode);
        insetsController.setAppearanceLightNavigationBars(dayMode);
    }
}
