/*
 * AbnormallyDurableSQLiteQueueService.java
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
import android.util.Log;

/**
 * This version of {@link AbstractSQLiteQueueService} does NOT use a {@link java.util.Queue}
 * to store Intents.  Rather, it keeps everything in the SQLite database at all
 * times and all calls go through it.  Because of that, this type of
 * QueueService CAN survive early termination with minimal loss of Intents (if
 * any at all), but it will potentially run considerably slower, given it will
 * make SQLite calls for literally every data access, including a trip through
 * serializing and deserializing as each is added to the queue AND processed.
 */
public abstract class AbnormallyDurableSQLiteQueueService
        extends AbstractSQLiteQueueService {
    private static final String DEBUG_TAG = "SuperSQLiteQueueService";

    @Override
    protected final void onQueueLoad() {
        // Nothing happens.  We're doing this the hard way.
    }

    @Override
    protected final void onQueueUnload() {
        // Same thing.  Everything's in the database already, nothing to do now.
    }

    @Override
    protected int getQueueCount() {
        // Since all the data is stored in the database this time around, we
        // ALWAYS need to make an SQLite call for it.  Yes, even during normal
        // working operation.  I told you this could get considerably slower.
        return getQueueCountFromDatabase();
    }

    @Override
    protected void clearQueue() {
        // Again, no internal queue, so just go for the database.
        clearQueueFromDatabase();
    }
}
