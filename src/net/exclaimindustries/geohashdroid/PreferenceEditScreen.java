/**
 * PreferenceEditScreen.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.text.InputType;
import android.widget.Toast;

/**
 * So-called because just about any other sensible name for this is already
 * taken by the normal Android classes or would be easily confused
 * (PreferenceScreen, PreferenceWindow, PreferenceActivity...).
 * 
 * @author Nicholas Killewald
 */
public class PreferenceEditScreen extends PreferenceActivity {
    
    private final static int DIALOG_WIPESURE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager manager = getPreferenceManager();

        manager.setSharedPreferencesName(GHDConstants.PREFS_BASE);

        addPreferencesFromResource(R.xml.prefs);

        // Assign the handler to the stock wiper.  First a popup, and that popup
        // leads back to HashBuilder.
        Preference stockWipe = (Preference)findPreference("_stockWipe");
        stockWipe.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Dialog!  Go!
                showDialog(DIALOG_WIPESURE);
                return true;
            }
            
        });
        
        // Now, assign proper summaries to various preferences.
        initializePrefViews(manager.getSharedPreferences());
    }
    
    @Override
    public Dialog onCreateDialog(int id) {
        switch(id) {
            case DIALOG_WIPESURE:
                // Pop up the "are you sure you want to wipe the stock cache?"
                // dialog.  "Yes" wipes it (and Toasts).  "No" simply dismisses.
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                
                build.setMessage(R.string.pref_stockwipe_dialog_text);
                build.setTitle(R.string.pref_stockwipe_title);
                build.setIcon(android.R.drawable.ic_dialog_alert);
                
                build.setPositiveButton(R.string.dialog_stockwipe_yes,
                        new DialogInterface.OnClickListener() {
    
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                // Clear!
                                PreferenceEditScreen.this.dismissDialog(DIALOG_WIPESURE);
                                
                                if(HashBuilder.deleteCache(PreferenceEditScreen.this)) {
                                    Toast sourdough = Toast.makeText(
                                            PreferenceEditScreen.this,
                                            R.string.toast_stockwipe_success,
                                            Toast.LENGTH_SHORT);
                                    sourdough.show();
                                } else {
                                    Toast sourdough = Toast.makeText(
                                            PreferenceEditScreen.this,
                                            R.string.toast_stockwipe_failure,
                                            Toast.LENGTH_SHORT);
                                    sourdough.show();
                                }
                            }
                        });
                
                build.setNegativeButton(R.string.dialog_stockwipe_no,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                // Just clears the dialog.
                                PreferenceEditScreen.this.dismissDialog(DIALOG_WIPESURE);
                            }
                        });
                
                return build.create();
        }
        
        return null;
    }
    
    private void initializePrefViews(SharedPreferences prefs) {
        // There has GOT to be a cleaner way to do this...
        
        Preference curPref;
        
        // Starting it off with infobox size!
        curPref = (Preference)findPreference(GHDConstants.PREF_INFOBOX_SIZE);
        String set = prefs.getString(GHDConstants.PREF_INFOBOX_SIZE, GHDConstants.PREFVAL_INFOBOX_SMALL);
        if(set.equals(GHDConstants.PREFVAL_INFOBOX_NONE))
            curPref.setSummary(R.string.pref_infobox_off);
        else if(set.equals(GHDConstants.PREFVAL_INFOBOX_SMALL))
            curPref.setSummary(R.string.pref_infobox_small);
        else
            curPref.setSummary(R.string.pref_infobox_jumbo);
        
        curPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                    Object newValue) {
                // newValue better be a String...
                if(newValue instanceof String) {
                    String set = (String)newValue;
                    if(set.equals(GHDConstants.PREFVAL_INFOBOX_NONE))
                        preference.setSummary(R.string.pref_infobox_off);
                    else if(set.equals(GHDConstants.PREFVAL_INFOBOX_SMALL))
                        preference.setSummary(R.string.pref_infobox_small);
                    else
                        preference.setSummary(R.string.pref_infobox_jumbo);
                }
                return true;
            }
            
        });
        
        // Distance units!
        curPref = (Preference)findPreference(GHDConstants.PREF_DIST_UNITS);
        set = prefs.getString(GHDConstants.PREF_DIST_UNITS, GHDConstants.PREFVAL_DIST_METRIC);
        if(set.equals(GHDConstants.PREFVAL_DIST_METRIC))
            curPref.setSummary(R.string.pref_units_metric);
        else
            curPref.setSummary(R.string.pref_units_imperial);
        
        curPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                    Object newValue) {
                // newValue better be a String...
                if(newValue instanceof String) {
                    String set = (String)newValue;
                    if(set.equals(GHDConstants.PREFVAL_DIST_METRIC))
                        preference.setSummary(R.string.pref_units_metric);
                    else
                        preference.setSummary(R.string.pref_units_imperial);
                }
                return true;
            }
            
        });
        
        // Coordinate units!
        curPref = (Preference)findPreference(GHDConstants.PREF_COORD_UNITS);
        set = prefs.getString(GHDConstants.PREF_COORD_UNITS, GHDConstants.PREFVAL_COORD_DEGREES);
        if(set.equals(GHDConstants.PREFVAL_COORD_DEGREES))
            curPref.setSummary(R.string.pref_coordunits_degrees);
        else if(set.equals(GHDConstants.PREFVAL_COORD_MINUTES))
            curPref.setSummary(R.string.pref_coordunits_minutes);
        else
            curPref.setSummary(R.string.pref_coordunits_seconds);
        
        curPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                    Object newValue) {
                // newValue better be a String...
                if(newValue instanceof String) {
                    String set = (String)newValue;
                    if(set.equals(GHDConstants.PREFVAL_COORD_DEGREES))
                        preference.setSummary(R.string.pref_coordunits_degrees);
                    else if(set.equals(GHDConstants.PREFVAL_COORD_MINUTES))
                        preference.setSummary(R.string.pref_coordunits_minutes);
                    else
                        preference.setSummary(R.string.pref_coordunits_seconds);
                }
                return true;
            }
            
        });
        
        // StockService!
        curPref = (Preference)findPreference(GHDConstants.PREF_STOCK_SERVICE);
        // Presumably, the CURRENT status of StockService is valid, so we only
        // need to worry about changing it from here.
        curPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                    Object newValue) {
                if(newValue instanceof Boolean) {
                    Boolean set = (Boolean)newValue;
                    
                    Intent i = new Intent(PreferenceEditScreen.this, StockService.class);
                    
                    if(set.booleanValue()) {
                        // ON!  Start the service!
                        i.setAction(GHDConstants.STOCK_INIT);                        
                    } else {
                        // OFF!  Stop the service and cancel all alarms!
                        i.setAction(GHDConstants.STOCK_CANCEL_ALARMS);
                    }
                    
                    startService(i);
                }
                
                return true;
            }
            
        });
        
        // Cache size!
        curPref = (Preference)findPreference(GHDConstants.PREF_STOCK_CACHE_SIZE);
        set = prefs.getString(GHDConstants.PREF_STOCK_CACHE_SIZE, "0");
        if(set.equals("0"))
            curPref.setSummary(R.string.pref_stockcachesize_off);
        else if(set.equals("10"))
            curPref.setSummary(R.string.pref_stockcachesize_10);
        else if(set.equals("15"))
            curPref.setSummary(R.string.pref_stockcachesize_15);
        else if(set.equals("25"))
            curPref.setSummary(R.string.pref_stockcachesize_25);
        else if(set.equals("50"))
            curPref.setSummary(R.string.pref_stockcachesize_50);
        else
            curPref.setSummary(R.string.pref_stockcachesize_100);
        
        curPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                    Object newValue) {
                // newValue better be a String...
                if(newValue instanceof String) {
                    String set = (String)newValue;
                    if(set.equals("0"))
                        preference.setSummary(R.string.pref_stockcachesize_off);
                    else if(set.equals("10"))
                        preference.setSummary(R.string.pref_stockcachesize_10);
                    else if(set.equals("15"))
                        preference.setSummary(R.string.pref_stockcachesize_15);
                    else if(set.equals("25"))
                        preference.setSummary(R.string.pref_stockcachesize_25);
                    else if(set.equals("50"))
                        preference.setSummary(R.string.pref_stockcachesize_50);
                    else
                        preference.setSummary(R.string.pref_stockcachesize_100);
                }
                return true;
            }
            
        });
        
        // Wiki user name and password!
        curPref = (Preference)findPreference(GHDConstants.PREF_WIKI_USER);
        final EditTextPreference passPref = (EditTextPreference)findPreference(GHDConstants.PREF_WIKI_PASS);
        
        ((EditTextPreference)curPref).getEditText().setHint(R.string.pref_wikiusername_hint);
        ((EditTextPreference)curPref).getEditText().setSingleLine(true);

        passPref.getEditText().setHint(R.string.pref_wikipassword_hint);
        passPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        
        String wikiName = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
        
        // If the user has a name entered, put it in as the summary.  If not,
        // leave it blank.  Oh, and disable the password field if the username
        // isn't entered in.
        if(wikiName.length() != 0) {
            curPref.setSummary(wikiName);
            passPref.setEnabled(true);
        } else
            passPref.setEnabled(false);
        
        curPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

            @Override
            public boolean onPreferenceChange(Preference preference,
                    Object newValue) {
                // newValue better be a String...
                if(newValue instanceof String) {
                    String value = (String)newValue;
                    preference.setSummary(value);
                    
                    if(value.length() == 0)
                        passPref.setEnabled(false);
                    else
                        passPref.setEnabled(true);
                }
                return true;
            }
            
        });
        
        // Wiki password!
        // This one only changes in that it gets disabled if there's no username
        // entered.

    }
}
