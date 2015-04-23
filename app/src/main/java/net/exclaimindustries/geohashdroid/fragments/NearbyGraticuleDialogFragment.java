/*
 * NearbyGraticuleChangeDialog.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.UnitConverter;
import net.exclaimindustries.geohashdroid.util.GHDBasicDialogBuilder;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.tools.LocationUtil;

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
        public void nearbyGraticuleClicked(Info info);
    }

    /**
     * Generates a new NearbyGraticuleDialogFragment, suitable for use as
     * a DialogFragment.
     *
     * @param info the Info that this dialog will concern itself with
     * @param location the user's current Location (can be null)
     * @return a dialog
     * @throws java.lang.IllegalArgumentException Info was null
     */
    public static NearbyGraticuleDialogFragment newInstance(Info info, Location location) {
        if(info == null) throw new IllegalArgumentException("You need to pass a non-null Info into newInstance()!");

        NearbyGraticuleDialogFragment frag = new NearbyGraticuleDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable("info", info);
        args.putParcelable("location", location);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Info info = getArguments().getParcelable("info");
        final Location location = getArguments().getParcelable("location");

        String message;
        if(location != null && LocationUtil.isLocationNewEnough(location)) {
            message = getString(R.string.dialog_switch_graticule_text,
                    UnitConverter.makeDistanceString(getActivity(),
                            UnitConverter.DISTANCE_FORMAT_SHORT,
                            location.distanceTo(info.getFinalLocation())));
        } else {
            message = getString(R.string.dialog_switch_graticule_unknown);
        }

        // Fortunately, we've got GHDBasicDialogBuilder on-hand for just such
        // basic dialog purposes!
        return new GHDBasicDialogBuilder(getActivity())
                .setMessage(message)
                .setTitle(info.getGraticule().getLatitudeString(false) + " " + info.getGraticule().getLongitudeString(false))
                .setPositiveButton(getString(R.string.dialog_switch_graticule_okay), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Well, you heard the orders!
                        dismiss();
                        if(getActivity() instanceof NearbyGraticuleClickedCallback)
                            ((NearbyGraticuleClickedCallback)getActivity()).nearbyGraticuleClicked(info);
                        else
                            Log.e(DEBUG_TAG, "This Activity isn't implementing NearbyGraticuleClickedCallback!");
                    }
                })
                .setNegativeButton(getString(R.string.dialog_switch_graticule_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismiss();
                    }
                })
                .create();
    }
}
