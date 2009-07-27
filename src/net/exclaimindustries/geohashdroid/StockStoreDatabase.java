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
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

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
    
    /** The name of the column for the row's ID. */
    public static final String KEY_ROWID = "_id";
    /** The name of the year column. */
    public static final String KEY_YEAR = "year";
    /** The name of the month column. */
    public static final String KEY_MONTH = "month";
    /** The name of the day column. */
    public static final String KEY_DAY = "day";
    /** The name of the stock value column. */
    public static final String KEY_STOCK = "stock";
    
    private static final String DATABASE_NAME = "stockstore";
    private static final String DATABASE_TABLE = "stocks";
    private static final int DATABASE_VERSION = 1;
    
    // Note that we have three columns for year, month, and day.  Since we can't
    // store Serializables into the database via ContentValues, it's either this
    // or we parse out a string when we read it back.
    
    private static final String DATABASE_CREATE =
        "create table stock (_id integer primary key autoincrement, "
                + "year integer not null, month integer not null, "
                + "day integer not null, stock text not null);";

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
     * @return the new row ID created, or -1 if it went wrong
     */
    public long storeInfo(Info i) {
        // Fortunately, there's a handy ContentValues object for this sort of
        // thing.  I mean, we COULD do manual SQLite calls, but why bother?
        ContentValues toGo = new ContentValues();
        Calendar cal = (Calendar)(i.getCalendar().clone());
        if(i.getGraticule().uses30WRule())
            cal.add(Calendar.DAY_OF_MONTH, -1);
        toGo.put(KEY_YEAR, cal.get(Calendar.YEAR));
        toGo.put(KEY_MONTH, cal.get(Calendar.MONTH));
        toGo.put(KEY_DAY, cal.get(Calendar.DAY_OF_MONTH));
        toGo.put(KEY_STOCK, i.getStockString());
        
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
//    public String getStock(Calendar c, Graticule g) {
//        
//    }
}
