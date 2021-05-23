/*
 * PlainSQLiteQueueService.java
 * Copyright (C) 2018 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.tools;

import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import androidx.annotation.NonNull;
import android.util.Log;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * <p>
 * This type of {@link AbstractSQLiteQueueService} writes Intent data to an
 * SQLite database any time the queue pauses for any reason.  This uses a plain
 * ol' {@link java.util.Queue} during normal operation.  While the queue is
 * running, the SQLite database will be empty (that is, it is emptied out during
 * {@link #onQueueLoad()} and restocked during {@link #onQueueUnload()}).
 * </p>
 *
 * <p>
 * If this service is terminated without going through pause first, the queue
 * may be lost.  Keep this in mind.
 * </p>
 */
public abstract class PlainSQLiteQueueService
        extends AbstractSQLiteQueueService {
    private static final String DEBUG_TAG = "PlainSQLiteQueueService";

    private final Queue<Intent> mQueue;

    public PlainSQLiteQueueService() {
        super();

        // QUEUE!!!
        mQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void onDestroy() {
        // We really shouldn't get here with anything left in the queue, but
        // just in case...
        if(!mQueue.isEmpty()) {
            onQueueUnload();
        }

        // Close down the helper.
        mHelper.close();

        super.onDestroy();
    }

    @Override
    protected void clearQueue() {
        // Empty the database!
        clearQueueFromDatabase();

        // Also, empty out the working queue, if anything's there.
        mQueue.clear();
    }

    @Override
    protected final void onQueueLoad() {
        synchronized(this) {
            SQLiteDatabase database = initDatabase();

            Cursor cursor = null;

            try {
                // Fetch.  EVERYTHING.  In order.
                cursor = database.query(TABLE_QUEUE, new String[]{KEY_QUEUE_TIMESTAMP, KEY_QUEUE_DATA},
                        null, null, null, null,
                        KEY_QUEUE_TIMESTAMP + " ASC");

                if(cursor == null) {
                    Log.w(DEBUG_TAG, "When loading the queue, the Cursor was null!");
                    return;
                }

                // Loop through each row we pulled out of the database and
                // deserialize them into queue entries.
                cursor.moveToFirst();

                while(!cursor.isAfterLast()) {
                    String data = cursor.getString(cursor.getColumnIndex(KEY_QUEUE_DATA));
                    if(data != null) {
                        Intent intent = deserializeIntent(data);
                        if(intent != null) mQueue.add(intent);
                    }

                    cursor.moveToNext();
                }

                cursor.close();

                // Also, wipe out the database.  We'll write it back if need be,
                // but we don't want stray data hanging around.
                clearQueueFromDatabase();
            } catch(SQLException sqle) {
                Log.e(DEBUG_TAG, "Exception in onQueueLoad()!", sqle);
            } finally {
                if(cursor != null && !cursor.isClosed()) cursor.close();
            }
        }
    }

    @Override
    protected final void onQueueUnload() {
        synchronized(this) {
            // If anything's still left, shove it back in.  The database better
            // be empty right now, else we messed something up.
            if(!mQueue.isEmpty()) {
                for(Intent intent : mQueue) {
                    // Hey, look!  We've got a helper function!
                    try {
                        writeIntentToDatabase(intent);
                    } catch(SQLiteException sqle) {
                        Log.e(DEBUG_TAG, "Exception in onQueueUnload()! (will try to keep going)", sqle);
                    }
                }

                // With that done, clear out the in-memory queue.
                mQueue.clear();
            }
        }
    }

    @Override
    protected int getQueueCount() {
        // If the queue is active, use the actual internal queue.
        if(isThreadAlive()) {
            Log.d(DEBUG_TAG, "Thread is live, returning size of queue...");
            return mQueue.size();
        }

        // Otherwise, go to the database.
        Log.d(DEBUG_TAG, "Thread is not live, fetching size from database...");
        return getQueueCountFromDatabase();
    }

    @Override
    protected void removeNextIntentFromQueue() {
        if(isThreadAlive()) {
            if(!mQueue.isEmpty()) mQueue.remove();
        }
        else {
            try {
                removeNextIntentFromDatabase();
            } catch(SQLException sqle) {
                Log.e(DEBUG_TAG, "Error removing the next Intent from the queue!", sqle);
            }
        }
    }

    @Override
    protected Intent peekNextIntentFromQueue() {
        if(isThreadAlive()) return mQueue.peek();

        try {
            return getNextIntentFromDatabase();
        } catch(SQLException sqle) {
            Log.e(DEBUG_TAG, "Error getting the next Intent from the queue! (returning null)", sqle);
            return null;
        }
    }

    @Override
    protected void addIntentToQueue(@NonNull Intent i) {
        if(isThreadAlive()) {
            mQueue.add(i);
        }
        else {
            try {
                writeIntentToDatabase(i);
            } catch(SQLException sqle) {
                Log.e(DEBUG_TAG, "Error adding Intent to the queue!", sqle);
            }
        }
    }
}
