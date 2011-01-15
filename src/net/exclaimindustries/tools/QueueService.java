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
import java.util.concurrent.ConcurrentLinkedQueue;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

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
    private static final String DEBUG_TAG = "QueueService";
    
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
    
    /**
     * Send an Intent with this extra data in it, set to one of the command
     * statics, to send a command.
     */
    public static final String COMMAND_EXTRA = "QUEUETHREAD_COMMAND";
    
    /**
     * Command code sent to ask a paused QueueService to resume processing.
     */
    public static final int COMMAND_RESUME = 0;
    /**
     * Command code sent to ask a paused QueueService to give up entirely and
     * empty the queue (and by extension stop the service).  Note that this is
     * NOT guaranteed to stop the queue if it is currently not paused.
     */
    public static final int COMMAND_ABORT = 1;
    
    private Queue<Intent> mQueue;
    private Thread mThread;
    
    // This stores the most recently given command.  The idea is that the
    // thread will wait if the queue needs to be paused, and when a control
    // statement comes in (or when the service otherwise needs to be killed),
    // the thread will awake from its slumber and continue onward in an
    // appropriate manner.  Whether this actually works as planned is quite
    // frankly anybody's guess, but it sounds good in theory.
    private int mLastCommand;
    
    public QueueService() {
        super();
        
        // Give us a queue!
        mQueue = new ConcurrentLinkedQueue<Intent>();
        
        // Set the control to make sure it starts resumed.
        mLastCommand = COMMAND_RESUME;
    }
    
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
    
    @Override
    public void onStart(Intent intent, int startId) {
        // First, check if this is a command message.
        if(intent.hasExtra(COMMAND_EXTRA)) {
            // If so, take command.  Make sure it's a valid command.
            int command = intent.getIntExtra(COMMAND_EXTRA, -1);
            
            if(mThread == null || !mThread.isAlive()) {
                Log.w(DEBUG_TAG, "There's no queue processing thread running!  You can't send a command NOW!");
                return;
            }
            
            if(command == -1) {
                // INVALID!
                Log.w(DEBUG_TAG, "Command Intent didn't have a valid command in it!");
                return;
            }
            
            if(command != COMMAND_RESUME && command != COMMAND_ABORT) {
                Log.w(DEBUG_TAG, "I don't know what sort of command " + command + " is supposed to be, ignoring...");
                return;
            }
            
            // It's a good command, send it off!
            mLastCommand = command;
            Log.d(DEBUG_TAG, "Notifying thread with command " + mLastCommand);
            mThread.notify();
        } else {
            // If this isn't a control message, add the intent to the queue.
            mQueue.add(intent);
            
            // Next, if the thread isn't already running, make it run.  If it IS
            // running, we'll just process the next one in turn.
            if(mThread == null || !mThread.isAlive()) {
                mThread = new Thread(new QueueThread());
                mThread.run();
            }
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        // Make sure the thread is good and dead by now.
        if(mThread != null && mThread.isAlive()) {
            Log.i(DEBUG_TAG, "The thread seems to be alive at onDestroy time, sending it an interrupt...");
            mThread.interrupt();
        }
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
                i = mQueue.peek();

                Log.d(DEBUG_TAG, "Processing intent...");
                
                ReturnCode r = onHandleIntent(i);
                
                Log.d(DEBUG_TAG, "Intent processed, return code is " + r);
                
                // Return check!
                if(r == ReturnCode.STOP) {
                    // If the return code we got instructed us to stop entirely,
                    // bail out of the thread.
                    Log.d(DEBUG_TAG, "Return said to stop, stopping now.");
                    break;
                } else if(r == ReturnCode.CONTINUE) {
                    // CONTINUE means processing was a success, so we can yoink
                    // the Intent from the front of the queue and scrap it.
                    Log.d(DEBUG_TAG, "Return said to continue.");
                    mQueue.remove();
                } else if(r == ReturnCode.PAUSE) {
                    // If we were told to pause, well, pause.  We'll be told to
                    // wake up later.
                    Log.d(DEBUG_TAG, "Return said to pause.");
                    
                    onQueuePause(i);
                    
                    try {
                        wait();
                    } catch (InterruptedException ie) {
                        // If we got interrupted, assume we're bailing out.
                        Log.w(DEBUG_TAG, "INTERRUPTED!");
                        break;
                    }
                    
                    // Now, check the command statement.
                    if(mLastCommand == COMMAND_ABORT) {
                        // If we were told to abort, abort.
                        Log.d(DEBUG_TAG, "Aboring from pause.");
                        break;
                    }
                    
                    // If we were told to continue, head back to the while loop.
                    // We only peeked the Intent, so we'll try it again.
                    Log.d(DEBUG_TAG, "Resuming from pause, repeating last Intent...");
                }
            }
            // Thread's done!  Empty it out just in case.
            Log.d(DEBUG_TAG, "Processing complete, there are " + mQueue.size() + " elements being emptied from the queue.");
            onQueueEmpty(mQueue.isEmpty());
            mQueue.clear();
        }
    }

    /**
     * Subclasses call this every time something from the queue comes in to be
     * processed.  This will not be called on the main thread.  There will be
     * no callback on successful processing of an individual Intent, but
     * onQueuePause will be called if the queue is paused, and onQueueEmpty will
     * be called at the end of all processing.
     * 
     * @param i Intent to be processed
     * @return a ReturnCode indicating what the queue should do next
     */
    protected abstract ReturnCode onHandleIntent(Intent i);
    
    /**
     * This gets called if the queue needs to be paused for some reason.  The
     * Intent that caused the pause will be included.
     * 
     * Note that you aren't doing the actual pausing here.  This method is just
     * here to do status updates or to inform the user that the queue is paused,
     * which might or might not require more input.  If you need more
     * information as to exactly why the queue was paused, you can always stuff
     * more extras in the Intent before it gets here.
     * 
     * @param i Intent that caused the pause
     */
    protected abstract void onQueuePause(Intent i);
    
    /**
     * This is called right before the queue is done processing.  The boolean
     * indicates if the queue is empty at the time.  If the queue isn't empty,
     * that means the thread was aborted before everything was taken care of.
     * 
     * Note that the queue will be emptied immediately after this returns.  This
     * method is to do any status or broadcast updates, not for you to empty the
     * queue.
     * 
     * @param allProcessed true if the queue emptied normally, false if it was
     *                     aborted before all Intents were processed
     */
    protected abstract void onQueueEmpty(boolean allProcessed);
}
