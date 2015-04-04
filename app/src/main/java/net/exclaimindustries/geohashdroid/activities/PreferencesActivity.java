/*
 * SettingsActivity.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;

import net.exclaimindustries.geohashdroid.R;

import java.util.List;

/**
 * So, the actual Android class is already called "{@link PreferenceActivity}",
 * it turns out.  So let's call this one <code>PreferencesActivity</code> this
 * time around, right?  That certainly won't be confusing!
 */
public class PreferencesActivity extends PreferenceActivity {
    @Override
    public void onBuildHeaders(List<Header> target) {
        // Let's Honeycomb these preferences right up.  Headers!
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return(fragmentName.equals(MapPreferenceFragment.class.getName())
            || fragmentName.equals(WikiPreferenceFragment.class.getName())
            || fragmentName.equals(OtherPreferenceFragment.class.getName()));
    }

    /**
     * These are your garden-variety map preferences, assuming your garden is on
     * the map somewhere.
     */
    public static class MapPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_map);
        }
    }

    /**
     * These are the preferences you'll be seeing way too often if you keep
     * getting your wiki password wrong.
     */
    public static class WikiPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_wiki);
        }
    }

    /**
     * These preferences are outcasts, and nobody likes them.
     */
    public static class OtherPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_other);
        }
    }
}
