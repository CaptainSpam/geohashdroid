/*
 * SQLiteQueueService.java
 * Copyright (C) 2018 Nicholas Killewald
 *
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENSE file at the toplevel.
 */

package net.exclaimindustries.tools;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>
 * The base definition of a class that implements {@link QueueService} behavior
 * using an SQLite database to store Intent data (via standard Android calls).
 * You're probably looking for a bit more implementation, like, say,
 * {@link PlainSQLiteQueueService} or {@link AbnormallyDurableSQLiteQueueService}.
 * </p>
 */
public abstract class AbstractSQLiteQueueService extends QueueService {
    private static final String DEBUG_TAG = "SQLiteQueueService";

    protected DatabaseHelper mHelper;
    private SQLiteDatabase mDatabase;

    /** The name of the table storing everything. */
    protected static final String TABLE_QUEUE = "queue";

    /** Everybody needs a rowid, right? */
    protected static final String KEY_QUEUE_ROWID = "_id";
    /** The timestamp of the data.  We sort by this. */
    protected static final String KEY_QUEUE_TIMESTAMP = "timestamp";
    /** The serialized data itself.  Treat as an opaque string. */
    protected static final String KEY_QUEUE_DATA = "data";

    /**
     * We all need some help once in a while.  Databases moreso.
     */
    protected class DatabaseHelper extends SQLiteOpenHelper {

        private static final int DATABASE_VERSION = 1;

        private static final String CREATE_QUEUE_TABLE =
                "CREATE TABLE " + TABLE_QUEUE
                        + " (" + KEY_QUEUE_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + KEY_QUEUE_TIMESTAMP + " INTEGER NOT NULL, "
                        + KEY_QUEUE_DATA + " TEXT NOT NULL);";

        DatabaseHelper(Context context) {
            super(context, getQueueName(), null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_QUEUE_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // This is version 1, there's no upgrading right now.
        }
    }

    /**
     * Initializes a handle to the database.
     *
     * @return a database handle
     * @throws SQLException if something goes wrong
     */
    protected synchronized SQLiteDatabase initDatabase() throws SQLException {
        // If we already have an open database, use that.  Otherwise, make a new
        // one.
        if(mDatabase != null && mDatabase.isOpen())
            return mDatabase;

        mHelper = new DatabaseHelper(this);
        mDatabase = mHelper.getWritableDatabase();
        return mDatabase;
    }

    /**
     * Queries the database for the current queue count.  Will return 0 if
     * anything goes wrong.
     *
     * @return the current queue count as it stands on the database
     */
    protected int getQueueCountFromDatabase() {
        synchronized(this) {
            Cursor cursor = null;
            try {
                SQLiteDatabase database = initDatabase();

                // This oughta be easy.
                cursor = database.query(TABLE_QUEUE, new String[]{KEY_QUEUE_ROWID},
                        null, null, null, null,
                        null);

                if(cursor == null) {
                    Log.w(DEBUG_TAG, "When getting the queue count, the Cursor was null!");
                    return 0;
                }

                int toReturn = cursor.getCount();
                cursor.close();
                return toReturn;
            } catch (SQLException sqle) {
                Log.e(DEBUG_TAG, "Exception in getQueueCount()!", sqle);
                return 0;
            } finally {
                if(cursor != null && !cursor.isClosed()) {
                    cursor.close();
                }
            }
        }
    }

    /**
     * Clears out the database.  That is, effectively does a DELETE ALL FROM
     * TABLE.
     */
    protected void clearQueueFromDatabase() {
        synchronized(this) {
            try {
                // Everybody out of the pool!
                SQLiteDatabase database = initDatabase();

                database.delete(TABLE_QUEUE, null, null);
            } catch(SQLException sqle) {
                Log.e(DEBUG_TAG, "Exception in clearQueue()!", sqle);
            }
        }
    }

