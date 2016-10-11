/*
 * StockFetcher.java
 * Copyright (C) 2016 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.geohashdroid.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Calendar;

/**
 * <p>
 * Anything that implements StockFetcher better be able to retrieve a stock
 * price for a given date, as well as properly abort any connections at will.
 * StockFetchers will spin up new threads to handle their queries and return
 * results to a callback asynchronously.
 * </p>
 *
 * <p>
 * These classes are intended to be single-use; if a given instance is already
 * fetching a stock price, don't try to re-use it for another until it's done.
 * </p>
 *
 * @author Nicholas Killewald
 */
public interface StockFetcher {
    /**
     * The last request couldn't be met because the stock value wasn't
     * posted for the given day yet.
     */
    int ERROR_NOT_POSTED = 1;
    /**
     * The last request couldn't be met because of some server error.
     */
    int ERROR_SERVER = 2;
    /**
     * The user aborted the request.
     */
    int ABORTED = 3;

    /**
     * StockFetcher's callback is what gets called when a stock value comes in.
     */
    interface Callback {
        /**
         * Called when a stock successfully comes back.
         *
         * @param cal the date for this stock
         * @param stock the value fetched
         */
        void stockFetched(@NonNull Calendar cal, float stock);

        /**
         * Called when there was some problem that prevented the stock from
         * being fetched.  This WILL be called if {@link StockFetcher#abort()}
         * is called and a request was in progress.
         *
         * @param code the reason why
         * @see #ERROR_NOT_POSTED
         * @see #ERROR_SERVER
         * @see #ABORTED
         */
        void stockError(int code);
    }

    /**
     * Starts the fetching in motion for the given date.  This will generally
     * fire up a new thread.
     *
     * @param cal the date requested
     * @param callback a callback for when the stock comes back
     * @throws IllegalStateException if the fetcher was busy and couldn't start
     */
    void fetchStock(@NonNull Calendar cal, @Nullable Graticule g, @NonNull Callback callback) throws IllegalStateException;

    /**
     * Aborts whatever current fetch is in progress.  This won't return any sort
     * of status.
     */
    void abort();
}
