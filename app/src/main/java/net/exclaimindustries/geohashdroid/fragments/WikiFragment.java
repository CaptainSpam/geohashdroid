/*
 * WikiFragment.java
 * Copyright (C) 2015 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.services.WikiService;
import net.exclaimindustries.geohashdroid.util.GHDConstants;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.util.UnitConverter;
import net.exclaimindustries.geohashdroid.wiki.WikiImageUtils;
import net.exclaimindustries.geohashdroid.wiki.WikiUtils;
import net.exclaimindustries.tools.BitmapTools;
import net.exclaimindustries.tools.LocationUtil;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Calendar;

/**
 * <code>WikiFragment</code> handles any wiki-related UI (besides the username
 * and password inputs).  It takes text, asks for pictures, and sends all the
 * data off to {@link WikiService} when it's ready to go.
 */
public class WikiFragment extends CentralMapExtraFragment {
    private static final String PICTURE_URI = "pictureUri";
    private static final String IMAGE_ROTATION = "imageRotation";
    private static final String IMAGE_FLIP_X = "imageFlipX";
    private static final String IMAGE_FLIP_Y = "imageFlipY";

    private static final int GET_PICTURE = 1;

    private View mAnonWarning;
    private ImageButton mGalleryButton;
    private TextView mPictureLocationLabel;
    private TextView mPictureLocationText;
    private CheckBox mPictureCheckbox;
    private CheckBox mIncludeLocationCheckbox;
    private TextView mLocationView;
    private TextView mDistanceView;
    private EditText mMessage;
    private Button mPostButton;
    private TextView mHeader;
    private RadioGroup mLocationTypeGroup;
    private RadioButton mUsePictureLocationButton;
    private RadioButton mUseDeviceLocationButton;
    private RadioButton mUseNoLocationButton;

    private Location mLastLocation = null;
    private WikiImageUtils.ImageInfo mLastImageInfo = null;

    private Uri mPictureUri;
    private Bitmap mThumbnail = null;

    private final BitmapTools.ImageEdits mImageEdits =
            new BitmapTools.ImageEdits(BitmapTools.ImageRotation.ROTATE_0, false, false);

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener = (sharedPreferences, key) -> {
        // Huh, we register for ALL changes, not just for a few prefs.  May
        // as well narrow it down...
        if(key.equals(GHDConstants.PREF_WIKI_USER) || key.equals(GHDConstants.PREF_WIKI_PASS)) {
            checkAnonStatus();
        }
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.wiki, container, false);

        // Views!
        mAnonWarning = layout.findViewById(R.id.wiki_anon_warning);
        mPictureCheckbox = layout.findViewById(R.id.wiki_check_include_picture);
        mPictureLocationLabel = layout.findViewById(R.id.wiki_picture_location_label);
        mPictureLocationText = layout.findViewById(R.id.wiki_picture_location);
        mIncludeLocationCheckbox = layout.findViewById(R.id.wiki_check_include_location);
        mGalleryButton = layout.findViewById(R.id.wiki_thumbnail);
        mPostButton = layout.findViewById(R.id.wiki_post_button);
        mMessage = layout.findViewById(R.id.wiki_message);
        mLocationView = layout.findViewById(R.id.wiki_current_location);
        mDistanceView = layout.findViewById(R.id.wiki_distance);
        mHeader = layout.findViewById(R.id.wiki_header);
        mLocationTypeGroup = layout.findViewById(R.id.wiki_location_type_group);
        mUsePictureLocationButton = layout.findViewById(R.id.wiki_use_picture_location);
        mUseDeviceLocationButton = layout.findViewById(R.id.wiki_use_device_location);
        mUseNoLocationButton = layout.findViewById(R.id.wiki_use_no_location);

