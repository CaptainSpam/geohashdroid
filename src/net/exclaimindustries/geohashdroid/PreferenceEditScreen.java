/**
 * PreferenceEditScreen.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

/**
 * So-called because just about any other sensible name for this is already
 * taken by the normal Android classes or would be easily confused
 * (PreferenceScreen, PreferenceWindow, PreferenceActivity...).
 * 
 * @author Nicholas Killewald
 */
public class PreferenceEditScreen extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager manager = getPreferenceManager();

        manager.setSharedPreferencesName(MainMenu.PREFS_BASE);

        addPreferencesFromResource(R.xml.prefs);

    }

}
