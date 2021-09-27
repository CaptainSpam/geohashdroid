/*
 * DetailedInfoFragment.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.util.UnitConverter;

import java.text.DateFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

/**
 * The DetailedInfoFragment shows us some detailed info.  It's Javadocs like
 * this that really sell the whole concept, I know.
 */
public class DetailedInfoFragment extends CentralMapExtraFragment {
    private TextView mDate;
    private TextView mYouLat;
    private TextView mYouLon;
    private TextView mDestLat;
    private TextView mDestLon;
    private TextView mDistance;
    private TextView mAccuracy;
    private View mYouBlock;
    private View mDistanceBlock;

    private Location mLastLocation;

    private ClipboardManager mClipManager;

    private final View.OnLongClickListener mYouListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            FragmentActivity act = getActivity();
            assert act != null;

            // Only allow this if we actually HAVE data.
            if(mLastLocation == null) {
                Toast.makeText(act, R.string.details_toast_no_location, Toast.LENGTH_SHORT).show();
            } else {
                String clipText = UnitConverter.makeLatitudeCoordinateString(act, mLastLocation.getLatitude(), false, UnitConverter.OUTPUT_DETAILED)
                        + " "
                        + UnitConverter.makeLongitudeCoordinateString(act, mLastLocation.getLongitude(), false, UnitConverter.OUTPUT_DETAILED);
                // Let's see if I know how the clipboard works...
                ClipData clip = ClipData.newPlainText(getString(R.string.details_clip_your_location), clipText);
                mClipManager.setPrimaryClip(clip);

                Toast.makeText(act, R.string.details_toast_your_location, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
    };

    private final View.OnLongClickListener mDestListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            FragmentActivity act = getActivity();
            assert act != null;

            // Same deal, only this time with the final destination.
            if(mInfo == null) {
                Toast.makeText(act, R.string.details_toast_stand_by, Toast.LENGTH_SHORT).show();
            } else {
                String clipText = UnitConverter.makeLatitudeCoordinateString(act, mInfo.getLatitude(), false, UnitConverter.OUTPUT_DETAILED)
                        + " "
                        + UnitConverter.makeLongitudeCoordinateString(act, mInfo.getLongitude(), false, UnitConverter.OUTPUT_DETAILED);
                ClipData clip = ClipData.newPlainText(getString(R.string.details_clip_final_location, DateFormat.getDateInstance(DateFormat.LONG)
                        .format(mInfo.getCalendar().getTime())), clipText);
                mClipManager.setPrimaryClip(clip);

                Toast.makeText(act, R.string.details_toast_final_location, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.detail, container, false);

        // Clipboard!
        FragmentActivity act = getActivity();
        assert act != null;

        mClipManager = (ClipboardManager)act.getSystemService(Context.CLIPBOARD_SERVICE);

        // TextViews!
        mDate = layout.findViewById(R.id.detail_date);
        mYouLat = layout.findViewById(R.id.you_lat);
        mYouLon = layout.findViewById(R.id.you_lon);
        mDestLat = layout.findViewById(R.id.dest_lat);
        mDestLon = layout.findViewById(R.id.dest_lon);
        mDistance = layout.findViewById(R.id.distance);
        mAccuracy = layout.findViewById(R.id.accuracy);
        mYouBlock = layout.findViewById(R.id.you_block);
        mDistanceBlock = layout.findViewById(R.id.distance_block);

        // Long clicks!
        mYouLat.setOnLongClickListener(mYouListener);
        mYouLon.setOnLongClickListener(mYouListener);
        mDestLat.setOnLongClickListener(mDestListener);
        mDestLon.setOnLongClickListener(mDestListener);

        // Button!
        Button closeButton = layout.findViewById(R.id.close);

        // Button does a thing!
        if(closeButton != null) registerCloseButton(closeButton);

        updateDisplay();

        return layout;
    }

    /**
     * Sets the Info.  If null, this will make it go to standby.  Whatever gets
     * set here will override any arguments originally passed in if and when
     * onSaveInstanceState is needed.
     *
     * @param info the new Info
     */
    public void setInfo(@Nullable final Info info) {
        super.setInfo(info);

        updateDisplay();
    }

    private void updateDisplay() {
        // Good!  This is almost the same as the InfoBox.  It just has more
        // detail and such.
        final FragmentActivity activity = getActivity();

        if(activity != null) {
            activity.runOnUiThread(() -> {
                float accuracy = 0.0f;
                if(mLastLocation != null) accuracy = mLastLocation.getAccuracy();

                // If we can't get to the user's current location due to
                // pesky permissions perils, just hide the relevant blocks.
                // I mean, it'll be a somewhat sparse fragment, but it'll at
                // least not have ugly Stand By lines all over.
                if(mPermissionsDenied) {
                    mYouBlock.setVisibility(View.GONE);
                    mDistanceBlock.setVisibility(View.GONE);
                } else {
                    mYouBlock.setVisibility(View.VISIBLE);
                    mDistanceBlock.setVisibility(View.VISIBLE);
                }

                // One by one, just like InfoBox!  I mean, not JUST like it.
                // We split the coordinate parts into different TextViews
                // here, and we have the date to display, but other than
                // THAT...
                if(mInfo == null) {
                    mDestLat.setText(R.string.standby_title);
                    mDestLon.setText("");
                    mDate.setText("");
                } else {
                    mDestLat.setText(UnitConverter.makeLatitudeCoordinateString(activity, mInfo.getFinalLocation().getLatitude(), false, UnitConverter.OUTPUT_DETAILED));
                    mDestLon.setText(UnitConverter.makeLongitudeCoordinateString(activity, mInfo.getFinalLocation().getLongitude(), false, UnitConverter.OUTPUT_DETAILED));
                    mDate.setText(DateFormat.getDateInstance(DateFormat.LONG).format(
                            mInfo.getCalendar().getTime()));
                }

                // Location and accuracy!
                if(mLastLocation == null) {
                    mYouLat.setText(R.string.standby_title);
                    mYouLon.setText("");
                    mAccuracy.setText("");
                } else {
                    mYouLat.setText(UnitConverter.makeLatitudeCoordinateString(activity, mLastLocation.getLatitude(), false, UnitConverter.OUTPUT_DETAILED));
                    mYouLon.setText(UnitConverter.makeLongitudeCoordinateString(activity, mLastLocation.getLongitude(), false, UnitConverter.OUTPUT_DETAILED));

                    mAccuracy.setText(getString(R.string.details_accuracy,
                            UnitConverter.makeDistanceString(activity,
                                    GHDConstants.ACCURACY_FORMAT, mLastLocation.getAccuracy())));
                }

                // Distance!
                SpannableStringBuilder ssb = new SpannableStringBuilder();
                ssb.append(getString(R.string.details_dist)).append(" ");
                int endOfLead = ssb.length();

                if(mLastLocation == null || mInfo == null) {
                    ssb.append(getString(R.string.standby_title));
                } else {
                    float distance = mLastLocation.distanceTo(mInfo.getFinalLocation());
                    String distanceText = UnitConverter.makeDistanceString(
                            activity,
                            GHDConstants.DIST_FORMAT,
                            distance);
                    ssb.append(distanceText);

                    // Plus, if we're close enough AND accurate enough, make the
                    // text be green.  We COULD do this with geofencing
                    // callbacks and all, but, I mean, we're already HERE,
                    // aren't we?
                    if(accuracy < GHDConstants.LOW_ACCURACY_THRESHOLD && distance <= accuracy) {
                        ssb.setSpan(
                                new TextAppearanceSpan(
                                    activity,
                                    R.style.DistanceInRange),
                                endOfLead,
                                endOfLead + distanceText.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    mDistance.setText(ssb);
                }
            });
        }
    }

    @NonNull
    @Override
    public FragmentType getType() {
        return FragmentType.DETAILS;
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        // Ding!
        mLastLocation = location;
        updateDisplay();
    }

    @Override
    public void permissionsDenied(boolean denied) {
        // Dong!
        mPermissionsDenied = denied;
        updateDisplay();
    }
}
