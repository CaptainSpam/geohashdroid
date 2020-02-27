/*
 * SettingsActivity.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.backup.BackupManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.services.AlarmService;
import net.exclaimindustries.geohashdroid.services.WikiService;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.HashBuilder;
import net.exclaimindustries.geohashdroid.util.KnownLocation;
import net.exclaimindustries.tools.QueueService;

import java.util.LinkedList;
import java.util.List;

import androidx.annotation.StringRes;

/**
 * <p>
 * So, the actual Android class is already called "{@link PreferenceActivity}",
 * it turns out.  So let's call this one <code>PreferencesScreen</code>, because
 * it got really confusing to call it <code>PreferencesActivity</code> like it
 * used to be.
 * </p>
 *
 * <p>
 * Note that this doesn't inherit from BaseGHDThemeActivity.  It has to
 * implement all of that itself.
 * </p>
 */
public class PreferencesScreen extends PreferenceActivity {
    private boolean mStartedInNight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mStartedInNight = prefs.getBoolean(GHDConstants.PREF_NIGHT_MODE, false);

        // We have to do this BEFORE any layouts are set up.
        if(mStartedInNight)
            setTheme(R.style.Theme_GeohashDroidDark);
        else
            setTheme(R.style.Theme_GeohashDroid);

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If the nightiness has changed since we paused, do a recreate.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(prefs.getBoolean(GHDConstants.PREF_NIGHT_MODE, false) != mStartedInNight)
            recreate();
    }

    /**
     * This largely comes from Android Studio's default Setting Activity wizard
     * thingamajig.  It conveniently updates preferences with summaries.
     */
    private static Preference.OnPreferenceChangeListener mSummaryUpdater = (preference, newValue) -> {
        updateSummary(preference, newValue);
        return true;
    };

    private static void updateSummary(Preference preference, Object newValue) {
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
    }

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
            || fragmentName.equals(OtherPreferenceFragment.class.getName())
            || fragmentName.equals(DebugFragment.class.getName()));
    }

    /**
     * These are your garden-variety map preferences, assuming your garden is on
     * the map somewhere.
     */
    public static class MapPreferenceFragment extends PreferenceFragment {
        private static final String KNOWN_NOTIFICATION_REMINDER_DIALOG = "KnownNotificationReminderDialog";

        /**
         * This {@link DialogFragment} reminds the user that we're not monsters
         * and therefore won't spam them with hundreds of notifications if they
         * really have that many known locations.
         */
        public static class KnownNotificationLimitDialogFragment extends DialogFragment {
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                String setting = getArguments().getString(GHDConstants.PREF_KNOWN_NOTIFICATION, "");

                @StringRes int dialogText;

                switch(setting) {
                    case GHDConstants.PREFVAL_KNOWN_NOTIFICATION_PER_GRATICULE:
                        dialogText = R.string.pref_knownnotification_pergraticule_reminder;
                        break;
                    case GHDConstants.PREFVAL_KNOWN_NOTIFICATION_PER_LOCATION:
                        dialogText = R.string.pref_knownnotification_perlocation_reminder;
                        break;
                    default:
                        // Really, this shouldn't happen, but if the dialog is
                        // being summoned anyway, may as well...
                        dialogText = R.string.pref_knownnotification_fallback_reminder;
                        break;
                }

                return new AlertDialog.Builder(getActivity()).setMessage(dialogText)
                        .setTitle(R.string.pref_knownnotification_reminder_title)
                        .setNegativeButton(R.string.stop_reminding_me_label, (dialog, which) -> {
                            dismiss();

                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putBoolean(GHDConstants.PREF_STOP_BUGGING_ME_KNOWN_NOTIFICATION_LIMIT, true);
                            editor.apply();

                            BackupManager bm = new BackupManager(getActivity());
                            bm.dataChanged();
                        })
                        .setPositiveButton(R.string.gotcha_label, (dialog, which) -> dismiss())
                        .create();
            }
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_map);

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

            bindPreferenceSummaryToValue(findPreference(GHDConstants.PREF_DIST_UNITS));
            bindPreferenceSummaryToValue(findPreference(GHDConstants.PREF_COORD_UNITS));
            bindPreferenceSummaryToValue(findPreference(GHDConstants.PREF_STARTUP_BEHAVIOR));

            // This one needs special handling due to its onPreferenceChange
            // being overridden elsewhere.
            Preference knownNotification = findPreference(GHDConstants.PREF_KNOWN_NOTIFICATION);
            updateSummary(knownNotification, prefs.getString(knownNotification.getKey(), GHDConstants.PREFVAL_KNOWN_NOTIFICATION_ONLY_ONCE));

            // The known locations manager is just another Activity.
            findPreference("_knownLocations").setOnPreferenceClickListener(preference -> {
                Intent i = new Intent(getActivity(), KnownLocationsPicker.class);
                startActivity(i);
                return true;
            });

            // The known locations notification preference needs a quick
            // reminder popup.
            knownNotification.setOnPreferenceChangeListener((preference, newValue) -> {
                // First off, ignore this entirely if the user told us to
                // stop bugging them or if the new setting doesn't need a
                // warning.
                boolean stopBugging = prefs.getBoolean(GHDConstants.PREF_STOP_BUGGING_ME_KNOWN_NOTIFICATION_LIMIT, false);

                // Maybe I should invest in shorter variable names.
                if(!stopBugging
                        && (newValue.equals(GHDConstants.PREFVAL_KNOWN_NOTIFICATION_PER_GRATICULE)
                            || newValue.equals(GHDConstants.PREFVAL_KNOWN_NOTIFICATION_PER_LOCATION))) {
                    // Notify!
                    DialogFragment frag = new KnownNotificationLimitDialogFragment();
                    Bundle args = new Bundle();
                    args.putString(GHDConstants.PREF_KNOWN_NOTIFICATION, newValue.toString());
                    frag.setArguments(args);
                    frag.show(getFragmentManager(), KNOWN_NOTIFICATION_REMINDER_DIALOG);
                }

                // We're also doing the summary update ourselves, as this
                // takes over the onPreferenceChange part that
                // bindPreferenceSummaryToValue needs to function.
                updateSummary(preference, newValue);

                return true;
            });
        }

        @Override
        public void onStop() {
            BackupManager bm = new BackupManager(getActivity());
            bm.dataChanged();

            super.onStop();
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
            usernamePref.setOnPreferenceChangeListener((preference, newValue) -> {
                preference.setSummary(newValue.toString());
                mHasChanged = true;
                return true;
            });
            usernamePref.setSummary(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(GHDConstants.PREF_WIKI_USER, ""));

            findPreference(GHDConstants.PREF_WIKI_PASS).setOnPreferenceChangeListener((preference, newValue) -> {
                mHasChanged = true;
                return true;
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

            BackupManager bm = new BackupManager(getActivity());
            bm.dataChanged();

            super.onStop();
        }
    }

    /**
     * These preferences are outcasts, and nobody likes them.
     */
    public static class OtherPreferenceFragment extends PreferenceFragment {
        private static final String WIPE_DIALOG = "wipeDialog";
        private static final String RESET_BUGGING_ME_DIALOG = "resetBuggingMe";

        /**
         * This is the {@link DialogFragment} that shows up when the user wants
         * to wipe the stock cache, just to make really really sure the user
         * really wants to do so.
         */
        public static class WipeCacheDialogFragment extends DialogFragment {
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                return new AlertDialog.Builder(getActivity()).setMessage(R.string.pref_stockwipe_dialog_text)
                        .setTitle(R.string.pref_stockwipe_title)
                        .setPositiveButton(R.string.dialog_stockwipe_yes, (dialog, which) -> {
                            // Well, you heard the orders!
                            dismiss();

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
                        })
                        .setNegativeButton(R.string.dialog_stockwipe_no, (dialog, which) -> dismiss())
                        .create();
            }
        }

        public static class ResetBuggingMeDialogFragment extends DialogFragment {
            @Override
            public Dialog onCreateDialog(Bundle savedInstanceState) {
                return new AlertDialog.Builder(getActivity()).setMessage(R.string.pref_reset_butting_me_dialog_text)
                        .setTitle(R.string.pref_reset_bugging_me_title)
                        .setPositiveButton(R.string.dialog_reset_bugging_me_yes, (dialog, which) -> {
                            // Well, you heard the orders!
                            dismiss();

                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                            SharedPreferences.Editor editor = prefs.edit();

                            // This list will grow and grow as I keep adding
                            // in new prompts until I get sick of it and
                            // come up with a more efficient way to do this.
                            editor.putBoolean(GHDConstants.PREF_STOP_BUGGING_ME_PREFETCH_WARNING, false);
                            editor.putBoolean(GHDConstants.PREF_STOP_BUGGING_ME_KNOWN_NOTIFICATION_LIMIT, false);
                            editor.apply();

                            Toast.makeText(
                                    getActivity(),
                                    R.string.toast_reset_bugging_me_success,
                                    Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.dialog_reset_bugging_me_no, (dialog, which) -> dismiss())
                        .create();
            }
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_other);

            bindPreferenceSummaryToValue(findPreference(GHDConstants.PREF_STOCK_CACHE_SIZE));

            // The stock alarm preference needs to enable/disable the alarm as
            // need be.
            findPreference(GHDConstants.PREF_STOCK_ALARM).setOnPreferenceChangeListener((preference, newValue) -> {
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

                    AlarmService.enqueueWork(getActivity(), i);
                }

                return true;
            });

            // Cache wiping is more a button than a preference, per se.
            findPreference("_stockWipe").setOnPreferenceClickListener(preference -> {
                DialogFragment frag = new WipeCacheDialogFragment();
                frag.show(getFragmentManager(), WIPE_DIALOG);
                return true;
            });

            // As is the reminder unremindening.
            findPreference("_resetBuggingMe").setOnPreferenceClickListener(preference -> {
                DialogFragment frag = new ResetBuggingMeDialogFragment();
                frag.show(getFragmentManager(), RESET_BUGGING_ME_DIALOG);
                return true;
            });
        }

        @Override
        public void onStop() {
            BackupManager bm = new BackupManager(getActivity());
            bm.dataChanged();

            super.onStop();
        }
    }

    /**
     * These preferences aren't real, and should only exist on the logcat
     * branch.
     */
    public static class DebugFragment extends PreferenceFragment {
        private static final String DEBUG_TAG = "DebugFragment";

        private Preference.OnPreferenceClickListener _fillClicker = new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Our plan is to just flood the graticule-sized area with an
                // 11x11 grid of 10km Known Locations, separated by .1 degrees
                // (that roughly kinda works).  So, first let's get the base
                // coordinates.
                double baseLat, baseLon;
                switch(preference.getKey()) {
                    case "_debug_non30w":
                        // Non-30W... how about the Lexington, KY graticule?
                        // If you don't like this, too bad, I lived there long
                        // enough, so I'm choosing it.  Start at -85 and work
                        // your way RIGHT to make the 38N 84E graticule.
                        baseLat = 38;
                        baseLon = -85;
                        break;
                    case "_debug_30w":
                        // A 30W graticule... let's go with Bathurst, Australia,
                        // as that's the graticule in question for the bug I was
                        // chasing down when I made this.  Start at -34 and work
                        // your way UP to make the 33S 149W graticule.
                        baseLat = -34;
                        baseLon = 149;
                        break;
                    case "_debug_meridian":
                        // The Prime Meridian isn't really a graticule, per se.
                        // In order to wrap around it for testing purposes, we
                        // need to put it in half-graticule portions.  But let's
                        // make it around London anyway.
                        baseLat = 51;
                        baseLon = -0.5;
                        break;
                    default:
                        Log.e(DEBUG_TAG, preference.getKey() + " isn't a valid debug location filler!");
                        return true;
                }

                // This is debug-land, so to determine if we've already filled a
                // graticule, we'll just see if the top-corner has a location.
                List<KnownLocation> locations = KnownLocation.getAllKnownLocations(getActivity());

                // Unfortunately, the locations aren't organized at all.  Oops.
                for(KnownLocation kl : locations) {
                    LatLng loc = kl.getLatLng();
                    if(loc.latitude == baseLat && loc.longitude == baseLon && kl.getName().startsWith("Debug"))
                    {
                        Toast.makeText(
                                getActivity(),
                                R.string.pref_debug_locations_exist,
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }

                // If not, it's time to build locations!  121 of them!
                for(double lat = baseLat; lat <= baseLat + 1; lat += .1) {
                    for(double lon = baseLon; lon <= baseLon + 1; lon += .1) {
                        KnownLocation kl = new KnownLocation("Debug " + (Math.round(lat * 10.0) / 10.0) + ", " + (Math.round(lon * 10.0) / 10.0) + " location", new LatLng(lat, lon), 10000, false);
                        locations.add(kl);
                    }
                }

                // Built!  Toss 'em in!
                KnownLocation.storeKnownLocations(getActivity(), locations);

                Toast.makeText(
                        getActivity(),
                        R.string.pref_debug_locations_added,
                        Toast.LENGTH_SHORT).show();

                return true;
            }
        };

        private Preference.OnPreferenceClickListener _fillClickerBigger = new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                // The bigger-area filler is meant for testing the multiple
                // notifications feature, so we have to make a few adjustments
                // to the other filler.  For starters, we need... oh, let's say
                // 25 graticules filled with 121 Known Locations each.  And
                // we'll put them in the American southwest, centered on the
                // graticule for Las Vegas (36N 115W).
                int baseLat = 36;
                int baseLon = -115;

                List<KnownLocation> locations = KnownLocation.getAllKnownLocations(getActivity());

                // Again, make sure the locations don't already exist, and
                // again, do a cheap check.  Unfortunately, we can't check these
                // by actual latitude or longitude due to the random sway...
                for(KnownLocation kl : locations) {
                    LatLng loc = kl.getLatLng();
                    if(Math.round(loc.latitude) == baseLat && Math.round(loc.longitude) == baseLon && kl.getName().startsWith("Debug"))
                    {
                        Toast.makeText(
                                getActivity(),
                                R.string.pref_debug_locations_exist,
                                Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }

                for(int i = -2; i <= 2; i++) {
                    // Latitude loop.
                    for(int j = -2; j <= 2; j++) {
                        // Longitude loop.
                        for(double lat = baseLat + i; lat <= baseLat + i + 1; lat += .1) {
                            // Known Location latitude loop.
                            for(double lon = baseLon + j; lon <= baseLon + j + 1; lon += .1) {
                                // Known Location longitude loop.  This one
                                // actually does something.

                                // This is where we differ from the other filler
                                // slightly.  We can't have a uniform grid of
                                // Known Locations, as that'll make it hard to
                                // sort them by what's closest per graticule.
                                // So in this case, we'll add a slight bit of
                                // randomization to the coordinates.  Like, say,
                                // +/- 0.01 degrees.
                                double randLat = lat + (Math.random() * 0.01) - 0.005;
                                double randLon = lon + (Math.random() * 0.01) - 0.005;

                                KnownLocation kl = new KnownLocation("Debug " + (Math.round(lat * 10.0) / 10.0) + ", " + (Math.round(lon * 10.0) / 10.0) + " location (with random sway)", new LatLng(randLat, randLon), 10000, false);
                                locations.add(kl);
                            }
                        }
                    }
                }

                // That's it!  Go!
                KnownLocation.storeKnownLocations(getActivity(), locations);

                Toast.makeText(
                        getActivity(),
                        R.string.pref_debug_locations_added,
                        Toast.LENGTH_SHORT).show();

                return true;
            }
        };

        private Preference.OnPreferenceClickListener _wipeClicker = new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Look, if you wanted to keep your locations, maybe you should
                // come up with a better name than "Debug" if you're using the
                // logcat branch.
                List<KnownLocation> locations = KnownLocation.getAllKnownLocations(getActivity());
                List<KnownLocation> toDelete = new LinkedList<>();

                for(KnownLocation kl : locations) {
                    if(kl.getName().startsWith("Debug"))
                        toDelete.add(kl);
                }

                for(KnownLocation kl : toDelete) {
                    locations.remove(kl);
                }

                KnownLocation.storeKnownLocations(getActivity(), locations);

                Toast.makeText(
                        getActivity(),
                        R.string.pref_debug_locations_wiped,
                        Toast.LENGTH_SHORT).show();

                return true;
            }
        };

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_debug);

            // Seriously, none of these preferences are real.  They're all more
            // of things you poke to make things happen.
            findPreference("_debug_non30w").setOnPreferenceClickListener(_fillClicker);
            findPreference("_debug_30w").setOnPreferenceClickListener(_fillClicker);
            findPreference("_debug_meridian").setOnPreferenceClickListener(_fillClicker);
            findPreference("_debug_many").setOnPreferenceClickListener(_fillClickerBigger);

            findPreference("_debug_wipeLocations").setOnPreferenceClickListener(_wipeClicker);

            findPreference("_debug_stockAlarm").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    // PARTY ALARM!!!
                    Intent i = new Intent("net.exclaimindustries.geohashdroid.STOCK_ALARM");
                    i.setClass(getActivity(), AlarmService.class);
                    AlarmService.enqueueWork(getActivity(), i);

                    Toast.makeText(
                            getActivity(),
                            R.string.pref_debug_stock_alarm,
                            Toast.LENGTH_SHORT).show();

                    return true;
                }
            });
        }
    }
}
