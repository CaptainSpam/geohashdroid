/*
 * SettingsActivity.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.services.AlarmService;
import net.exclaimindustries.geohashdroid.services.WikiService;
import net.exclaimindustries.geohashdroid.util.GHDBasicDialogBuilder;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.HashBuilder;
import net.exclaimindustries.tools.QueueService;

import java.util.List;

/**
 * So, the actual Android class is already called "{@link PreferenceActivity}",
 * it turns out.  So let's call this one <code>PreferencesActivity</code> this
 * time around, right?  That certainly won't be confusing!
 */
public class PreferencesActivity extends PreferenceActivity {
    private AlertDialog mCurrentDialog;

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
                // Eh, just use the string value.  That's simple enough.
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
        /**
         * This keeps track of whether or not the wiki username and/or password
         * have changed.  If so, we need to ask WikiService to resume itself, as
         * the user might've come here to resolve a bad login error.
         */
        private boolean mHasChanged = false;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_wiki);

            // Unfortunately, we can't use the otherwise-common binding method
            // for username and password, owing to the extra boolean we need to
            // track.  Worse, since we don't want to update the summary for
            // password (for obvious reasons), we can't even share the same
            // object between the two preferences.  Well, we CAN, but that won't
            // really buy us much in terms of efficiency.
            Preference usernamePref = findPreference(GHDConstants.PREF_WIKI_USER);
            usernamePref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary(newValue.toString());
                    mHasChanged = true;
                    return true;
                }
            });
            usernamePref.setSummary(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(GHDConstants.PREF_WIKI_USER, ""));

            findPreference(GHDConstants.PREF_WIKI_PASS).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    mHasChanged = true;
                    return true;
                }
            });
        }

        @Override
        public void onStop() {
            // If something changed, tell WikiService to kick back in.  Don't
            // worry; if WikiService isn't paused, this won't do anything, and
            // if it's stopped for any other reason, it'll stop again when this
            // comes in.
            if(mHasChanged) {
                mHasChanged = false;
                Intent i = new Intent(getActivity(), WikiService.class);
                i.putExtra(QueueService.COMMAND_EXTRA, QueueService.COMMAND_RESUME);
                getActivity().startService(i);
            }

            super.onStop();
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

            // Cache wiping is more a button than a preference, per se.
            findPreference("_stockWipe").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    GHDBasicDialogBuilder builder = new GHDBasicDialogBuilder(getActivity());
                    builder.setMessage(R.string.pref_stockwipe_dialog_text)
                            .setTitle(R.string.pref_stockwipe_title)
                            .setPositiveButton(getString(R.string.dialog_stockwipe_yes), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // Well, you heard the orders!
                                    dialog.dismiss();

                                    if(HashBuilder.deleteCache(getActivity())) {
                                        Toast.makeText(
                                                getActivity(),
                                                R.string.toast_stockwipe_success,
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(
                                                getActivity(),
                                                R.string.toast_stockwipe_failure,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }
                            })
                            .setNegativeButton(getString(R.string.dialog_stockwipe_no), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });

                    AlertDialog d = builder.show();
                    ((PreferencesActivity)getActivity()).setCurrentDialog(d);

                    return true;
                }
            });
        }
    }

    @Override
    protected void onPause() {
        // We really need to do something about that dialog (if it's up), else
        // it'll just leak a window all over the place.
        if(mCurrentDialog != null && mCurrentDialog.isShowing())
            mCurrentDialog.dismiss();

        super.onPause();
    }

    private void setCurrentDialog(AlertDialog dialog) {
        mCurrentDialog = dialog;
    }
}
