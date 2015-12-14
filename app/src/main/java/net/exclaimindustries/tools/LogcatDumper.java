/*
 * LogcatDumper.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.tools;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import net.exclaimindustries.geohashdroid.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * This is a simple helper script to dump out a logcat and immediately share it.
 */
public class LogcatDumper {
    public static void shareLogcat(Context context) {
        StringBuilder log = new StringBuilder();

        // Now here's a trick I picked up online somewhere...
        try {
            Process process = Runtime.getRuntime().exec("logcat -d");
            BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                log.append(line).append("\n");
            }
        }
        catch (IOException e) {
            // We're cool with this.  The length check will catch if
            // there's a real issue.
        }

        if(log.length() <= 0) {
            Toast.makeText(context, R.string.logcat_dump_error, Toast.LENGTH_LONG).show();
        } else {
            // Now, share that.  Let Android take care of it.
            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.logcat_dump_title));
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, log.toString());
            context.startActivity(Intent.createChooser(sharingIntent, context.getString(R.string.logcat_dump_title)));
        }

    }
}
