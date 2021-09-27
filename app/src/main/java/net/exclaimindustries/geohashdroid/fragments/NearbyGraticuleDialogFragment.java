/*
 * NearbyGraticuleChangeDialog.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.util.UnitConverter;
import net.exclaimindustries.tools.LocationUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

/**
 * This handy Fragment is the dialog that pops up when the user wants to change
 * to a different Graticule by tapping one of the nearby ones on the map.
 */
public class NearbyGraticuleDialogFragment extends DialogFragment {
    private static final String DEBUG_TAG = "NearbyGraticuleDialog";

    /**
     * Interface that the containing Activity must implement if it's expecting
     * to get any data out of this.
     */
    public interface NearbyGraticuleClickedCallback {
        /**
         * Called when the user has accepted the new Graticule.
         *
         * @param info Info of the new hashpoint
         */
        void nearbyGraticuleClicked(Info info);
    }

    private NearbyGraticuleClickedCallback mCallback;

    /**
     * Sets the callback.  If this isn't set or is null, the dialog will sort of
     * just disappear without telling anyone and logcat will yell at you.
     *
     * @param callback the new callback
     */
    public void setCallback(NearbyGraticuleClickedCallback callback) {
        mCallback = callback;
    }

    /**
     * Generates a new NearbyGraticuleDialogFragment, suitable for use as
     * a DialogFragment.
     *
     * @param info the Info that this dialog will concern itself with
     * @param location the user's current Location (can be null)
     * @return a dialog
     */
    public static NearbyGraticuleDialogFragment newInstance(@NonNull Info info, @Nullable Location location) {
        NearbyGraticuleDialogFragment frag = new NearbyGraticuleDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable("info", info);
        args.putParcelable("location", location);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arguments = getArguments();
        assert arguments != null;
        final Info info = arguments.getParcelable("info");
        final Location location = getArguments().getParcelable("location");

        if(info == null) throw new RuntimeException("Passed a null Info into NearbyGraticuleDialogFragment!");

        String message;
        FragmentActivity act = getActivity();
        assert act != null;
        if(LocationUtil.isLocationNewEnough(location)) {
            message = getString(R.string.dialog_switch_graticule_text,
                    UnitConverter.makeDistanceString(act,
                            UnitConverter.DISTANCE_FORMAT_SHORT,
                            location.distanceTo(info.getFinalLocation())));
        } else {
            message = getString(R.string.dialog_switch_graticule_unknown);
        }

        // Fortunately, we've got GHDBasicDialogBuilder on-hand for just such
        // basic dialog purposes!
        Graticule g = info.getGraticule();
        if(g == null) throw new RuntimeException("Passed a null Graticule into NearbyGraticuleDialogFragment!  You can't use this on a Globalhash!");

        return new AlertDialog.Builder(getActivity())
                .setMessage(message)
                .setTitle(g.getTitleString(false))
                .setPositiveButton(getString(R.string.dialog_switch_graticule_okay), (dialog, which) -> {
                    // Well, you heard the orders!
                    dismiss();
                    if(mCallback != null)
                        mCallback.nearbyGraticuleClicked(info);
                    else
                        Log.e(DEBUG_TAG, "You didn't specify a callback!");
                })
                .setNegativeButton(getString(R.string.dialog_switch_graticule_cancel), (dialog, which) -> dismiss())
                .create();
    }
}