    /**
     * Writes the given Intent to database.  {@link #serializeIntent(Intent)}
     * will be called on it first.
     *
     * @param i the Intent to write
     * @throws SQLException if anything SQL-ish goes wrong.
     */
    protected final void writeIntentToDatabase(@NonNull Intent i) throws SQLiteException {
        synchronized(this) {
            SQLiteDatabase database = initDatabase();

            // Serialize the Intent, using whatever method the concrete
            // implementation says it should.
            String data = serializeIntent(i);
            if(data == null) data = "";

            // Grab a timestamp!
            long time = Calendar.getInstance().getTimeInMillis();

            // Now, shove it into the database!
            ContentValues toGo = new ContentValues();
            toGo.put(KEY_QUEUE_TIMESTAMP, time);
            toGo.put(KEY_QUEUE_DATA, data);

            database.insert(TABLE_QUEUE, null, toGo);
        }
    }

    /**
     * Removes the next intent from the database (that is, a remove, not a
     * peek).  Don't call this unless you're either not using a queue or the
     * service is paused.
     *
     * @throws SQLException if something SQL-y goes kerflooey
     */
    protected final void removeNextIntentFromDatabase() throws SQLException {
        synchronized(this) {
            SQLiteDatabase database = initDatabase();

            // Grab us exactly one entry, if that.
            Cursor cursor = database.query(TABLE_QUEUE, new String[]{KEY_QUEUE_ROWID},
                    null, null, null, null,
                    KEY_QUEUE_TIMESTAMP + " ASC", "1");

            if(cursor == null) {
                // I really hope this never comes up, else a LOT of methods will
                // dump this to logcat.
                Log.w(DEBUG_TAG, "When removing the next Intent, the Cursor was null!");
                return;
            }

            if(cursor.getCount() == 0) {
                Log.i(DEBUG_TAG, "Tried to remove next Intent but there's nothing in the database!");
                return;
            }

            // Otherwise, we have us our row ID.
            cursor.moveToFirst();
            long rowId = cursor.getLong(cursor.getColumnIndex(KEY_QUEUE_ROWID));
            cursor.close();

            database.delete(TABLE_QUEUE, KEY_QUEUE_ROWID + "=" + rowId, null);
        }
    }

    /**
     * Gets the next Intent directly from the database (that is, a peek, not a
     * remove).  Don't call this unless you're either not using a queue or the
     * service is paused.
     *
     * @return the next Intent, or null if there is none
     * @throws SQLException something went bad with SQL
     */
    @Nullable
    protected final Intent getNextIntentFromDatabase() throws SQLException {
        synchronized(this) {
            SQLiteDatabase database = initDatabase();

            // We'll delete these when we're done with the Cursor.
            List<Long> toDelete = new LinkedList<>();

            // Grab everything!  Sorted!
            Cursor cursor = database.query(TABLE_QUEUE, new String[]{KEY_QUEUE_ROWID, KEY_QUEUE_DATA},
                    null, null, null, null,
                    KEY_QUEUE_TIMESTAMP + " ASC");

            if(cursor == null) {
                // Problem!
                Log.w(DEBUG_TAG, "When getting the next Intent, the Cursor was null!");
                return null;
            }

            if(cursor.getCount() == 0) {
                // Not really a problem, but the queue's just empty.
                return null;
            }

            cursor.moveToFirst();

            // Now, loop through until we find something we can use (or until
            // we bottom out).
            Intent toReturn = null;

            while(toReturn == null && !cursor.isAfterLast()) {
                // Data!  Now!
                long rowId = cursor.getLong(cursor.getColumnIndex(KEY_QUEUE_ROWID));
                String data = cursor.getString(cursor.getColumnIndex(KEY_QUEUE_DATA));

                // Now, try to deserialize.  This'll be null if it should be
                // ignored.
                toReturn = deserializeIntent(data);

                // And if it IS null, delete it afterward.
                if(toReturn == null)
                    toDelete.add(rowId);

                // The while loop will stop if we found something.  Move on!
                cursor.moveToNext();
            }

            // So!  Let's wrap things up.  Get rid of the cursor.
            cursor.close();

            // Now, delete everything that was null.
            for(Long l : toDelete) {
                database.delete(TABLE_QUEUE, KEY_QUEUE_ROWID + "=" + l, null);
            }

            // And return whatever our result was.  That result may very well be
            // null.
            return toReturn;
        }
    }

    @Override
    protected void onQueueEmpty(boolean allProcessed) {
        // By default, nothing should happen.  This can be overridden.
    }

    @Override
    protected void onQueueStart() {
        // onQueueLoad should've taken care of this by now.
    }
}
