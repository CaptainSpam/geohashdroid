/**
 * CentralMap.java
 * Copyright (C)2015 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.UnitConverter;
import net.exclaimindustries.geohashdroid.fragments.GHDDatePickerDialogFragment;
import net.exclaimindustries.geohashdroid.services.StockService;
import net.exclaimindustries.geohashdroid.util.ExpeditionMode;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import net.exclaimindustries.geohashdroid.util.SelectAGraticuleMode;
import net.exclaimindustries.geohashdroid.widgets.ErrorBanner;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Calendar;

/**
 * CentralMap replaces MainMap as the map display.  Unlike MainMap, it also
 * serves as the entry point for the entire app.  These comments are going to
 * make so much sense later when MainMap is little more than a class that only
 * exists on the legacy branch.
 */
public class CentralMap
        extends Activity
        implements GoogleApiClient.ConnectionCallbacks,
                   GoogleApiClient.OnConnectionFailedListener,
                   GHDDatePickerDialogFragment.GHDDatePickerCallback {
    private static final String DEBUG_TAG = "CentralMap";

    private static final String LAST_MODE_BUNDLE = "lastModeBundle";
    private static final String DATE_PICKER_DIALOG = "datePicker";

    // If we're in Select-A-Graticule mode (as opposed to expedition mode).
    private boolean mSelectAGraticule = false;
    // If we already did the initial zoom for this expedition.
    private boolean mAlreadyDidInitialZoom = false;
    // If the map's ready.
    private boolean mMapIsReady = false;

    private Info mCurrentInfo;
    private GoogleMap mMap;
    private GoogleApiClient mGoogleClient;

    // This is either the current expedition Graticule (same as in mCurrentInfo)
    // or the last-selected Graticule in Select-A-Graticule mode (needed if we
    // need to reconstruct from an onDestroy()).
    private Graticule mLastGraticule;
    private Calendar mLastCalendar;

    // Because a null Graticule is considered to be the Globalhash indicator, we
    // need a boolean to keep track of whether we're actually in a Globalhash or
    // if we just don't have a Graticule yet.
    private boolean mGlobalhash;

    private ErrorBanner mBanner;
    private Bundle mLastModeBundle;
    private CentralMapMode mCurrentMode;

    /**
     * <p>
     * A <code>CentralMapMode</code> is a set of behaviors that happen whenever
     * some corresponding event occurs in {@link CentralMap}.
     * </p>
     *
     * <p>
     * Note the {@link #pause()} and {@link #resume()} methods.  While those
     * correspond to {@link CentralMap}'s onPause and onResume methods, there is
     * NOT a similar lifecycle in <code>CentralMapMode</code>.  That is,
     * {@link #pause()} and {@link #resume()} are NOT guaranteed to be called in
     * any relation to {@link #init(Bundle)} or {@link #cleanUp()}.  If there's
     * never an onPause or onResume in the life of a CentralMapMode, it will NOT
     * receive the corresponding calls, and will instead just get the
     * {@link #init(Bundle)} and {@link #cleanUp()} calls.
     * </p>
     */
    public abstract static class CentralMapMode {
        protected boolean mInitComplete = false;
        private boolean mCleanedUp = false;

        /** Bundle key for the current Graticule. */
        public final static String GRATICULE = "graticule";
        /** Bundle key for the current date, as a Calendar. */
        public final static String CALENDAR = "calendar";
        /**
         * Bundle key for a boolean indicating that, if the Graticule is null,
         * this was actually a Globalhash, not just a request with an empty
         * Graticule.
         */
        public final static String GLOBALHASH = "globalhash";
        /**
         * Bundle key for the current Info.  In cases where this can be given,
         * the Graticule, Calendar, and boolean indicating a Globalhash can be
         * implied from it.
         */
        public final static String INFO = "info";

        /** The current GoogleMap object. */
        protected GoogleMap mMap;
        /** The calling CentralMap Activity. */
        protected CentralMap mCentralMap;

        /** The current destination Marker. */
        protected Marker mDestination;

        /**
         * Sets the {@link GoogleMap} this mode deals with.  When implementing
         * this, make sure to actually do something with it like subscribe to
         * events as the mode needs them if you're not doing so in
         * {@link #init(Bundle)}.
         *
         * @param map that map
         */
        public void setMap(@NonNull GoogleMap map) {
            mMap = map;
        }

        /**
         * Sets the {@link CentralMap} to which this will talk back.
         *
         * @param centralMap that CentralMap
         */
        public void setCentralMap(@NonNull CentralMap centralMap) {
            mCentralMap = centralMap;
        }

        /**
         * Gets the current GoogleApiClient held by CentralMap.  This will
         * return null if the client isn't usable (not connected, null itself,
         * etc).
         *
         * @return the current GoogleApiClient
         */
        @Nullable
        protected final GoogleApiClient getGoogleClient() {
            if(mCentralMap != null) {
                GoogleApiClient gClient = mCentralMap.getGoogleClient();
                if(gClient != null && gClient.isConnected())
                    return gClient;
                else
                    return null;
            } else {
                return null;
            }
        }

        /**
         * <p>
         * Does whatever init tomfoolery is needed for this class, using the
         * given Bundle of stuff.  You're probably best calling this AFTER
         * {@link #setMap(GoogleMap)} and {@link #setCentralMap(CentralMap)} are
         * called and when the GoogleApiClient object is ready for use.
         * </p>
         *
         * @param bundle a bunch of stuff, or null if there's no stuff to be had
         */
        public abstract void init(@Nullable Bundle bundle);

        /**
         * Does whatever cleanup rigmarole is needed for this class, such as
         * unsubscribing to all those subscriptions you set up in {@link #setMap(GoogleMap)}
         * or {@link #init(Bundle)}.
         */
        public void cleanUp() {
            // The marker always goes away, at the very least.
            removeDestinationPoint();

            if(mCentralMap != null) mCentralMap.getErrorBanner().animateBanner(false);

            // Set the cleaned up flag, too.
            mCleanedUp = true;
        }

        /**
         * Stores the state of this mode into yonder Bundle.  This is NOT
         * guaranteed to be followed by {@link #cleanUp()}, apparently.  Did not
         * know that at first.  This is also where you write out any data that
         * might be useful to other modes, such as the selected Graticule in
         * SelectAGraticuleMode.
         *
         * @param bundle the Bundle to which to write data.
         */
        public abstract void onSaveInstanceState(@NonNull Bundle bundle);

        /**
         * Called when the Activity gets onPause().  Remember, the mode object
         * might not ever get this call.  This is only if the Activity is
         * EXPLICITLY pausing AFTER this mode was created.
         */
        public abstract void pause();

        /**
         * Called when the Activity gets onResume().  Remember, the mode object
         * might not ever get this call.  This is only if the Activity is
         * EXPLICITLY resuming AFTER this mode was created.
         */
        public abstract void resume();

        /**
         * Called when a new Info has come in from StockService.
         *
         * @param info that Info
         * @param nearby any nearby Infos that may have been requested (can be null)
         * @param flags the request flags that were sent with it
         */
        public abstract void handleInfo(Info info, @Nullable Info[] nearby, int flags);

        /**
         * Called when a stock lookup fails for some reason.
         *
         * @param reqFlags the flags used in the request
         * @param responseCode the response code (won't be {@link StockService#RESPONSE_OKAY}, for obvious reasons)
         */
        public abstract void handleLookupFailure(int reqFlags, int responseCode);

        /**
         * Called when the menu needs to be built.
         *
         * @param inflater a MenuInflater, for convenience
         * @param menu the Menu that needs inflating.
         */
        public abstract void onCreateOptionsMenu(MenuInflater inflater, Menu menu);

        /**
         * Called when a new Calendar comes in.  The modes should update as need
         * be.  This should mean calling for a new Info from StockService, but
         * NOT updating its own Info or concept of the current Calendar if there
         * was a problem with the stock (i.e. it wasn't posted yet).
         *
         * @param newDate the new Calendar
         */
        public abstract void changeCalendar(@NonNull Calendar newDate);

        /**
         * Draws a final destination point on the map given the appropriate
         * Info.  This also removes any old point that might've been around.
         *
         * @param info the new Info
         */
        protected void addDestinationPoint(Info info) {
            // Clear any old destination marker first.
            removeDestinationPoint();

            if(info == null) return;

            // We need a marker!  And that marker needs a title.  And that title
            // depends on globalhashiness and retroness.
            String title;

            if(!info.isRetroHash()) {
                // Non-retro hashes don't have today's date on them.  They just
                // have "today's [something]".
                if(info.isGlobalHash()) {
                    title = mCentralMap.getString(R.string.marker_title_today_globalpoint);
                } else {
                    title = mCentralMap.getString(R.string.marker_title_today_hashpoint);
                }
            } else {
                // Retro hashes, however, need a date string.
                String date = DateFormat.getDateInstance(DateFormat.LONG).format(info.getDate());

                if(info.isGlobalHash()) {
                    title = mCentralMap.getString(R.string.marker_title_retro_globalpoint, date);
                } else {
                    title = mCentralMap.getString(R.string.marker_title_retro_hashpoint, date);
                }
            }

            // The snippet's just the coordinates in question.  Further details
            // will go in the infobox.
            String snippet = UnitConverter.makeFullCoordinateString(mCentralMap, info.getFinalLocation(), false, UnitConverter.OUTPUT_LONG);

            // Under the current marker image, the anchor is the very bottom,
            // halfway across.  Presumably, that's what the default icon also
            // uses, but we're not concerned with the default icon, now, are we?
            mDestination = mMap.addMarker(new MarkerOptions()
                    .position(info.getFinalDestinationLatLng())
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.final_destination))
                    .anchor(0.5f, 1.0f)
                    .title(title)
                    .snippet(snippet));
        }

        /**
         * Removes the destination point, if one exists.
         */
        protected void removeDestinationPoint() {
            if(mDestination != null) {
                mDestination.remove();
                mDestination = null;
            }
        }

        /**
         * Sets the title of the map Activity using a String.
         *
         * @param title the new title
         */
        protected final void setTitle(String title) {
            mCentralMap.setTitle(title);
        }

        /**
         * Sets the title of the map Activity using a resource ID.
         *
         * @param resid the new title's resource ID
         */
        protected final void setTitle(int resid) {
            mCentralMap.setTitle(resid);
        }

        /**
         * Returns whether or not {@link #cleanUp()} has been called yet.  If
         * so, you should generally not call anything else.
         *
         * @return true if cleaned up, false if not
         */
        public final boolean isCleanedUp() {
            return mCleanedUp;
        }

        /**
         * Returns whether or not {@link #init(Bundle)} has finished.  If so,
         * you probably shouldn't call init again, and are probably looking to
         * call resume instead.
         *
         * @return true if init is complete, false if not
         */
        public final boolean isInitComplete() {
            return mInitComplete;
        }
    }

    private class StockReceiver extends BroadcastReceiver {
        private long mWaitingOnThisOne = -1;

        public void setWaitingId(long id) {
            mWaitingOnThisOne = id;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // A stock result arrives!  Let's get data!  That oughta tell us
            // whether or not we're even going to bother with it.
            int reqFlags = intent.getIntExtra(StockService.EXTRA_REQUEST_FLAGS, 0);
            long reqId = intent.getLongExtra(StockService.EXTRA_REQUEST_ID, -1);

            // Now, if the flags state this was from the alarm or somewhere else
            // we weren't expecting, give up now.  We don't want it.
            if((reqFlags & StockService.FLAG_ALARM) != 0) return;

            // Only check the ID if this was user-initiated.  If the user didn't
            // initiate it, we might be getting responses back in bunches,
            // meaning that ID checking will be useless.
            if((reqFlags & StockService.FLAG_USER_INITIATED) != 0 && reqId != mWaitingOnThisOne) return;

            // Well, it's what we're looking for.  What was the result?  The
            // default is RESPONSE_NETWORK_ERROR, as not getting a response code
            // is a Bad Thing(tm).
            int responseCode = intent.getIntExtra(StockService.EXTRA_RESPONSE_CODE, StockService.RESPONSE_NETWORK_ERROR);

            if(responseCode == StockService.RESPONSE_OKAY) {
                // Hey, would you look at that, it actually worked!  So, get
                // the Info out of it and fire it away to the corresponding
                // CentralMapMode.
                Info received = intent.getParcelableExtra(StockService.EXTRA_INFO);
                Parcelable[] pArr = intent.getParcelableArrayExtra(StockService.EXTRA_NEARBY_POINTS);

                Info[] nearby = null;
                if(pArr != null)
                    nearby = Arrays.copyOf(pArr, pArr.length, Info[].class);
                mCurrentMode.handleInfo(received, nearby, reqFlags);
            } else  {
                // Make sure the mode knows what's up first.
                mCurrentMode.handleLookupFailure(reqFlags, responseCode);

                if((reqFlags & StockService.FLAG_USER_INITIATED) != 0) {
                    // ONLY notify the user of an error if they specifically
                    // requested this stock.
                    switch(responseCode) {
                        case StockService.RESPONSE_NOT_POSTED_YET:
                            mBanner.setText(getString(R.string.error_not_yet_posted));
                            mBanner.setErrorStatus(ErrorBanner.Status.ERROR);
                            mBanner.animateBanner(true);
                            break;
                        case StockService.RESPONSE_NO_CONNECTION:
                            mBanner.setText(getString(R.string.error_no_connection));
                            mBanner.setErrorStatus(ErrorBanner.Status.ERROR);
                            mBanner.animateBanner(true);
                            break;
                        case StockService.RESPONSE_NETWORK_ERROR:
                            mBanner.setText(getString(R.string.error_server_failure));
                            mBanner.setErrorStatus(ErrorBanner.Status.ERROR);
                            mBanner.animateBanner(true);
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    private StockReceiver mStockReceiver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load up!
        if(savedInstanceState != null) {
            mCurrentInfo = savedInstanceState.getParcelable("info");
            mAlreadyDidInitialZoom = savedInstanceState.getBoolean("alreadyZoomed", false);
            mSelectAGraticule = savedInstanceState.getBoolean("selectAGraticule", false);
            mGlobalhash = savedInstanceState.getBoolean("globalhash", false);

            mLastGraticule = savedInstanceState.getParcelable("lastGraticule");

            mLastCalendar = (Calendar)savedInstanceState.getSerializable("lastCalendar");

            // This will just get dropped right back into the mode wholesale.
            mLastModeBundle = savedInstanceState.getBundle(LAST_MODE_BUNDLE);
        }

        setContentView(R.layout.centralmap);

        // We deal with locations, so we deal with the GoogleApiClient.  It'll
        // connect during onStart.
        mGoogleClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mBanner = (ErrorBanner)findViewById(R.id.error_banner);
        mStockReceiver = new StockReceiver();

        // Get a map ready.  We'll know when we've got it.  Oh, we'll know.
        MapFragment mapFrag = (MapFragment)getFragmentManager().findFragmentById(R.id.map);
        mapFrag.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;

                // I could swear you could do this in XML...
                UiSettings set = mMap.getUiSettings();

                // The My Location button has to go off, as we're going to have the
                // infobox right around there.
                set.setMyLocationButtonEnabled(false);

                mMap.setMyLocationEnabled(true);

                // Now, set the flag that tells everything else (especially the
                // doReadyChecks method) we're ready.  Then, call doReadyChecks.
                // We might still be waiting on the API.
                mMapIsReady = true;
                doReadyChecks();
            }
        });

        // Now, we get our initial mode set up based on mSelectAGraticule.  We
        // do NOT init it yet; we have to wait for both the map fragment and the
        // API to be ready first.
        if(mSelectAGraticule)
            mCurrentMode = new SelectAGraticuleMode();
        else
            mCurrentMode = new ExpeditionMode();
    }

    @Override
    protected void onPause() {
        // The receiver goes right off as soon as we pause.
        unregisterReceiver(mStockReceiver);

        // The modes should know what they need to do when pausing.
        if(mCurrentMode != null)
            mCurrentMode.pause();

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // The receiver goes on during onResume, even though we might not be
        // waiting for anything yet.
        IntentFilter filt = new IntentFilter();
        filt.addAction(StockService.ACTION_STOCK_RESULT);
        registerReceiver(mStockReceiver, filt);

        // The mode will resume itself once the client comes back in from
        // onStart.
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Service up!
        mGoogleClient.connect();
    }

    @Override
    protected void onStop() {
        // Service down!
        mGoogleClient.disconnect();

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // Make sure that mode's been cleaned up first.
        mCurrentMode.cleanUp();

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Also, keep the latest Info around.
        // TODO: Later, we'll need to know NOT to reload the Info at startup
        // time.  Determine the correct way to determine that.
        outState.putParcelable("info", mCurrentInfo);

        // Keep the various flags, too.
        outState.putBoolean("alreadyZoomed", mAlreadyDidInitialZoom);
        outState.putBoolean("selectAGraticule", mSelectAGraticule);
        outState.putBoolean("globalhash", mGlobalhash);

        // And some additional data.
        outState.putParcelable("lastGraticule", mLastGraticule);
        outState.putSerializable("lastCalendar", mLastCalendar);

        // Also, shut down the current mode.  We'll rebuild it later.
        if(mCurrentMode != null) {
            mLastModeBundle = new Bundle();
            mCurrentMode.onSaveInstanceState(mLastModeBundle);
            outState.putBundle(LAST_MODE_BUNDLE, mLastModeBundle);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        // Just hand it off to the current mode, it'll know what to do.
        mCurrentMode.onCreateOptionsMenu(inflater, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_selectagraticule: {
                // It's Select-A-Graticule Mode!  At long last!
                enterSelectAGraticuleMode();
                return true;
            }
            case R.id.action_exitgraticule: {
                // We've left Select-A-Graticule for whatever reason.
                exitSelectAGraticuleMode();
                return true;
            }
            case R.id.action_date: {
                // The date picker is common to all modes and is best handled by
                // the Activity itself.  With that said, we can just use the
                // basic DatePickerDialog, because I know from experience the
                // DatePicker widget has a few... quirks.
                if(mLastCalendar == null) {
                    // Of course, we need a date to fill in.
                    mLastCalendar = Calendar.getInstance();
                }

                GHDDatePickerDialogFragment frag = GHDDatePickerDialogFragment.newInstance(mLastCalendar);
                frag.setCallback(this);
                frag.show(getFragmentManager(), DATE_PICKER_DIALOG);

                return true;
            }
            case R.id.action_whatisthis: {
                // The everfamous and much-beloved "What's Geohashing?" button,
                // because honestly, this IS sort of confusing if you're
                // expecting something for geocaching.
                Intent i = new Intent();
                i.setAction(Intent.ACTION_VIEW);
                i.setData(Uri.parse("http://wiki.xkcd.com/geohashing/How_it_works"));
                startActivity(i);
                return true;
            }
            case R.id.action_preferences: {
                // Preferences!  To the Preferencemobile!
                Intent i = new Intent(this, PreferencesActivity.class);
                startActivity(i);
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void requestStock(Graticule g, Calendar cal, int flags) {
        // As a request ID, we'll use the current date, because why not?
        long date = cal.getTimeInMillis();

        Intent i = new Intent(this, StockService.class)
                .putExtra(StockService.EXTRA_DATE, cal)
                .putExtra(StockService.EXTRA_GRATICULE, g)
                .putExtra(StockService.EXTRA_REQUEST_ID, date)
                .putExtra(StockService.EXTRA_REQUEST_FLAGS, flags);

        if((flags & StockService.FLAG_USER_INITIATED) != 0)
            mStockReceiver.setWaitingId(date);

        WakefulIntentService.sendWakefulWork(this, i);
    }

    @Override
    public void onConnected(Bundle bundle) {
        // If we're coming back from somewhere, reset the marker.  This is just
        // in case the user changes coordinate preferences, as the marker only
        // updates its internal info when it's created.
        if(!isFinishing()) {
            doReadyChecks();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Since the location API doesn't appear to connect back to the network,
        // I'm not sure I need to do anything special here.  I'm not even
        // entirely convinced the connection CAN become suspended after it's
        // made unless things are completely hosed.
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // I'm not really certain how this can fail to connect, and so I'm not
        // really certain what to do if it does.
    }

    private void enterSelectAGraticuleMode() {
        if(mSelectAGraticule) return;
        mSelectAGraticule = true;

        // We can at least get a starter Graticule for Select-A-Graticule, if
        // Expedition had one yet.
        mLastModeBundle = new Bundle();
        mCurrentMode.onSaveInstanceState(mLastModeBundle);
        mCurrentMode.cleanUp();
        mCurrentMode = new SelectAGraticuleMode();
        doReadyChecks();
    }

    /**
     * Tells Select-A-Graticule mode to exit, and does whatever's needed to make
     * that work.  I could sure use a better way to do this other than making
     * the method public...
     */
    public void exitSelectAGraticuleMode() {
        if(!mSelectAGraticule) return;
        mSelectAGraticule = false;

        // The result can be retrieved from the Bundle and shoved right into
        // ExpeditionMode via doReadyChecks.
        mLastModeBundle = new Bundle();
        mCurrentMode.onSaveInstanceState(mLastModeBundle);
        mCurrentMode.cleanUp();
        mCurrentMode = new ExpeditionMode();
        doReadyChecks();
    }

    @Override
    public void onBackPressed() {
        // If we're in Select-A-Graticule, pressing back will send us back to
        // expedition mode.  This seems obvious, especially when the default
        // implementation will close the graticule fragment anyway when the back
        // stack is popped, but we also need to do the other stuff like change
        // the menu back, stop the tap-the-map selections, etc.  Also, I really
        // wish there were a better way to do this that didn't require this
        // Activity keeping track of things.
        if(mCurrentMode instanceof SelectAGraticuleMode)
            exitSelectAGraticuleMode();
        else
            super.onBackPressed();
    }

    private boolean doReadyChecks() {
        // This should be called any time the Google API client or MapFragment
        // become ready.  It'll check to see if both are up, starting the
        // current mode when so.
        if(!mCurrentMode.isCleanedUp() && mMapIsReady && mGoogleClient != null && mGoogleClient.isConnected()) {
            if(mCurrentMode.isInitComplete()) {
                mCurrentMode.resume();
            } else {
                mCurrentMode.setMap(mMap);
                mCurrentMode.setCentralMap(this);
                mCurrentMode.init(mLastModeBundle);
            }
            invalidateOptionsMenu();

            return true;
        } else {
            return false;
        }
    }

    /**
     * Gets the {@link ErrorBanner} we currently hold.  This is mostly for the
     * {@link CentralMapMode} classes.
     *
     * @return the current ErrorBanner
     */
    public ErrorBanner getErrorBanner() {
        return mBanner;
    }

    /**
     * Gets the {@link GoogleApiClient} we currently hold.  There's no guarantee
     * it's connected at this point, so be careful.
     *
     * @return the current GoogleApiClient
     */
    public GoogleApiClient getGoogleClient() {
        return mGoogleClient;
    }

    @Override
    public void datePicked(Calendar picked) {
        // Calendar!
        mLastCalendar = picked;
        mCurrentMode.changeCalendar(mLastCalendar);
    }
}