/**
 * QueueService.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.tools;

import java.util.Iterator;
import java.util.Queue;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * <p>
 * A <code>QueueService</code> is similar in theory to an
 * <code>IntentService</code>, with the exception that the <code>Intent</code>
 * is stored in a queue and dealt with that way.  This also means the queue can
 * be observed and iterated as need be to, for instance, get a list of
 * currently-waiting things to process.
 * </p>
 * 
 * <p>
 * Note that while <code>QueueService</code> has many superficial similarities
 * to <code>IntentService</code>, it is NOT a subclass of it.  They just don't
 * work similarly enough under the hood to justify it.
 * </p>
 * 
 * @author captainspam
 *
 */
public abstract class QueueService extends Service {

    /**
     * Codes returned from onHandleIntent that tells the queue what to do next.
     */
    protected enum ReturnCode {
        /** Queue should continue as normal. */
        CONTINUE,
        /** Queue should pause until resumed later.  Useful for errors. */
        PAUSE,
        /**
         * Queue should stop entirely and not be resumed.  Whether or not this
         * means the queue will be emptied is not defined.
         */
        STOP
    }
    
    private Queue<Intent> mQueue;
    
    /**
     * Gets an iterator to the current queue.
     * 
     * @return an iterator to the current queue
     */
    public Iterator<Intent> getIterator() {
        return mQueue.iterator();
    }
    
    /**
     * Gets how many items are currently in the queue.
     * 
     * @return the number of items in the queue
     */
    public int getSize() {
        return mQueue.size();
    }
    
    /* (non-Javadoc)
     * @see android.app.Service#onBind(android.content.Intent)
     */
    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }
    
    private class QueueThread implements Runnable {

        @Override
        public void run() {
            // Now!  Loop through the queue!
            Intent i;
            
            while(!mQueue.isEmpty()) {
                i = mQueue.remove();
                
                ReturnCode r = onHandleIntent(i);
                
                // If the return code we got instructed us to stop entirely,
                // bail out of the thread.
                if(r == ReturnCode.STOP) break; 
            }
            
            // Queue's done!  Thread can terminate now.
        }
        
    }

    /**
     * Subclasses call this every time something from the queue comes in to be
     * processed.  This will not be called on the main thread.
     * 
     * @param i Intent to be processed
     * @return a ReturnCode indicating what the queue should do next
     */
    protected abstract ReturnCode onHandleIntent(Intent i);
}
