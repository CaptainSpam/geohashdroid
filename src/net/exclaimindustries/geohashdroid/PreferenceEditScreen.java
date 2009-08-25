/**
 * PreferenceEditScreen.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
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
                                
                                if(HashBuilder.deleteCache()) {
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

}
