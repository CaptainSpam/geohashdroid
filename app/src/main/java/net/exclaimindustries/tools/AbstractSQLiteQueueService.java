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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    protected synchronized SQLiteDatabase initDatabase() throws SQLException {
        // If we already have an open database, use that.  Otherwise, make a new
        // one.
        if(mDatabase != null && mDatabase.isOpen())
            return mDatabase;

        mHelper = new DatabaseHelper(this);
        mDatabase = mHelper.getWritableDatabase();
        return mDatabase;
    }

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
     * will be called on it first.  If the Intent is null, an empty string will
     * be written.
     *
     * @param i the Intent to write
     * @throws SQLException if anything SQL-ish goes wrong.
     */
    protected void writeIntentToDatabase(@NonNull Intent i) throws SQLiteException {
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
}
