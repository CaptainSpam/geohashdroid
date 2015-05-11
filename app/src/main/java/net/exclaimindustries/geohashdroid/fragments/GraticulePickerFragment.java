/*
 * GraticulePickerFragment.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.Graticule;

/**
 * This is the fragment that appears on the bottom of the map when it enters
 * Select-A-Graticule mode.
 */
public class GraticulePickerFragment
        extends Fragment {
    private EditText mLat;
    private EditText mLon;
    private CheckBox mGlobal;

    private boolean mExternalUpdate;

    /**
     * The interface of choice for when GraticuleInputFragment needs to talk
     * back to something.  Make sure the Activity holding this implements this,
     * else it will explode.
     */
    public interface GraticulePickerListener {
        /**
         * Called when a new Graticule is picked.  This is called EVERY time the
         * user presses a key, so be careful.  If the input is blatantly
         * incomplete (i.e. empty or just a negative sign), this won't be
         * called.
         *
         * @param g the new Graticule (null if it's a globalhash)
         */
        void updateGraticule(Graticule g);

        /**
         * Called when the user presses the "Find Closest" button.  Later on,
         * this Fragment should get setNewGraticule called on it with the
         * results of said search (assuming it can get such a result).
         */
        void findClosest();

        /**
         * Called when the picker is closing.  For now, this just means when
         * the close button is pressed.  In the future, it might also mean if
         * it gets swipe-to-dismiss'd.  Note that this Fragment won't do its own
         * dismissal.  You need to handle that yourself.
         */
        void graticulePickerClosing();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        View v = inflater.inflate(R.layout.graticulepicker, container, false);

        // Here come the widgets.  Each is magical and unique.
        mLat = (EditText)v.findViewById(R.id.grat_lat);
        mLon = (EditText)v.findViewById(R.id.grat_lon);
        mGlobal = (CheckBox)v.findViewById(R.id.grat_globalhash);
        Button closest = (Button) v.findViewById(R.id.grat_closest);
        ImageButton close = (ImageButton)v.findViewById(R.id.close);

        // And how ARE they magical?  Well, like this.  First, any time the
        // boxes are updated, send out a new Graticule to the Activity.
        TextWatcher tw = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Blah.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // BLAH!
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Action!
                if(!mExternalUpdate)
                    dispatchGraticule();
            }
        };

        mLat.addTextChangedListener(tw);
        mLon.addTextChangedListener(tw);

        // Also, when the checkbox gets changed, set/unset Globalhash mode.
        mGlobal.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    // If it's checked, mLat and mLon go disabled, as you can't
                    // set a specific graticule.
                    mLat.setEnabled(false);
                    mLon.setEnabled(false);
                } else {
                    mLat.setEnabled(true);
                    mLon.setEnabled(true);
                }

                if(!mExternalUpdate)
                    dispatchGraticule();
            }
        });

        // Then, the Find Closest button.  That one we foist off on the calling
        // Activity.
        closest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((GraticulePickerListener) getActivity()).findClosest();
            }
        });

        // The close button needs to, well, close.
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((GraticulePickerListener) getActivity()).graticulePickerClosing();
            }
        });

        return v;
    }

    private void dispatchGraticule() {
        Graticule toSend;

        // First, read the inputs.
        if(mGlobal.isChecked()) {
            // A checked globalhash means we always send a null Graticule, no
            // matter what the inputs say, even if those inputs are invalid.
            toSend = null;
        } else {
            // Otherwise, make a Graticule.  The constructor will throw as need
            // be.
            try {
                toSend = new Graticule(mLat.getText().toString(), mLon.getText().toString());
            } catch (Exception e) {
                // If there's any problem, we've got bogus input.
                return;
            }
        }

        // If we got here, we can send it on its merry way!
        ((GraticulePickerListener)getActivity()).updateGraticule(toSend);
    }

    /**
     * Sets a new Graticule for the EditTexts.  This is called when the user
     * taps on the map to pick a new Graticule.
     *
     * @param g the new Graticule
     */
    public void setNewGraticule(Graticule g) {
        // Make sure this flag is set so we don't wind up double-updating.
        mExternalUpdate = true;

        // This should NEVER be a globalhash, as that isn't possible from the
        // map.  But, we're nothing if not defensive here.
        if(g == null) {
            mGlobal.setChecked(true);
        } else {
            // Update text as need be.  Remember, negative zero IS valid!
            mLat.setText(g.getLatitudeString(true));
            mLon.setText(g.getLongitudeString(true));
        }

        // And we're done, so unset the flag.
        mExternalUpdate = false;

        // NOW we can dispatch the change.
        dispatchGraticule();
    }
}
