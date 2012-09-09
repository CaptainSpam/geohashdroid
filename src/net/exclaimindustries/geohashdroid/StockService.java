/**
 * StockService.java
 * Copyright (C)2012 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.util.Calendar;
import java.util.TimeZone;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
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
    
//    private static final String DEBUG_TAG = "StockService";
    
    public static class StockBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context c, Intent i) {
            // If this is the boot broadcast, start the service.
            if(i.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
                Intent serviceIntent = new Intent(c, StockService.class);
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

    @Override
    public void onCreate() {
        super.onCreate();
        
        // First off, get today's stocks, if need be and if possible, as soon as
        // we start the service.  That'll ensure we're in a fully-cached state
        // as soon as we begin without waiting a day until the alarm goes off
        // for the first time.
        
        // TODO: Do just that!
        
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

}
