/*
 * DetailedInfoActivity.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.fragments.DetailedInfoFragment;
import net.exclaimindustries.geohashdroid.util.Info;

/**
 * In the event this is a phone, <code>DetailedInfoActivity</code> holds the
 * {@link DetailedInfoFragment} that gets displayed.
 */
public class DetailedInfoActivity extends Activity
        implements DetailedInfoFragment.CloseListener {
    /**
     * The key for the Intent extra containing the Info object.  This should
     * very seriously NOT be null.  If it is, you did something very wrong.
     */
    public static final String INFO = "info";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.detail_activity);

        // Slap in a fragment.  Make sure it isn't already there, of course.  It
        // might be if we're coming back from, say, a config change.
        FragmentManager manager = getFragmentManager();
        DetailedInfoFragment frag = (DetailedInfoFragment) manager.findFragmentById(R.id.detail_fragment);
        // We'd BETTER have an Intent.
        Intent intent = getIntent();

        // And that intent BETTER have an Info.
        Info inf = intent.getParcelableExtra(INFO);

        // Since the fragment's part of the layout, we can't set an argument
        // anymore.  So, just update the Info.
        frag.setCloseListener(this);
        frag.setInfo(inf);
    }

    @Override
    public void detailedInfoClosing() {
        // Easy enough, just finish the Activity.
        finish();
    }
}
