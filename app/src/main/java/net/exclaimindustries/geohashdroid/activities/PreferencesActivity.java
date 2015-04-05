/*
 * SettingsActivity.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.services.AlarmService;
import net.exclaimindustries.geohashdroid.util.GHDConstants;

import java.util.List;

/**
 * So, the actual Android class is already called "{@link PreferenceActivity}",
 * it turns out.  So let's call this one <code>PreferencesActivity</code> this
 * time around, right?  That certainly won't be confusing!
 */
public class PreferencesActivity extends PreferenceActivity {
    /**
     * This largely comes from Android Studio's default Setting Activity wizard
     * thingamajig.  It conveniently updates preferences with summaries.
     */
    private static Preference.OnPreferenceChangeListener mSummaryUpdater = new Preference.OnPreferenceChangeListener() {

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            // The basic stringy version of the value.
            String stringValue = newValue.toString();

            if(preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);
            } else {
                // Eh, just use the string value.  Chances are if we got here, I
                // did something wrong.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Also from Android Studio, this attaches a preference to the summary
     * updater.
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(mSummaryUpdater);

        // Trigger the listener immediately with the preference's current value.
        mSummaryUpdater.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

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

            bindPreferenceSummaryToValue(findPreference(GHDConstants.PREF_INFOBOX_SIZE));
            bindPreferenceSummaryToValue(findPreference(GHDConstants.PREF_DIST_UNITS));
            bindPreferenceSummaryToValue(findPreference(GHDConstants.PREF_COORD_UNITS));
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

            bindPreferenceSummaryToValue(findPreference(GHDConstants.PREF_WIKI_USER));
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

            bindPreferenceSummaryToValue(findPreference(GHDConstants.PREF_STOCK_CACHE_SIZE));

            // The stock alarm preference needs to enable/disable the alarm as
            // need be.
            findPreference(GHDConstants.PREF_STOCK_SERVICE).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

                @Override
                public boolean onPreferenceChange(Preference preference,
                                                  Object newValue) {
                    if(newValue instanceof Boolean) {
                        Boolean set = (Boolean) newValue;

                        Intent i = new Intent(getActivity(), AlarmService.class);

                        if(set) {
                            // ON!  Start the service!
                            i.setAction(AlarmService.STOCK_ALARM_ON);
                        } else {
                            // OFF!  Stop the service and cancel all alarms!
                            i.setAction(AlarmService.STOCK_ALARM_OFF);
                        }

                        getActivity().startService(i);
                    }

                    return true;
                }

            });
        }
    }
}
