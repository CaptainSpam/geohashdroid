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
    /**
     * Bundle key for a starter Graticule.  Note that the Graticule should be
     * restored automatically in the case of a state change.
     */
    public final static String GRATICULE = "starterGraticule";
    /**
     * Bundle key for the globalhash checkbox.  Including both this and the
     * Graticule key is a valid action; it'll just mean the input boxes are
     * pre-filled and disabled.
     */
    public final static String GLOBALHASH = "globalHash";

    private EditText mLat;
    private EditText mLon;
    private CheckBox mGlobal;
    private Button mClosest;

    private boolean mExternalUpdate;

    private GraticulePickerListener mListener;

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
        void updateGraticule(@Nullable Graticule g);

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
        mClosest = (Button) v.findViewById(R.id.grat_closest);
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
        mClosest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);
                if(mListener != null)
                    mListener.findClosest();
            }
        });

        // The close button needs to, well, close.
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mListener != null)
                    mListener.graticulePickerClosing();
            }
        });

        // That said, we need some default values.
        if(savedInstanceState == null) {
            // If we're NOT coming back from a saved instance, check the
            // arguments.
            Bundle args = getArguments();

            Graticule g = args.getParcelable(GRATICULE);
            boolean global = args.getBoolean(GLOBALHASH, false);

            if(g != null) {
                mLat.setText(g.getLatitudeString(true));
                mLon.setText(g.getLongitudeString(true));
            }

            mGlobal.setChecked(global);
        }

        return v;
    }

    private void dispatchGraticule() {
        Graticule toSend;

        try {
            toSend = buildGraticule();
        } catch (Exception e) {
            // If an exception is thrown, we don't have valid input.
            return;
        }

        // If we got here, we can send it on its merry way!
        if(mListener != null)
            mListener.updateGraticule(toSend);
    }

    private Graticule buildGraticule() throws NullPointerException, NumberFormatException {
        // First, read the inputs.
        if(mGlobal.isChecked()) {
            // A checked globalhash means we always send a null Graticule, no
            // matter what the inputs say, even if those inputs are invalid.
            return null;
        } else {
            // Otherwise, make a Graticule.  The constructor will throw as need
            // be.
            return new Graticule(mLat.getText().toString(), mLon.getText().toString());
        }
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
            mGlobal.setChecked(false);
            mLat.setText(g.getLatitudeString(true));
            mLon.setText(g.getLongitudeString(true));
        }

        // And we're done, so unset the flag.
        mExternalUpdate = false;

        // NOW we can dispatch the change.
        dispatchGraticule();
    }

    /**
     * Sets the {@link GraticulePickerListener}.  If this is either null or
     * never called, this whole Fragment won't do much.
     *
     * @param listener the new listener
     */
    public void setListener(GraticulePickerListener listener) {
        mListener = listener;

        dispatchGraticule();
    }

    /**
     * Gets the currently-input Graticule.  This will return null if there's no
     * valid input yet OR if the Globalhash checkbox is ticked, so make sure to
     * also check {@link #isGlobalhash()}.
     *
     * @return the current Graticule, or null
     */
    public Graticule getGraticule() {
        try {
            return buildGraticule();
        } catch(Exception e) {
            return null;
        }
    }

    /**
     * Gets whether or not Globalhash is ticked.  Note that if this is false, it
     * doesn't necessarily mean there's a valid Graticule in the inputs.
     *
     * @return true if global, false if not
     */
    public boolean isGlobalhash() {
        return mGlobal.isChecked();
    }

    /**
     * Resets the Find Closest button after it's been triggered.  It'll be
     * disabled otherwise.
     */
    public void resetFindClosest() {
        mClosest.setEnabled(true);
    }
}
