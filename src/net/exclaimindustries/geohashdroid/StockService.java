/**
 * StockService.java
 * Copyright (C)2012 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

/**
 * <p>
 * <code>StockService is a background service that retrieves the current stock
 * value around 9:30am ET (that is, a reasonable time after the opening of the
 * New York Stock Exchange, at which time the DJIA opening value is known).
 * Then it stores it away in the cache so that later instances of hashing will
 * have that data available right away.
 * </p>
 * 
 * <p>
 * In the current implementation, it ONLY starts up when the app is run.  That
 * is, it won't come on at boot time.  That seems rude for a simple Geohashing
 * app, really.
 * </p>
 * 
 * <p>
 * Preferably, this'll eventually become the central point from which all stock
 * retrieval comes.  So everything will send out BroadcastIntents to get stock
 * data.  Eventually.  Not now.
 * </p>
 * @author Nicholas Killewald
 *
 */
public class StockService extends Service {
    
    private static final String DEBUG_TAG = "StockService";
    
    private static PowerManager.WakeLock mWakeLock;
    
    private static final Graticule DUMMY_YESTERDAY = new Graticule(51, false, 0, true);
    private static final Graticule DUMMY_TODAY = new Graticule(38, false, 84, true);

    private NetworkReceiver mNetReceiver = new NetworkReceiver();
    
    private HashBuilder.StockRunner mRunner;

    private AlarmManager mAlarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
    private NotificationManager mNotificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    
    private NotificationCompat.Builder mNotificationBuilder;
    
    private static final int NOTIFICATION_ID = 1;
    
    /**
     * This handles all wakelockery.
     * 
     * This may be overly complicated.  I'm open to suggestions to change it.
     * 
     * @param c Context from which a wakelock will be gotten
     * @param acquire true to acquire a lock, false to release it
     */
    @SuppressLint("Wakelock")
    private static void doWakeLockery(Context c, boolean acquire) {
        // First, get a wakelock if we need one.
        if(mWakeLock == null)
            mWakeLock = ((PowerManager)c
                    .getSystemService(Context.POWER_SERVICE)).newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, DEBUG_TAG);
        