        // The picture checkbox determines if the other boxes are visible or
        // not.
        mPictureCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> resolvePictureControlVisibility());

        // The include location checkbox determines if the picture location
        // options are enabled.
        mIncludeLocationCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> resolveLocationTypeSelection());

        // The gallery button needs to fire off to the gallery.  Or Files.  Or
        // whatever else might be listening for this intent.
        mGalleryButton.setOnClickListener(v -> {
            Intent i;

            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                i = new Intent(Intent.ACTION_GET_CONTENT);
            } else {
                i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            }

            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");

            startActivityForResult(i, GET_PICTURE);
        });

        // Any time the user edits the text, we also check to re-enable the post
        // button.
        mMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                // Ah, there we go.
                resolvePostButtonEnabledness();
            }
        });

        // The header goes to the current wiki page.
        mHeader.setOnClickListener(v -> {
            if(mInfo != null) {
                Intent i = new Intent();
                i.setAction(Intent.ACTION_VIEW);
                i.setData(Uri.parse(WikiUtils.getWikiBaseViewUrl() + WikiUtils.getWikiPageName(mInfo)));
                startActivity(i);
            }
        });

        // Here's the main event.
        mPostButton.setOnClickListener(v -> dispatchPost());

        // Make sure the header gets set here, too.
        applyHeader();

        // If we had a leftover Uri, apply that as well.
        if(savedInstanceState != null) {
            mImageEdits.rotation = BitmapTools.ImageRotation.values()[savedInstanceState.getInt(IMAGE_ROTATION, 0)];
            mImageEdits.flipX = savedInstanceState.getBoolean(IMAGE_FLIP_X, false);
            mImageEdits.flipY = savedInstanceState.getBoolean(IMAGE_FLIP_Y, false);

            Uri pic = savedInstanceState.getParcelable(PICTURE_URI);
            if(pic != null) setImageUri(pic);
        } else {
            // setImageUri will call resolvePostButtonEnabledness, but since we
            // don't want to pass a null to the former, we'll call the latter if
            // we got a null.
            resolveLocationTypeSelection();
            resolvePostButtonEnabledness();
        }

        updateCheckbox();

        return layout;
    }

    @Override
    public void onResume() {
        super.onResume();

        // We do the anon checks on resume, since it's possible that the user
        // came back from preferences and the anon states have changed.
        checkAnonStatus();

        // Plus, resubscribe for those changes.
        PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(mPrefListener);

        // Update the location, too.  This also makes the location fields
        // invisible if permissions aren't granted yet.  permissionsDenied()
        // will cover if they suddenly became available.
        updateLocation();
    }

    @Override
    public void onPause() {
        // Stop listening for changes.  We'll redo anon checks on resume anyway.
        PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(mPrefListener);

        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // We've also got a picture URI to deal with.
        outState.putParcelable(PICTURE_URI, mPictureUri);

        // And the current image edits.
        outState.putInt(IMAGE_ROTATION, mImageEdits.rotation.ordinal());
        outState.putBoolean(IMAGE_FLIP_X, mImageEdits.flipX);
        outState.putBoolean(IMAGE_FLIP_Y, mImageEdits.flipY);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == GET_PICTURE) {
            if(data != null) {
                // Picture in!  We need to stash the URL away and make a
                // thumbnail out of it, if we can!
                Uri uri = data.getData();

                if(uri == null)
                    return;

                resetImageEdits();
                setImageUri(uri);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Nullable
    private Bitmap createThumbnail(@NonNull Uri uri) {
        Activity a = getActivity();

        // Grab a new Bitmap.
        int dimen = getResources().getDimensionPixelSize(R.dimen.wiki_nominal_icon_size);
        final Bitmap thumbnail = BitmapTools
                .createRatioPreservedDownscaledBitmapFromUri(
                        a,
                        uri,
                        dimen,
                        dimen,
                        true
                );

        if(thumbnail == null) {
            return null;
        }

        // Rotate as need be.
        ExifInterface exif = null;
        try {
            InputStream input = a.getContentResolver().openInputStream(uri);
            if(input != null) {
                exif = new ExifInterface(input);
            }
        } catch(IOException ioe) {
            // This can happen, the error is handled right up next.
        }

        if(exif == null) {
            // If there's no EXIF data for whatever reason, just accept the
            // plain thumbnail, unchanged.
            return thumbnail;
        }

        Bitmap toReturn = BitmapTools.createReorientedBitmap(
                thumbnail,
                exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL));
        thumbnail.recycle();
        return toReturn;
    }

    private void updateThumbnailIcon() {
        Activity a = getActivity();

        // The plain mThumbnail will remain as the original image (with EXIF
        // orientation changes applied).  We just apply edits to it as need be.
        a.runOnUiThread(() -> mGalleryButton
                .setImageBitmap(BitmapTools.createEditedBitmap(mThumbnail, mImageEdits)));
    }

    private void setImageUri(@NonNull Uri uri) {
        Activity a = getActivity();

        // Make us a thumbnail!
        mThumbnail = createThumbnail(uri);
        updateThumbnailIcon();

        // We want vital info for later.  If there's no location, just give back
        // an ImageInfo with a null in it.  We'll know what to do with it when
        // the time comes.
        mLastImageInfo = WikiImageUtils.readImageInfo(a,
                uri,
                null,
                Calendar.getInstance()
        );

        // And remember it for posting later.  Done!
        mPictureUri = uri;

        resolvePostButtonEnabledness();

        // It's a new image, so reset the location selection.
        mLocationTypeGroup.clearCheck();
        resolveLocationTypeSelection();
        resolvePictureLocationText();
    }

    private void resetImageEdits() {
        mImageEdits.rotation = BitmapTools.ImageRotation.ROTATE_0;
        mImageEdits.flipX = false;
        mImageEdits.flipY = false;
    }

    @Override
    public void setInfo(Info info) {
        super.setInfo(info);

        applyHeader();
    }

    private void checkAnonStatus() {
        getActivity().runOnUiThread(() -> {
            // A user is anonymous if they either have no username or no
            // password (the wiki doesn't allow passwordless users, which
            // would just be silly anyway).
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

            String username = prefs.getString(GHDConstants.PREF_WIKI_USER, "");
            String password = prefs.getString(GHDConstants.PREF_WIKI_PASS, "");

            if(username == null
                    || username.isEmpty()
                    || password == null
                    || password.isEmpty()) {
                // If anything isn't defined, we can't set a picture.  Also,
                // uncheck the picture checkbox just to make sure.
                mPictureCheckbox.setChecked(false);
                mPictureCheckbox.setVisibility(View.GONE);
                mGalleryButton.setVisibility(View.GONE);
                mAnonWarning.setVisibility(View.VISIBLE);
            } else {
                // Now, we can't just turn everything back on without
                // checking.  But we CAN get rid of the anon warning and
                // bring back the picture checkbox.
                mAnonWarning.setVisibility(View.GONE);
                mPictureCheckbox.setVisibility(View.VISIBLE);
            }

            // Now, make sure everything else is up to date, including the
            // text on the post button.  This will do some redundant checks
            // in the case of hiding things, but meh.
            resolvePictureControlVisibility();
        });
    }

    private void resolvePictureControlVisibility() {
        // One checkbox to rule them all!
        getActivity().runOnUiThread(() -> {
            if(mPictureCheckbox.isChecked()) {
                mGalleryButton.setVisibility(View.VISIBLE);
                mLocationTypeGroup.setVisibility(View.VISIBLE);

                // Oh, and update a few strings, too.
                mPostButton.setText(R.string.wiki_dialog_submit_picture);
                mIncludeLocationCheckbox.setText(R.string.wiki_dialog_stamp_image);
                mMessage.setHint(R.string.hint_caption);
            } else {
                mGalleryButton.setVisibility(View.GONE);
                mLocationTypeGroup.setVisibility(View.GONE);
                mPostButton.setText(R.string.wiki_dialog_submit_message);
                mIncludeLocationCheckbox.setText(R.string.wiki_dialog_append_coordinates);
                mMessage.setHint(R.string.hint_message);
            }

            // This also changes the post button's enabledness.
            resolvePostButtonEnabledness();

            // And the location type selector's options.
            resolveLocationTypeSelection();

            // And the visibility of the picture's location text.
            resolvePictureLocationText();
        });
    }

    private void resolveLocationTypeSelection() {
        getActivity().runOnUiThread(() -> {
            @IdRes int selection = mLocationTypeGroup.getCheckedRadioButtonId();

            if(mIncludeLocationCheckbox.getVisibility() != View.VISIBLE
                    || !mIncludeLocationCheckbox.isChecked()) {
                // If the user isn't including a location at all, disable the
                // radios.  Keep the text and selection, though, if plausible.
                mUsePictureLocationButton.setEnabled(false);
                mUseDeviceLocationButton.setEnabled(false);
                mUseNoLocationButton.setEnabled(false);
            } else if(mLastImageInfo == null) {
                // If there's no image at all, all the buttons get disabled.
                mUsePictureLocationButton.setEnabled(false);
                mUseDeviceLocationButton.setEnabled(false);
                mUseNoLocationButton.setEnabled(false);
                mUsePictureLocationButton.setText(R.string.wiki_dialog_location_from_picture);
                mLocationTypeGroup.clearCheck();
            } else if(mLastImageInfo.location != null) {
                // If there's an image with a valid location, the from-picture
                // button gets enabled.
                mUsePictureLocationButton.setEnabled(true);
                mUseDeviceLocationButton.setEnabled(true);
                mUseNoLocationButton.setEnabled(true);
                mUsePictureLocationButton.setText(R.string.wiki_dialog_location_from_picture);

                // Default to picture location.
                if(selection == -1) {
                    mLocationTypeGroup.check(R.id.wiki_use_picture_location);
                }
            } else {
                // If not, it gets disabled.
                mUsePictureLocationButton.setEnabled(false);
                mUseDeviceLocationButton.setEnabled(true);
                mUseNoLocationButton.setEnabled(true);
                mUsePictureLocationButton.setText(R.string.wiki_dialog_location_picture_has_none);

                // Default to device location.
                if(selection == -1 || selection == R.id.wiki_use_picture_location) {
                    mLocationTypeGroup.check(R.id.wiki_use_device_location);
                }
            }
        });

    }

    private void resolvePostButtonEnabledness() {
        getActivity().runOnUiThread(() -> {
            // We can make a few booleans here just so the eventual call to
            // setEnabled is easier to read.
            boolean isInPictureMode = mPictureCheckbox.isChecked();
            boolean hasPicture = (mPictureUri != null);
            boolean hasMessage = !(mMessage.getText().toString().isEmpty());

            // So, to review, the button is enabled ONLY if there's a
            // message and, if we're in picture mode, there's a picture to
            // go with it.
            mPostButton.setEnabled(hasMessage && (hasPicture || !isInPictureMode));
        });
    }

    private void resolvePictureLocationText() {
        getActivity().runOnUiThread(() -> {
            if(!mPictureCheckbox.isChecked()) {
                // If we're not posting a picture, remove the picture location
                // stuff.
                mPictureLocationLabel.setVisibility(View.GONE);
                mPictureLocationText.setVisibility(View.GONE);
            } else {
                // Otherwise, turn 'em back on and get a location.
                mPictureLocationLabel.setVisibility(View.VISIBLE);
                mPictureLocationText.setVisibility(View.VISIBLE);

                // But, what text goes on the texty bit?  Well...
                if(mLastImageInfo != null && mLastImageInfo.location != null) {
                    // There's a location, so apply that.  Converted, of course.
                    mPictureLocationText.setText(
                            UnitConverter.makeFullCoordinateString(
                                    getActivity(),
                                    mLastImageInfo.location,
                                    false,
                                    UnitConverter.OUTPUT_SHORT));
                } else {
                    // Otherwise, put the placeholder in place.
                    mPictureLocationText.setText(
                            getString(
                                    R.string.wiki_dialog_location_picture_none));
                }
            }
        });
    }

    private void applyHeader() {
        getActivity().runOnUiThread(() -> {
            if(mInfo == null) {
                mHeader.setText("");
            } else {
                // Make sure it's underlined so it at least LOOKS like a
                // thing someone might click.
                Graticule g = mInfo.getGraticule();

                SpannableString text = new SpannableString(getString(R.string.wiki_dialog_header,
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(mInfo.getCalendar().getTime()),
                        (g == null
                                ? getString(R.string.globalhash_label)
                                : g.getTitleString(false))));
                text.setSpan(new UnderlineSpan(), 0, text.length(), 0);
                mHeader.setText(text);
            }
        });
    }

    private void updateLocation() {
        // If we're not ready yet (or if this isn't a phone layout), don't
        // bother.
        if(mLocationView == null || mDistanceView == null || mInfo == null) return;

        getActivity().runOnUiThread(() -> {
            // Easy enough, this is just the current location data.
            if(mPermissionsDenied) {
                mLocationView.setVisibility(View.INVISIBLE);
                mDistanceView.setVisibility(View.INVISIBLE);
            } else {
                mLocationView.setVisibility(View.VISIBLE);
                mDistanceView.setVisibility(View.VISIBLE);
            }

            if(mLastLocation == null) {
                // Or not, if there's no location.
                mLocationView.setText(R.string.standby_title);
                mDistanceView.setText(R.string.standby_title);
            } else {
                mLocationView.setText(UnitConverter.makeFullCoordinateString(getActivity(), mLastLocation, false, UnitConverter.OUTPUT_SHORT));
                mDistanceView.setText(UnitConverter.makeDistanceString(getActivity(), UnitConverter.DISTANCE_FORMAT_SHORT, mLastLocation.distanceTo(mInfo.getFinalLocation())));
            }
        });
    }

    private void dispatchPost() {
        Context c = getActivity();

        // Time for fun!
        boolean includeLocation = !mPermissionsDenied && mIncludeLocationCheckbox.isChecked();
        boolean includePicture = mPictureCheckbox.isChecked();
        @IdRes int locationSelection = mLocationTypeGroup.getCheckedRadioButtonId();

        // So.  If we didn't have an Info yet, we're hosed.
        if(mInfo == null) {
            Toast.makeText(c, R.string.error_no_data_to_wiki, Toast.LENGTH_LONG).show();
            return;
        }

        // If there's no message, we're hosed.
        if(mMessage.getText().toString().isEmpty()) {
            Toast.makeText(c, R.string.error_no_message, Toast.LENGTH_LONG).show();
            return;
        }

        // If this is a picture post but there's no picture, we're hosed.
        if(includePicture && (mPictureUri == null || mLastImageInfo == null)) {
            Toast.makeText(c, R.string.error_no_picture, Toast.LENGTH_LONG).show();
            return;
        }

        // Otherwise, it's time to send!
        String message = mMessage.getText().toString();

        // The location depends on if there's a picture and what the options on
        // said picture are.  And the checkbox that says whether to include the
        // location in the first place.
        Location loc;
        if(!includeLocation || (includePicture && locationSelection == R.id.wiki_use_no_location)) {
            // Either the user said not to include a location or the user said
            // to draw the infobox without the current location.
            loc = null;
        } else if(!includePicture || locationSelection == R.id.wiki_use_device_location) {
            // The user said to include the location (as per passing the prior
            // clause) and either this isn't a picture post (and thus we have to
            // use the device's location) or the user explicitly said to use the
            // device's location for an infobox.
            loc = mLastLocation;

            // BUT, if the device's location isn't new enough, ignore it.
            if(!LocationUtil.isLocationNewEnough(loc)) loc = null;
        } else {
            // The user said to include the location AND include a picture AND
            // the only other option is to use the picture's location.
            loc = mLastImageInfo.location;
        }

        Intent i = new Intent(c, WikiService.class)
                .putExtra(WikiService.EXTRA_INFO, mInfo)
                .putExtra(WikiService.EXTRA_TIMESTAMP, Calendar.getInstance())
                .putExtra(WikiService.EXTRA_MESSAGE, message)
                .putExtra(WikiService.EXTRA_LOCATION, loc)
                .putExtra(WikiService.EXTRA_INCLUDE_LOCATION, includeLocation);
        if(includePicture) {
            // Now hold on!  Let's ALSO make the uploadable version here, as
            // we're the ones with permission to open the file, NOT WikiService!
            // This is actually a thing.  WikiService won't be on the same
            // Context by the time it uploads, so that'd be a SecurityException.
            byte[] pictureData = WikiImageUtils.createWikiImage(
                    c,
                    mInfo,
                    mLastImageInfo,
                    loc,
                    includeLocation,
                    mImageEdits);

            i.putExtra(WikiService.EXTRA_IMAGE, mPictureUri)
                    .putExtra(WikiService.EXTRA_IMAGE_INFO, mLastImageInfo)
                    .putExtra(WikiService.EXTRA_IMAGE_DATA, pictureData);
        }

        // And away it goes!
        c.startService(i);

        // Post complete!  We're done here!
        if(mCloseListener != null)
            mCloseListener.extraFragmentClosing(this);
    }

    @NonNull
    @Override
    public FragmentType getType() {
        return FragmentType.WIKI;
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        // We'll get this in either from CentralMap or WikiActivity.  In either
        // case, we act the same way.
        mLastLocation = location;
        updateLocation();
    }

    @Override
    public void permissionsDenied(boolean denied) {
        // This comes in from ExpeditionMode if permissions are denied/granted
        // or from WikiActivity during onResume if permissions are granted some
        // other way (WikiActivity won't ask for permission; it'll just assume
        // the current permission state holds).
        mPermissionsDenied = denied;
        updateLocation();

        // Also, remove the Append Location box if permissions were denied.
        updateCheckbox();
    }

    private void updateCheckbox() {
        mIncludeLocationCheckbox.setVisibility(mPermissionsDenied ? View.GONE : View.VISIBLE);
    }
}
