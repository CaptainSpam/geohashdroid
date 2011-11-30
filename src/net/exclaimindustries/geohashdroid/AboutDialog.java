/**
 * AboutDialog.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Message;
import android.view.LayoutInflater;

/**
 * The <code>AboutDialog</code> is sort of what it says it is.
 * 
 * @author Nicholas Killewald
 */
public class AboutDialog extends AlertDialog {

    public AboutDialog(Context context) {
        super(context);
        create();
    }

    private void create() {
        // We can't use setContextView on this (I don't think), so we need to
        // inflate the layout ourselves.
        LayoutInflater inflater = (LayoutInflater)getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        setView(inflater.inflate(R.layout.about, null));
        setTitle(R.string.menu_item_about);
        setIcon(android.R.drawable.ic_dialog_info);
        setButton(getContext().getResources().getString(R.string.ok_label),
                (Message)null);
    }
}
