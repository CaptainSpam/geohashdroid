/*
 * WikiActivity.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.fragments.CentralMapExtraFragment;
import net.exclaimindustries.geohashdroid.fragments.WikiFragment;
import net.exclaimindustries.geohashdroid.util.Info;

/**
 * Are you using a phone?  How about a very very small tablet?  Maybe you
 * somehow coerced an Android-based media player into running Geohash Droid?  If
 * the latter, then why did you do that?  If the former two, this Activity's for
 * you, assuming that what you want to do is post to the wiki.
 */
public class WikiActivity extends Activity
        implements CentralMapExtraFragment.CloseListener{
    /**
     * The key for the Intent extra containing the Info object.  You really,
     * really need this, else there's really, really no point in this Activity.
     */
    public static final String INFO = "info";

    private Info mInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.wiki_activity);

        // Hi, fragment.
        FragmentManager manager = getFragmentManager();
        WikiFragment frag = (WikiFragment) manager.findFragmentById(R.id.wiki_fragment);

        // Hi, intent.
        Intent intent = getIntent();

        // Hi, Info that better be in that intent.
        mInfo = intent.getParcelableExtra(INFO);

        // Make that info LIVE!
        frag.setInfo(mInfo);

        // TODO: Also, tell the fragment to start listening for the location.
    }

    @Override
    public void extraFragmentClosing(CentralMapExtraFragment fragment) {
        // Easy enough, just finish the Activity.
        finish();
    }

    @Override
    public void extraFragmentDestroying(CentralMapExtraFragment fragment) {
        // Nothing happens here; we're already on our way out.
    }
}
