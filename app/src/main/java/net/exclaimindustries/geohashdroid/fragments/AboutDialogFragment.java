/*
 * AboutDialogFragment.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import net.exclaimindustries.geohashdroid.R;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

/**
 * Where credits become due and links become available.
 */
public class AboutDialogFragment extends DialogFragment {
    private static final String VERSION_HISTORY_DIALOG = "versionHistory";

    /**
     * Generates a new AboutDialogFragment, suitable for use in a dialog.
     *
     * @return the fragment
     */
    public static AboutDialogFragment newInstance() {
        // Really, all we're doing is returning a new, empty fragment, but we've
        // got two other dialog fragments with a newInstance() interface, so for
        // consistency's sake...
        return new AboutDialogFragment();
    }

    @NonNull
    @Override
    @SuppressLint("InflateParams")
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        FragmentActivity act = getActivity();
        assert act != null;
        View v = act.getLayoutInflater().inflate(R.layout.about, null);

        return new AlertDialog.Builder(getActivity())
                .setView(v)
                .setPositiveButton(R.string.cool_label, (dialog, which) -> dismiss())
                .setNeutralButton(R.string.title_versionhistory, (dialog, which) ->
                    VersionHistoryDialogFragment
                            .newInstance(act)
                            .show(act.getSupportFragmentManager(),
                                    VERSION_HISTORY_DIALOG)
                )
                .create();
    }
}