        if(acquire) {
            if(!mWakeLock.isHeld()) mWakeLock.acquire();
        } else {
            if(mWakeLock.isHeld()) mWakeLock.release();
        }
    }
    
    /**
     * This receiver isn't declared in the manifest because we want to be able
     * to shut it off if we don't want it on.  In general, it should ONLY go on
     * if we run into a network issue and are waiting for the connection to come
     * back up, at which time we can try firing off a stock check again.
     */
    private class NetworkReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(isConnected(context)) {
                doWakeLockery(context, true);
                
                // NETWORK'D!!! Unregister ourselves from broadcasts and get an
                // Intent fired off to the Service!
                context.unregisterReceiver(this);
                Intent i = new Intent(context, StockService.class);
                i.setAction(GHDConstants.STOCK_ALARM);
                context.startService(i);
            }
        }
    }
    
    /**
     * This wakes up the service when the party alarm starts.
     */
    public static class StockAlarmReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            doWakeLockery(context, true);

            // Fire off the Intent to start up the service.  That'll handle all
            // of whatever we need handled.
            Intent i = new Intent(context, StockService.class);
            i.setAction(intent.getAction());
            context.startService(i);
        }
        
    }
    
    /* (non-Javadoc)
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    /**
     * <p>
     * This is the basic Handler for the StockRunner.  This assumes the usual
     * StockService <i>automated</i> operation; that is, it's expecting to be in
     * a process of checking TWO stocks.  If the response it gets says it was
     * checking a 30W stock, it'll fire off another check for the non-30W stock.
     * If the response is non-30W, it'll consider itself done and report any
     * errors it ran into along the way.
     * </p>
     * 
     * <p>
     * All messages to this should be handled in a timely manner.  Handler
     * leaking should not be an issue.
     * </p>
     */
    private static class ResponseHandler extends Handler {
        private WeakReference<StockService> mService;
        
        public ResponseHandler(StockService service) {
            mService = new WeakReference<StockService>(service);
        }
        
        public void handleMessage(Message message) {
            // Response!
            Info info = (Info)message.obj;
            StockService service = mService.get();
            boolean doneHere = true;
            
            // First, what happened?
            if(message.what == HashBuilder.StockRunner.ABORTED) {
                // If we aborted, then just clear the notification and forget
                // about it.  In the current incarnation, we're the only ones
                // who can abort the operation, and there can only be one such
                // operation at a time.
            } else if(message.what == HashBuilder.StockRunner.ERROR_NOT_POSTED) {
                // If it wasn't posted yet, we need to schedule another check
                // later.  Thankfully, the logic required to make sure this is
                // a sane request is in the initial check when the stock alarm
                // happens, so we just need to bump up the time by a half hour
                // and wait it out.  
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.MINUTE, 30);

                Intent alarmIntent = new Intent();
                alarmIntent.setAction(GHDConstants.STOCK_ALARM_RETRY);

                service.mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                        cal.getTimeInMillis(),
                        AlarmManager.INTERVAL_DAY,
                        PendingIntent.getBroadcast(service, 0, alarmIntent, 0));
            } else if(message.what == HashBuilder.StockRunner.ERROR_SERVER) {
                // A server error can mean any of a wide variety of things.  If
                // we're not connected right now, we can pretty reliably assume
                // said thing is in the variety of "it failed because there's no
                // network connection".
                if(!isConnected(service)) {
                    // So if that's the case, the NetworkReceiver can kick in.
                    service.registerReceiver(service.mNetReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                }
                
                // If that's NOT the case, give up.  The remaining wide variety
                // of things is not worth thinking about.  We'll alarm up later.
            } else {
                // Otherwise, we should be good to go!  The runner also cached
                // the data, so we don't have anything else to do with it.  If
                // this was 30W, go get the non-30W data for the SAME date.  If
                // this was non-30W, stop; we're all done here.
                if(info.uses30WRule() && !HashBuilder.hasStockStored(service, info.getCalendar(), DUMMY_TODAY)) {
                    service.doStockFetching(false);
                    
                    // Oh, and we're NOT done here.
                    doneHere = false;
                }
                // If not, we're done!  The alarm should wake us back up later
                // when we need it.
            }
            
            if(doneHere) {
                doWakeLockery(service, false);
                service.clearNotification();
                service.stopSelf();
            }
        }
    }
    
    private void doStockFetching(boolean yesterday) {
        // Remember, this DOES NOT CARE if the stock is already cached.  Do that
        // check FIRST!  It's a static call on HashBuilder!
        Calendar request = getMostRecentStockDate(null);
        showNotification(Info.makeAdjustedCalendar(request,
                (yesterday ? DUMMY_YESTERDAY : DUMMY_TODAY)));
        
        // First, kill the previous StockRunner, if it was in progress somehow.
        if(mRunner != null && mRunner.getStatus() == HashBuilder.StockRunner.BUSY) {
            mRunner.abort();
        }
        
        mRunner = HashBuilder.requestStockRunner(this, request,
                (yesterday ? DUMMY_YESTERDAY : DUMMY_TODAY),
                new ResponseHandler(this));
        
        // We don't need to keep track of the thread.  StockRunner should do
        // that for us.
        Thread thread = new Thread(mRunner);
        thread.setName("StockServiceRunnerThread");
        thread.start();
    }

    /**
     * This makes a 9:30am ET Calendar for today's date.  Note that if a
     * Calendar is supplied, what will be returned will be in America/New_York,
     * using the date it is in New York right now.
     *
     * @param source if not null, use this as the base, rather than build up a
     *               new Calendar from scratch
     * @return a new Calendar for 9:30am ET for today's (or the supplied) date
     */
    private Calendar makeNineThirty(Calendar source) {
        Calendar base;

        if(source == null) {
            base = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        } else {
            base = (Calendar)source.clone();
            base.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        }

        base.set(Calendar.HOUR_OF_DAY, 9);
        base.set(Calendar.MINUTE, 30);

        return base;
    }

    /**
     * Makes a new Calendar that represents the most recent probable date that
     * a stock would exist.  It does so by comparing the current time to 9:30am
     * ET of the same day.  If it's before 9:30am (and would thus be before we
     * can confidently say the NYSE has opened and a value reported), this will
     * rewind it by one day.  If it's after 9:30am, the date will remain the
     * same.  Note that the only important part of this is the date; the actual
     * time and time zone of the returned value are not guaranteed, though
     * chances are it'll be in the same time zone as what is given (or the
     * default time zone if not given).
     *
     * This implicitly assumes that source is today, if given.  This won't
     * return an accurate date if, say, source is next week.
     *
     * @param source if not null, use this as the base, rather than whatever
     *               the system considers the current time.
     * @return a new Calendar whose date is the most recent date a stock is
     *         likely to exist.
     */
    private Calendar getMostRecentStockDate(Calendar source) {
        Calendar base;

        if(source == null) {
            base = Calendar.getInstance();
        } else {
            base = (Calendar)source.clone();
        }

        // First, get 9:30 for today.
        Calendar nineThirty = makeNineThirty(base);

        // Then, compare it to the base.
        if(base.before(nineThirty)) {
            // It's before 9:30am!  Rewind!
            base.add(Calendar.DAY_OF_MONTH, -1);
        }

        // And that should be that!
        return base;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Ready the notification!  The detail text will be set by date, of
        // course.
        mNotificationBuilder = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.geohashing_logo_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setOngoing(true);
        
        // At create time, set the alarm.  We're aiming at 9:30am ET (with any
        // applicable DST adjustments).  The NYSE opens at 9:00am ET, but in the
        // interests of possible clock discrepancies and such (not to mention
        // any delays in the stock reporting sites being updated), we'll wait
        // the extra half hour.  The first alarm should be the NEXT 9:30am ET.
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        
        Calendar alarmTime = makeNineThirty(cal);
        
        if(alarmTime.before(cal)) {
            alarmTime.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        Intent alarmIntent = new Intent(GHDConstants.STOCK_ALARM);
        
        mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                alarmTime.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                PendingIntent.getBroadcast(this, 0, alarmIntent, 0));
    }

    @Override
    public void onStart(Intent intent, int startId) {
        // TODO: Okay, NEXT version I throw out all this 1.6 nonsense and go
        // straight up to 4.0.  Then I can ignore this deprecated method.  And
        // fix one hell of a lot of other things, too.
        super.onStart(intent, startId);

        // Examine the Intent.  What're we doing with it?
        if(intent.getAction().equals(GHDConstants.STOCK_ALARM)
                || intent.getAction().equals(GHDConstants.STOCK_ALARM_RETRY)) {
            // It's the alarm!
            boolean isBusy = false;
            if(isConnected(this)) {
                isBusy = doAllStockDbChecks();
            } else {
                // If there's no connection, wait for one first.
                registerReceiver(mNetReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
            }

            // If we're NOT busy, release the wakelock.  If either of these
            // broadcasts come in, the wakelock is held (so that the device
            // doesn't fall right back asleep after the broadcast is handled),
            // so we need to release it.  However, if we're busy, the response
            // mechanism will take care of things when it's done.
            if(!isBusy) doWakeLockery(this, false);

        } else if(intent.getAction().equals(GHDConstants.STOCK_CANCEL_ALARMS)) {
            // We've been told to stop all alarms!  While we're at it, abort any
            // in-progress connections, too!
            mAlarmManager.cancel(PendingIntent.getBroadcast(this, 0, new Intent(GHDConstants.STOCK_ALARM), 0));
            mAlarmManager.cancel(PendingIntent.getBroadcast(this, 0, new Intent(GHDConstants.STOCK_ALARM_RETRY), 0));
            unregisterReceiver(mNetReceiver);
            if(mRunner != null) mRunner.abort();

            doWakeLockery(this, false);
            stopSelf();
        } else if(intent.getAction().equals(GHDConstants.STOCK_ABORT)) {
            // We've been told to stop what we're doing!
            if(mRunner != null) mRunner.abort();
            doWakeLockery(this, false);
            stopSelf();
        } else if(intent.getAction().equals(GHDConstants.STOCK_INIT)) {
            // Initialization and running stocks are two different things.  If
            // we've been told to init, then alarm creation has already happened
            // in onCreate, and we'll get said alarm when the time comes.  Init
            // means the user is already in the app and poking around, thus it's
            // sort of too late.
            doWakeLockery(this, false);
            stopSelf();
        } else {
            // Stop doing this!
            Log.w(DEBUG_TAG, "Told to start on unknown action " + intent.getAction() + ", ignoring...");
        }
    }

    private static boolean isConnected(Context c) {
        // This just checks if we've got any valid network connection at all.
        NetworkInfo networkInfo = ((ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }
    
    private boolean doAllStockDbChecks() {
        // This does the database checks and fires off doStockFetching if need
        // be.  It returns true if we need to go to the network, false if we
        // have everything we need.
        
        // Why yes, I AM accounting for the vanishingly slim possibility
        // that the boundaries of a day might pass between the two calls
        // of hasStockStored below.  Thank you for noticing.
        Calendar cal = getMostRecentStockDate(null);
        
        // Go to the database!  This should be a relatively painless call.
        if(!HashBuilder.hasStockStored(this, cal, DUMMY_YESTERDAY)) {
            // No stock for yesterday.  Fetch!  Note that this will also
            // grab today's if need be after it's done.
            doStockFetching(true);
            return true;
        } else if(!HashBuilder.hasStockStored(this, cal, DUMMY_TODAY)) {
            // No stock for today.  Fetch!
            doStockFetching(false);
            return true;
        }
        
        // If we fell out of the if statements, we have all the stocks we
        // need.  Return false to let the caller know.
        return false;
    }
    
    private void showNotification(Calendar date) {
        mNotificationBuilder.setContentText(
                getString(R.string.notification_detail,
                        DateFormat
                            .getDateInstance(DateFormat.MEDIUM)
                            .format(date.getTime())));
        mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
    }
    
    private void clearNotification() {
        mNotificationManager.cancel(NOTIFICATION_ID);
    }
}
