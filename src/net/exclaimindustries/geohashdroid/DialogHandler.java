/**
 * DialogHandler.java
 * Copyright (C)2013 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.util.Log;

/**
 * This is a handy transparent container that will, with any luck, display a
 * popup at some point when the user might be busy elsewhere.
 */
public class DialogHandler extends Activity {
    
    private static final String DEBUG_TAG = "DialogHandler";
    
    static final String DIALOG = "dialog";
    static final int DIALOG_CANCEL_NETWORK = 1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Get the Intent, and by that token, what dialog we display.  Since
        // this should only be called from StockService anyway, we'll just
        // go right ahead and crash if there's no extra there.
        Intent i = getIntent();
        
        int dialog = i.getIntExtra(DIALOG, -1);
        
        showDialog(dialog);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch(id) {
            case DIALOG_CANCEL_NETWORK:
            {
                // Here's a little number I like to call "Are you sure you
                // want to cancel the wait for the network?"...
                AlertDialog.Builder build = new AlertDialog.Builder(this);
                
                build.setTitle(R.string.dialog_abandon_network_title);
                build.setMessage(R.string.dialog_abandon_network_text);
                build.setIcon(android.R.drawable.ic_dialog_alert);
                
                build.setPositiveButton(R.string.dialog_abandon_network_yes,
                        new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                // Clear!
                                dismissDialog(DIALOG_CANCEL_NETWORK);
                                
                                // Send an intent back to the Service to
                                // tell it to give up.
                                Intent i = new Intent(DialogHandler.this, StockService.class);
                                i.setAction(GHDConstants.STOCK_CANCEL_NETWORK);
                                startService(i);
                                
                                // And we're done here!
                                finish();
                            }
                });
                
                build.setNegativeButton(R.string.dialog_abandon_network_no,
                        new OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                // Just dismiss the dialog and stop the
                                // activity.
                                dismissDialog(DIALOG_CANCEL_NETWORK);
                                
                                finish();
                            }
                    
                });
                
                build.setOnCancelListener(new OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {
                        // DISMISSED!
                        finish();
                    }
                    
                });
                
                return build.create();
            }
        }
        
        Log.e(DEBUG_TAG, "What sort of dialog is " + id + " supposed to be?  Honestly!!");
        return null;
    }
    
}