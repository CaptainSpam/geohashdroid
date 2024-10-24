/*
 * WikiActivity.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import net.exclaimindustries.geohashdroid.R;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.MenuRes;

/**
 * Are you using a phone?  How about a very very small tablet?  Maybe you
 * somehow coerced an Android-based media player into running Geohash Droid?  If
 * the latter, then why did you do that?  If the former two, this Activity's for
 * you, assuming that what you want to do is post to the wiki.
 */
public class WikiActivity extends CentralMapExtraActivity {
    @Override
    @MenuRes
    protected int getMenuResource() {
        return R.menu.wiki_activity;
    }

    @Override
    @IdRes
    protected int getFragmentResource() {
        return R.id.wiki_fragment;
    }

    @Override
    @LayoutRes
    protected int getLayoutResource() {
        return R.layout.wiki_activity;
    }

    @Override
    @IdRes
    protected int getTopLevelViewResource() {
        return R.id.top_level;
    }
}
