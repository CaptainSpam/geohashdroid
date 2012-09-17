/**
 * StockService.java
 * Copyright (C)2012 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
//import android.util.Log;

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
 * Preferably, this'll eventually become the central point from which all stock
 * retrieval comes.  So everything will send out BroadcastIntents to get stock
 * data.  
 * </p>
 * @author Nicholas Killewald
 *
 */
public class StockService extends Service {
    
    private static final String DEBUG_TAG = "StockService";
    
    private static PowerManager.WakeLock mWakeLock;
    
    private static final Graticule DUMMY_YESTERDAY = new Graticule(51, false, 0, true);
    private static final Graticule DUMMY_TODAY = new Graticule(38, false, 84, true);
    
    private HashBuilder.StockRunner mRunner;
    
    public static class StockBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context c, Intent i)  {
            // Good morning!  Now STAY awake until we're done here.  If the
            // wakelock is already acquired, don't bother; it'll be freed 
            mWakeLock = ((PowerManager)c
                    .getSystemService(Context.POWER_SERVICE)).newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, DEBUG_TAG);
            
            mWakeLock.acquire();
            
            // If this is the boot broadcast, start the service.
            if(i.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
                Intent serviceIntent = new Intent(c, StockService.class);
                serviceIntent.setAction(i.getAction());
                c.startService(serviceIntent);
            }
        }
        
    }

    private AlarmManager mAlarmManager;
    
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
     * 
     * @author Nicholas Killewald
     */
    private static class ResponseHandler extends Handler {
        private WeakReference<StockService> mService;
        
        public ResponseHandler(StockService service) {
            mService = new WeakReference<StockService>(service);
        }
        
        public void handleMessage(Message message) {
            // Response!
            Info info = (Info)message.obj;
            
            // First, what happened?
            if(message.what == HashBuilder.StockRunner.ABORTED) {
                // If we aborted, then just clear the notification and forget
                // about it.
                
                // TODO: Do so!
            } else if(message.what == HashBuilder.StockRunner.ERROR_NOT_POSTED) {
                // If it wasn't posted yet, we need to send up a notification
                // AND schedule another check later.
                
                // TODO: Do so!
            } else if(message.what == HashBuilder.StockRunner.ERROR_SERVER) {
                // A server error can mean any of a wide variety of things.  For
                // our purposes, we should give up; if it's something transient
                // (i.e. bailed because the network connection vanished), the
                // main service will reschedule it when the network returns.
                
                // TODO: Do so, as well as the NetworkManager callbacks!
            } else {
                // Otherwise, we should be good to go!
                
                // TODO: Handle the next day, if need be!  We can even recycle
                // the Handler object!
            }
        }
    }
    
    private void doStockFetching(boolean yesterday) {
        // Remember, this DOES NOT CARE if the stock is already cached.  Do that
        // check FIRST!  It's a static call on HashBuilder!
        
        // First, kill the previous StockRunner, if it was in progress somehow.
        if(mRunner != null && mRunner.getStatus() == HashBuilder.StockRunner.BUSY) {
            mRunner.abort();
        }
        
        mRunner = HashBuilder.requestStockRunner(this, Calendar.getInstance(),
                (yesterday ? DUMMY_YESTERDAY : DUMMY_TODAY),
                new ResponseHandler(this));
        
        // We don't need to keep track of the thread.  StockRunner should do
        // that for us.
        Thread thread = new Thread(mRunner);
        thread.setName("StockServiceRunnerThread");
        thread.start();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        // First off, get today's stocks, if need be and if possible, as soon as
        // we start the service.  That'll ensure we're in a fully-cached state
        // as soon as we begin without waiting a day until the alarm goes off
        // for the first time.
        
        
        // Second, set the alarm.  We're aiming at 9:30am ET (2:30pm UTC).  The
        // NYSE opens at 9:00am ET, but in the interests of possible clock
        // discrepancies and such (not to mention any delays in the stock
        // reporting sites being updated), we'll wait the extra half hour.  The
        // first alarm should be the NEXT 9:30am ET.
        //
        // TODO: There has to be a more efficient way to determine this.
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        
        Calendar alarmTime = (Calendar)cal.clone();
        alarmTime.set(Calendar.HOUR_OF_DAY, 14);
        alarmTime.set(Calendar.MINUTE, 30);
        
        if(alarmTime.before(cal)) {
            alarmTime.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        Intent alarmIntent = new Intent();
        alarmIntent.setAction(GHDConstants.STOCK_ALARM);
        
        mAlarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        
        mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                alarmTime.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                PendingIntent.getBroadcast(this, 0, alarmIntent, 0));
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

}
