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
import android.util.Log;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * <p>
 * This type of {@link AbstractSQLiteQueueService} writes Intent data to an
 * SQLite database any time the queue pauses for any reason.  This uses a plain
 * ol' {@link java.util.Queue} during normal operation.  While the queue is
 * running, the SQLite database will be empty.
 * </p>
 *
 * <p>
 * If this service is terminated without going through pause first, the queue
 * will be lost.  Keep this in mind.
 * </p>
 */
public abstract class PlainSQLiteQueueService
        extends AbstractSQLiteQueueService {
    private static final String DEBUG_TAG = "PlainSQLiteQueueService";

    private Queue<Intent> mQueue;

    public PlainSQLiteQueueService() {
        super();

        // QUEUE!!!
        mQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void onDestroy() {
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
        // If anything's still left, shove it back in.  The database better be
        // empty right now, else we messed something up.
        if(!mQueue.isEmpty()) {
            for(Intent intent : mQueue) {
                // Hey, look!  We've got a helper function!
                try {
                    writeIntentToDatabase(intent);
                } catch (SQLiteException sqle) {
                    Log.e(DEBUG_TAG, "Exception in onQueueUnload()! (will try to keep going)", sqle);
                }
            }

            // With that done, clear out the in-memory queue.
            mQueue.clear();
        }
    }

    @Override
    protected int getQueueCount() {
        // If the queue is active, use the actual internal queue.
        if(!isPaused()) return mQueue.size();

        // Otherwise, go to the database.
        return getQueueCountFromDatabase();
    }
}
