/*
 * BaseMapActivity.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.activities;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;

/**
 * This is just a base Activity that holds the permission-checking stuff shared
 * between CentralMap and KnownLocationsPicker.  Hooray for not cutting and
 * pasting code!
 */
public abstract class BaseMapActivity
        extends Activity
        implements GoogleApiClient.ConnectionCallbacks,
                   GoogleApiClient.OnConnectionFailedListener {
    protected GoogleApiClient mGoogleClient;

    private boolean mResolvingError = false;

    /**
     * This is a fragment used to display an error dialog, used by both map
     * activities when they do their crazy permissions requesting stuff.
     */
    public static class ErrorDialogFragment
            extends DialogFragment {

        /** Request code to use when launching the resolution activity. */
        public static final int REQUEST_RESOLVE_ERROR = 1001;

        /** A unique tag for the error dialog fragment. */
        public static final String DIALOG_API_ERROR = "ApiErrorDialog";

        public ErrorDialogFragment() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_API_ERROR);
            return GoogleApiAvailability.getInstance().getErrorDialog(
                    this.getActivity(), errorCode, REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            // Gee, I really hope this only gets called by BaseMapActivity!
            ((BaseMapActivity) getActivity()).onDialogDismissed();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Oh, so THAT'S how the connection can fail: If we're using Marshmallow
        // and the user refused to give permissions to the API or the user
        // doesn't have the Google Play Services installed.  Okay, that's fair.
        // Let's deal with it, then.
        if(!mResolvingError) {
            if(result.hasResolution()) {
                try {
                    mResolvingError = true;
                    result.startResolutionForResult(this, ErrorDialogFragment.REQUEST_RESOLVE_ERROR);
                } catch(IntentSender.SendIntentException e) {
                    // We get this if something went wrong sending the intent.  So,
                    // let's just try to connect again.
                    mGoogleClient.connect();
                }
            } else {
                // If we can't actually resolve this, give up and throw an error.
                // doReadyChecks() won't ever be called.
                showErrorDialog(result.getErrorCode());
                mResolvingError = true;
            }
        }
    }

    private void showErrorDialog(int errorCode) {
        BaseMapActivity.ErrorDialogFragment dialogFragment = new BaseMapActivity.ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(BaseMapActivity.ErrorDialogFragment.DIALOG_API_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getFragmentManager(), "errordialog");
    }

    public void onDialogDismissed() {
        mResolvingError = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BaseMapActivity.ErrorDialogFragment.REQUEST_RESOLVE_ERROR) {
            mResolvingError = false;
            if (resultCode == RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mGoogleClient.isConnecting() &&
                        !mGoogleClient.isConnected()) {
                    mGoogleClient.connect();
                }
            }
        }
    }
}
