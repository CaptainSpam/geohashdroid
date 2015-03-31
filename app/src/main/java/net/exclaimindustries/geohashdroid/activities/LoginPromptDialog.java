/*
 * LoginPromptDialog.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.app.Activity;
import android.os.Bundle;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.services.WikiService;

/**
 * This is a simple dialog prompt that asks for a new username/password combo
 * from the user.  This is summoned from {@link WikiService} any time the wiki
 * reports a login problem.  Once the credentials are updated, it tells the
 * service to kick back in again.
 */
public class LoginPromptDialog extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.logindialog);
    }
}
