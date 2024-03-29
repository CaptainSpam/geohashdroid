/*
 * BaseGHDThemeActivity.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatActivity;

/**
 * A <code>BaseGHDThemeActivity</code> used to do a lot involving setting up the
 * day/night mode stuff, but since all that got moved into the OS, there's not
 * much left in here.
 */
public abstract class BaseGHDThemeActivity extends AppCompatActivity {
    /**
     * Returns whether or not the app is in night mode, as a convenience method.
     * Note that this only returns if it's actively night mode, and does not
     * differentiate between "use system default" and manually setting night
     * mode.
     *
     * @return true for night, false for not
     */
    protected boolean isNightMode() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
}
