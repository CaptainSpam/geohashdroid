/**
 * ServiceTester.java
 * Copyright (C)2014 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid.activities;

import java.util.Calendar;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import net.exclaimindustries.geohashdroid.R;
import net.exclaimindustries.geohashdroid.services.AlarmService;
import net.exclaimindustries.geohashdroid.services.StockService;
import net.exclaimindustries.geohashdroid.util.Graticule;
import net.exclaimindustries.geohashdroid.util.Info;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * This shouldn't have made it to the repo.
 * 
 * @author captainspam
 */
public class ServiceTester extends Activity {
    
    private TextView mResponseTv;
    private TextView mAlarmTv;
    private int mAlarms = 0;
    
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            // Ding!  Message received!
            int requestId = intent.getIntExtra(StockService.EXTRA_REQUEST_ID, -1);
            Info info = (Info)intent.getParcelableExtra(StockService.EXTRA_INFO);
            int responseCode = intent.getIntExtra(StockService.EXTRA_RESPONSE_CODE, StockService.RESPONSE_NETWORK_ERROR);
            int responseFlags = intent.getIntExtra(StockService.EXTRA_RESPONSE_FLAGS, 0);
            
            String toSay;
            
            switch(responseCode) {
                case StockService.RESPONSE_OKAY:
                    // Hooray!
                    double latHash = info.getLatitudeHash();
                    double lonHash = info.getLongitudeHash();
                    
                    toSay = "Request " + requestId + " was good" + ((responseFlags & StockService.FLAG_CACHED) != 0 ? " (from cache)" : "") + ", hashes are " + latHash + ", " + lonHash;
                    break;
                case StockService.RESPONSE_NO_CONNECTION:
                    toSay = "Request " + requestId + " failed due to no network connection";
                    break;
                case StockService.RESPONSE_NOT_POSTED_YET:
                    toSay = "Request " + requestId + " failed because that stock wasn't posted yet";
                    break;
                case StockService.RESPONSE_NETWORK_ERROR:
                    toSay = "Request " + requestId + " failed due to a network error";
                    break;
                default:
                    toSay = "Request " + requestId + " failed, but a response code of " + responseCode + " is meaningless";
            }
            
            mResponseTv.setText(toSay);
            
            
            // If it was an alarm response, update the alarm TextView.
            if((intent.getIntExtra(StockService.EXTRA_REQUEST_FLAGS, 0) & StockService.FLAG_ALARM) != 0) {
                mAlarms++;
                mAlarmTv.setText("Alarms received: " + mAlarms);
            }
        }
        
    };
    
    private IntentFilter mIntentFilter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.servicetester);
        
        final TextView requestTv = (TextView)findViewById(R.id.request);
        mResponseTv = (TextView)findViewById(R.id.results);
        mAlarmTv = (TextView)findViewById(R.id.alarmresult);
        
        Button non30w = (Button)findViewById(R.id.non30w);
        Button is30w = (Button)findViewById(R.id.is30w);
        Button baddate = (Button)findViewById(R.id.baddate);
        
        Button alarmon = (Button)findViewById(R.id.alarmon);
        Button alarmoff = (Button)findViewById(R.id.alarm_off);
        
        // Each button will just fire off an Intent for StockService to handle.
        non30w.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent i = buildIntent(Calendar.getInstance(), StockService.DUMMY_TODAY);
                WakefulIntentService.sendWakefulWork(ServiceTester.this, i);
                
                requestTv.setText("Requested stock for non-30W with request ID " + i.getIntExtra(StockService.EXTRA_REQUEST_ID, -1));
            }
            
        });
        
        is30w.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent i = buildIntent(Calendar.getInstance(), StockService.DUMMY_YESTERDAY);
                WakefulIntentService.sendWakefulWork(ServiceTester.this, i);
                
                requestTv.setText("Requested stock for 30W with request ID " + i.getIntExtra(StockService.EXTRA_REQUEST_ID, -1));
            }
            
        });
        
        baddate.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_MONTH, 7);
                
                Intent i = buildIntent(cal, StockService.DUMMY_YESTERDAY);
                WakefulIntentService.sendWakefulWork(ServiceTester.this, i);
                
                requestTv.setText("Requested stock for next week with request ID " + i.getIntExtra(StockService.EXTRA_REQUEST_ID, -1) + " (should return an error)");
            }
            
        });
        
        alarmon.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                Intent i = new Intent(AlarmService.STOCK_ALARM_ON);
                i.setClass(ServiceTester.this, AlarmService.class);
                WakefulIntentService.sendWakefulWork(ServiceTester.this, i);
            }
        });
        
        alarmoff.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                Intent i = new Intent(AlarmService.STOCK_ALARM_OFF);
                i.setClass(ServiceTester.this, AlarmService.class);
                WakefulIntentService.sendWakefulWork(ServiceTester.this, i);
            }
        });
        
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(StockService.ACTION_STOCK_RESULT);
    }
    
    @Override
    protected void onResume() {
        registerReceiver(mBroadcastReceiver, mIntentFilter);
        super.onResume();
    }
    
    @Override
    protected void onPause() {
        unregisterReceiver(mBroadcastReceiver);
        
        super.onPause();
    }

    private Intent buildIntent(Calendar cal, Graticule grat) {
        Intent i = new Intent(StockService.ACTION_STOCK_REQUEST);
        i.setClass(this, StockService.class);
        
        // I know this is not at all what you should be doing with a long, but
        // what it DOES do is give me SOMETHING that changes every millisecond.
        int now = (int)Calendar.getInstance().getTimeInMillis();
        
        i.putExtra(StockService.EXTRA_DATE, cal);
        i.putExtra(StockService.EXTRA_REQUEST_ID, now);
        i.putExtra(StockService.EXTRA_GRATICULE, grat);
        
        return i;
    }
}
