/**
 * StockStoreDatabase.java
 * Copyright (C)2009 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.geohashdroid;

import java.util.Calendar;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.exclaimindustries.tools.DateTools;

/**
 * <p>
 * A <code>StockStoreDatabase</code> object talks to the database to store and
 * retrieve stock prices to and from (respectively) the cache.  It does this via
 * <code>Info</code> bundles, so it will account for the 30W Rule as need be,
 * assuming it was created properly from <code>HashBuilder</code>.
 * </p>
 * 
 * @author Nicholas Killewald
 */
public class StockStoreDatabase {
    private final Context mContext;
    private DatabaseHelper mHelper;
    private SQLiteDatabase mDatabase;
    
    private static final String DEBUG_TAG = "StockStoreDatabase";
    
    /** The name of the column for the row's ID. */
    public static final String KEY_ROWID = "_id";
    /** The name of the date column. */
    public static final String KEY_DATE = "date";
    /** The name of the stock value column. */
    public static final String KEY_STOCK = "stock";
    
    private static final String DATABASE_NAME = "stockstore";
    private static final String DATABASE_TABLE = "stocks";
    private static final int DATABASE_VERSION = 1;
    
    private static final String DATABASE_CREATE =
        "CREATE TABLE " + DATABASE_TABLE
            + " (" + KEY_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + KEY_DATE + " INTEGER NOT NULL, "
            + KEY_STOCK + " TEXT NOT NULL);";

    /**
     * Implements SQLiteOpenHelper.  Much like Hamburger Helper, this can take
     * a pound of database and turn it into a meal.
     * 
     * @author Nicholas Killewald
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // This IS the first version of the database, so there's nothing to
            // do here.
        }
    }
    
    /**
     * Constructs a StockStoreDatabase object.
     * 
     * @param c Context to be used to open the database later
     */
    public StockStoreDatabase(Context c) {
        mContext = c;
    }
    
    /**
     * Checks to see if the database in this object is open.  If not, recreate
     * a new one.
     * 
     * @return true if open, false if not
     */
    public boolean isDatabaseOpen() {
        return mDatabase != null && mDatabase.isOpen();
    }
    
    /**
     * Initializes the store.  That is to say, opens the database for action.
     * Or creates it and THEN opens it.  Or just gives up and throws an
     * exception.
     * 
     * @return this (self reference, allowing this to be chained in an
     *         initialization call)
     * @throws SQLException if the database could be neither opened or created
     */
    public StockStoreDatabase init() throws SQLException {
        mHelper = new DatabaseHelper(mContext);
        mDatabase = mHelper.getWritableDatabase();
        return this;
    }
    
    /**
     * Finishes up.  In this case, closes the database.
     */
    public void finish() {
        mHelper.close();
    }
    
    /**
     * Stores a bundle of Info into the database.
     * 
     * @param i the aforementioned bundle of Info to be stored into the database
     * @return the new row ID created, or -1 if it went wrong or already exists
     */
    public long storeInfo(Info i) {
        // Fortunately, there's a handy ContentValues object for this sort of
        // thing.  I mean, we COULD do manual SQLite calls, but why bother?
        
        // But first!  First we need to know if this already exists.  If it
        // does, return a -1.
        // TODO: No, wrong.  I need a better mechanism for that.
        if(getStock(i.getCalendar(), i.getGraticule()) != null) {
            Log.d(DEBUG_TAG, "Stock price already exists, ignoring...");
            return -1;
        }
        
        ContentValues toGo = new ContentValues();
        Calendar cal = i.getStockCalendar();
        toGo.put(KEY_DATE, DateTools.getDateString(cal));
        toGo.put(KEY_STOCK, i.getStockString());
        
        Log.d(DEBUG_TAG, "NOW STORING " + DateTools.getDateString(cal) + " : " + i.getStockString());
        
        return mDatabase.insert(DATABASE_TABLE, null, toGo);
    }
    
    /**
     * Retrieves a stock quote from the database, if it exists.  If not,
     * returns null instead.
     * 
     * @param c Calendar containing the date to retrieve (this should NOT be
     *          adjusted for the 30W Rule)
     * @param g Graticule to use to determine if the 30W Rule is in effect
     * @return String containing the stock quote, or null if it doesn't exist
     */
    public String getStock(Calendar c, Graticule g) {
        // First, adjust the calendar if we need to.
        Calendar cal = Info.makeAdjustedCalendar(c, g);
        String toReturn = null;
        
        // Now, to the database!
        Cursor cursor = mDatabase.query(DATABASE_TABLE, new String[] {KEY_STOCK},
                KEY_DATE + " = " + DateTools.getDateString(cal),
                null, null, null, null);
        
        if(cursor == null) {
            // If a problem happens, assume there's no stock to get.
            Log.w(DEBUG_TAG, "The cursor returned from the query was null!");
            return null;
        } else if(cursor.getCount() == 0) {
            // If nothing resulted from this, the stock doesn't exist in the
            // cache.
            Log.d(DEBUG_TAG, "Stock doesn't exist in database");
        } else {
            // Otherwise, grab the first one we come across.
            if(!cursor.moveToFirst()) return null;
            
            toReturn = cursor.getString(0);
        }
        
        cursor.close();
        return toReturn;
    }
}
