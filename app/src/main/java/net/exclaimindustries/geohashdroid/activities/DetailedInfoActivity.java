/*
 * DetailedInfoActivity.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.fragments.DetailedInfoFragment;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.MenuRes;

/**
 * In the event this is a phone, <code>DetailedInfoActivity</code> holds the
 * {@link DetailedInfoFragment} that gets displayed.
 */
public class DetailedInfoActivity extends CentralMapExtraActivity {
    @Override
    @MenuRes
    protected int getMenuResource() {
        return R.menu.detail_activity;
    }

    @Override
    @IdRes
    protected int getFragmentResource() {
        return R.id.detail_fragment;
    }

    @Override
    @LayoutRes
    protected int getLayoutResource() {
        return R.layout.detail_activity;
    }

    @Override
    @IdRes
    protected int getTopLevelViewResource() {
        return R.id.top_level;
    }
}
