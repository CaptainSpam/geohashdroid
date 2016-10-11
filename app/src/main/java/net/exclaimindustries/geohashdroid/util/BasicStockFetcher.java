/*
 * BasicStockFetcher.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Calendar;

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClients;

/**
 * A BasicStockFetcher fetches stocks from URLs formatted like Crox or Peeron's
 * services.  That is, a simple GET URL that has YYYY, MM, and DD components and
 * responds with either a stock price as a string or a 404 indicating it isn't
 * posted yet.
 *
 * @author Nicholas Killewald
 */
public class BasicStockFetcher implements StockFetcher {
    private static final String DEBUG_TAG = "BasicStockFetcher";

    private final class RunningThread implements Runnable {
        private Calendar mSCal;
        private Callback mCallback;

        public RunningThread(@NonNull Calendar sCal,
                             @NonNull Callback callback)
        {
            mSCal = sCal;
            mCallback = callback;
        }

        @Override
        public void run() {
            // Now, we're NOT going to the cache.  The point of this is to run
            // multiple calls at once, canceling anything that hasn't finished by
            // the time the first success comes back.  So go to the cache FIRST,
            // then come here to fetch it from remote.
            try {
                String response = fetchStock(mSCal);
                if(response.isEmpty())
                    mCallback.stockError(ABORTED);
                else
                    mCallback.stockFetched(mSCal, Float.parseFloat(response));
            } catch (FileNotFoundException fnfe) {
                // If we got a 404, assume it's not posted yet.
                mCallback.stockError(ERROR_NOT_POSTED);
            } catch (IOException ioe) {
                // If we got anything else, assume a problem.
                mCallback.stockError(ERROR_SERVER);
            }
        }

        private String fetchStock(@NonNull Calendar sCal) throws IOException {
            // Now, generate a string for the URL.
            String sMonthStr;
            String sDayStr;

            if (sCal.get(Calendar.MONTH) + 1 < 10)
                sMonthStr = "0" + (sCal.get(Calendar.MONTH) + 1);
            else
                sMonthStr = Integer.valueOf(sCal.get(Calendar.MONTH) + 1).toString();

            if (sCal.get(Calendar.DAY_OF_MONTH) < 10)
                sDayStr = "0" + sCal.get(Calendar.DAY_OF_MONTH);
            else
                sDayStr = Integer.valueOf(sCal.get(Calendar.DAY_OF_MONTH)).toString();

            // Good, good! Now, to the web!  Go through our list of sites in order
            // until we find an answer, we bottom out, or we abort.  In terms of
            // what we report to the user, "Server error" is lowest-priority, with
            // "Stock not posted" rating above it.  That is to say, if one server
            // reports and error but another one explicitly tells us the stock
            // wasn't found, the latter is what we use.  Of course, if it turns out
            // we got an abort, well, that's our answer.
            String result;

            // Do all our substitutions...
            String location = mFormat.replaceAll("%Y", Integer.toString(sCal.get(Calendar.YEAR)));
            location = location.replaceAll("%m", sMonthStr);
            location = location.replaceAll("%d", sDayStr);
            Log.v(DEBUG_TAG, "Trying " + location + "...");

            // And go fetch!
            CloseableHttpClient client = HttpClients.createDefault();
            mRequest = new HttpGet(location);

            // Start a request!  If we throw an exception, the caller will get hold
            // of it.
            HttpResponse response = client.execute(mRequest);

            if(mRequest.isAborted())
            {
                // If we aborted, we're not returning anything.
                return "";
            }

            // Now, did we get an error code?
            if (response.getStatusLine().getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new FileNotFoundException();
            } else if (response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK) {
                // A non-okay response that isn't a 404 is bad.  Count this
                // one as ERROR_SERVER.
                throw new IOException();
            }

            // Well, we got this far!  Let's read!
            result = getStringFromStream(response.getEntity().getContent());

            // With that done, we try to convert the output to the float.  If this
            // fails, we got bogus data and it's a server error.
            try {
                //noinspection ResultOfMethodCallIgnored
                Float.parseFloat(result);
            } catch (NumberFormatException nfe) {
                throw new IOException();
            }

            // SUCCESS!
            client.close();
            return result;
        }
    }

    // This is the date format string to be used when assembling the stock URL.
    private String mFormat;
    // The current going request.
    private HttpGet mRequest;
    // The current Thread (to check if it's still running).
    private Thread mThread;

    /**
     * Gets us all constructed and such.
     *
     * @param format the format string
     * @throws IllegalArgumentException if the format doesn't have a %Y, %m, and %d somewhere in it
     */
    public BasicStockFetcher(@NonNull String format) throws IllegalArgumentException {
        // Check over that format first!  Note that this does allow multiple
        // instances of each field.  Eh, sure, why not?
        if(!format.contains("%Y") || !format.contains("%m") || !format.contains("%d"))
            throw new IllegalArgumentException("That format is missing one of the date fields!");

        // Looks good to me!
        mFormat = format;
    }

    @Override
    public void fetchStock(@NonNull Calendar cal,
                           @Nullable Graticule g,
                           @NonNull Callback callback) throws IllegalStateException {
        // First, we need to adjust the calendar in the event we're in the range
        // of the 30W rule.  To that end, sCal is for stock calendar.
        Calendar sCal = Info.makeAdjustedCalendar(cal, g);

        // Then, fire up the thread, if need be.
        if(mThread.isAlive())
        {
            // But if not be, throw an exception.
            throw new IllegalStateException("The thread's already running!");
        }

        mThread = new Thread(new RunningThread(sCal, callback));
        mThread.start();
    }

    @Override
    public void abort() {
        // If a request is going on, make it not go on.
        if(mRequest != null)
            mRequest.abort();
    }

    @NonNull
    private static String getStringFromStream(InputStream stream)
            throws IOException {
        BufferedReader buff = new BufferedReader(new InputStreamReader(stream));

        // Load it up...
        StringBuilder tempstring = new StringBuilder();
        char bean[] = new char[1024];
        int read;
        while ((read = buff.read(bean)) != -1) {
            tempstring.append(bean, 0, read);
        }

        return tempstring.toString();
    }
}
