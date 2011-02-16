/**
 * QueueService.java
 * Copyright (C)2011 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.tools;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
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

    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            // WE'RE IN A THREAD NOW!
            super(looper);
        }

        public void handleMessage(Message msg) {
            // Quick!  Hand this off to handleCommand!  It might start ANOTHER
            // thread to deal with this.
            handleCommand((Intent)msg.obj);
        }
    }
    
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
     * Internal prefix of serialized intent data.  Don't change this unless you
     * know you'll be running multiple QueueServices.
     */
    protected String INTERNAL_QUEUE_FILE_PREFIX = "Queue";
    
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
     * Command code sent to ask a paused QueueService to resume processing,
     * skipping the first thing in the queue.
     */
    public static final int COMMAND_RESUME_SKIP_FIRST = 1;
    /**
     * Command code sent to ask a paused QueueService to give up entirely and
     * empty the queue (and by extension stop the service).  Note that this is
     * NOT guaranteed to stop the queue if it is currently not paused.
     */
    public static final int COMMAND_ABORT = 2;
    
    private Queue<Intent> mQueue;
    private Thread mThread;
    
    // Whether or not the queue will restart on a new Intent if it's paused.
    private boolean mResumeOnNewIntent;
    
    // Whether or not the queue is currently paused.
    private boolean mIsPaused;
    
    public QueueService() {
        super();
        
        // Give us a queue!
        mQueue = new ConcurrentLinkedQueue<Intent>();
        
        // Default us to not resuming on a new Intent.
        mResumeOnNewIntent = false;
        
        // And we're not paused by default.
        mIsPaused = false;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // To recreate, we want to go through everything we have in storage in
        // the same order we wrote it out.
        String files[] = fileList();
        
        // But the only files we're interested in are Queue# files.
        int count = 0;
        
        for(String s : files) {
            if(s.startsWith(INTERNAL_QUEUE_FILE_PREFIX))
                count++;
        }
        
        if(count >= 1) {
            // Now, open each one in order and have the deserializer deserialize
            // them.  And because we're being paranoid today, make sure we
            // account for gaps in the numbering.
            int processed = 0;
            
            int i = 0;
            
            while(processed < count) {
                try {
                    // All the queue files are named Queue#.  We know there are
                    // as many as the count variable.  We don't know if all
                    // those digits exist, though, so track how many files we
                    // deserialized and stop when we run out.  I really hope we
                    // don't wind up in an infinite loop here.
                    InputStream is = openFileInput(INTERNAL_QUEUE_FILE_PREFIX + i);
                    
                    Intent intent = deserializeFromDisk(is);
                    if(intent != null) mQueue.add(intent);
                    
                    try {
                        is.close();
                    } catch (IOException e) {
                        // Ignore this.
                    }
                    
                    deleteFile(INTERNAL_QUEUE_FILE_PREFIX + i);
                    processed++;
                } catch (FileNotFoundException e) {
                    // If we get here, we're apparently out of order.
                    Log.w(DEBUG_TAG, "Couldn't find " + INTERNAL_QUEUE_FILE_PREFIX + i + ", apparently we missed a number when writing...");
                }
                
                i++;
            }
            
            // Always assume that a non-empty queue involved a pause somewhere.
            mIsPaused = true;
        }
        
        // Finally, restart the HandlerThread.  We'll wait for further
        // instructions.
        HandlerThread thread = new HandlerThread("QueueService Handler");
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public void onDestroy() {
        // Before destruction, serialize!  Make it snappy!
        int i = 0;
        
        if(mQueue != null) {
            for(Intent in : mQueue) {
                try {
                    serializeToDisk(in, openFileOutput(INTERNAL_QUEUE_FILE_PREFIX + i, MODE_PRIVATE));
                } catch (FileNotFoundException e) {
                    // If we get an exception, complain about it and just move
                    // on.
                    Log.e(DEBUG_TAG, "Couldn't write queue entry to persistant storage!  Stack trace follows...");
                    e.printStackTrace();
                }
                i++;
            }
        }
        
        mServiceLooper.quit();
        
        super.onDestroy();
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
        // Here's a trick I picked up from IntentService...
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        onStart(intent, startId);
        
        // We're not sticky.  We don't want intents re-sent and we call stopSelf
        // whenever we want to stop entirely.
        return Service.START_NOT_STICKY;
    }
    
    /**
     * Handles the Intent sent in.  Specifically, this looks at the Intent,
     * decides if it's a command or a work unit, and then either acts on the
     * command or shoves the Intent into the queue to be processed, starting the
     * queue-working thread if need be.  This gets called on a separate thread
     * from the rest of the GUI (AND a separate thread from the queue worker).
     * 
     * Note very carefully, this is NOT what should process the queue itself.
     * That is, you don't override this as your main workhorse.  You only
     * override this if you have some other special command Intents to handle
     * other than the basic QueueService stuff.
     * 
     * In general, you don't override this.  If you do, make absolutely sure you
     * call back up to the superclass if you're not handling the Intent, and
     * make sure you do so AFTER your own processing, else it'll go in the queue
     * regardless of what you do.
     * 
     * @param intent the incoming Intent
     */
    protected void handleCommand(Intent intent) {
        // First, check if this is a command message.
        if(intent.hasExtra(COMMAND_EXTRA)) {
            // If so, take command.  Make sure it's a valid command.
            int command = intent.getIntExtra(COMMAND_EXTRA, -1);
            
            if(!isPaused()) {
                Log.w(DEBUG_TAG, "The queue isn't paused!  You can't send a command NOW!");
                return;
            }
            
            if(command == -1) {
                // INVALID!
                Log.w(DEBUG_TAG, "Command Intent didn't have a valid command in it!");
                return;
            }
            
            if(command != COMMAND_RESUME && command != COMMAND_ABORT && command != COMMAND_RESUME_SKIP_FIRST) {
                Log.w(DEBUG_TAG, "I don't know what sort of command " + command + " is supposed to be, ignoring...");
                return;
            }

            // The thread should NOT be active right now!  If it is, we're in
            // trouble!
            if(mThread != null && mThread.isAlive()) {
                Log.e(DEBUG_TAG, "isPaused returned true, but the thread is still alive?  What?");
                // Last ditch effort: Try to interrupt the thread to death.
                mThread.interrupt();
            }
            
            mIsPaused = false;
            
            // It's a good command, send it off!
            if(command == COMMAND_RESUME) {
                // Simply restart the thread.  The queue will start from where
                // it left off.
                Log.d(DEBUG_TAG, "Restarting the thread now...");
                mThread = new Thread(new QueueThread(), "QueueService Runner");
                mThread.start();
            } else if(command == COMMAND_RESUME_SKIP_FIRST) {
                Log.d(DEBUG_TAG, "Restarting the thread now, skipping the first Intent...");
                if(mQueue.isEmpty()) {
                    Log.w(DEBUG_TAG, "The queue is empty!  There's nothing to skip!");
                } else {
                    mQueue.remove();
                }
                mThread = new Thread(new QueueThread(), "QueueService Runner");
                mThread.start();
            } else if(command == COMMAND_ABORT) {
                // Simply empty the queue (but call the callback first).
                Log.d(DEBUG_TAG, "Emptying out the queue (removing " + mQueue.size() + " Intents)...");
                onQueueEmpty(false);
                mQueue.clear();
                stopSelf();
            }
        } else {
            // If this isn't a control message, add the intent to the queue.
            Log.d(DEBUG_TAG, "Enqueueing an Intent!");
            mQueue.add(intent);
            
            // Next, if the thread isn't already running (AND we're not paused),
            // make it run.  If it IS running, we'll just process the next one
            // in turn.
            if(isPaused() && mResumeOnNewIntent) {
                Log.d(DEBUG_TAG, "Queue was paused, resuming it now!");
                
                if(mThread != null && mThread.isAlive()) {
                    Log.e(DEBUG_TAG, "isPaused returned true, but the thread is still alive?  What?");
                    // Last ditch effort: Try to interrupt the thread to death.
                    mThread.interrupt();
                }
                
                mIsPaused = false;
                mThread = new Thread(new QueueThread(), "QueueService Runner");
                mThread.start();
            } else if(!isPaused() && (mThread == null || !mThread.isAlive())) {
                Log.d(DEBUG_TAG, "Starting the thread fresh...");
                mThread = new Thread(new QueueThread(), "QueueService Runner");
                mThread.start();
            }
        }
        
        return;
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
                    // wipe the queue and bail out.
                    Log.d(DEBUG_TAG, "Return said to stop, stopping now and abandoning " + mQueue.size() + " Intents.");
                    onQueueEmpty(false);
                    mQueue.clear();
                    stopSelf();
                    return;
                } else if(r == ReturnCode.CONTINUE) {
                    // CONTINUE means processing was a success, so we can yoink
                    // the Intent from the front of the queue and scrap it.
                    Log.d(DEBUG_TAG, "Return said to continue.");
                    mQueue.remove();
                } else if(r == ReturnCode.PAUSE) {
                    // If we were told to pause, well, pause.  We'll be told to
                    // try again later.
                    Log.d(DEBUG_TAG, "Return said to pause.");

                    mIsPaused = true;
                    onQueuePause(i);
                    return;
                }
            }
            // If we got here, then hey!  The thread's done!
            Log.d(DEBUG_TAG, "Processing complete.");
            onQueueEmpty(true);
            stopSelf();
        }
    }
    
    /**
     * Returns whether or not the queue is currently paused.
     * 
     * @return true if paused, false if not
     */
    public boolean isPaused() {
        return mIsPaused;
    }
    
    /**
     * Sets whether or not the queue will resume itself if a new Intent comes in
     * while it's paused.  Ordinarily, it won't; that is, the queue won't move
     * again until an explicit COMMAND_RESUME comes in.  Setting this to true
     * will make it implicit that when a new Intent arrives, if the queue is
     * paused, it will restart it immediately.
     * 
     * @param flag true to auto-restart, false to not (default is false)
     */
    public void setResumeOnNewIntent(boolean flag) {
        mResumeOnNewIntent = flag;
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
     * Intent that caused the pause will be included.  The thread will be killed
     * after this callback returns.  However, isPaused() will return false if
     * called during this callback.  Try not to block it.
     * 
     * Note that you aren't doing the actual pausing here.  This method is just
     * here to do status updates or to inform the user that the queue is paused,
     * which might or might not require more input.  If you need more
     * information as to exactly why the queue was paused, you can always stuff
     * more extras in the Intent during onHandleIntent before it gets here.
     * 
     * @param i Intent that caused the pause
     */
    protected abstract void onQueuePause(Intent i);
    
    /**
     * This is called right after the queue is done processing and right before
     * the thread is killed and isn't paused.  The boolean indicates if
     * processing was complete.  If false, it means a STOP was received or
     * COMMAND_ABORT was sent.  The queue will be emptied AFTER this method
     * returns.
     *  
     * @param allProcessed true if the queue emptied normally, false if it was
     *                     aborted before all Intents were processed
     */
    protected abstract void onQueueEmpty(boolean allProcessed);
    
    /**
     * Serializes the given Intent to disk for later re-reading.  Note that at
     * this point, an Intent is solely used as a means of storing data.  Which,
     * really, it can be, though I doubt that's the intent.  This gets called at
     * onDestroy time for each Intent left in the queue (if any are left at all)
     * so that they can be recreated at onCreate time to persist the Service's
     * state (there doesn't appear to be an onSaveInstanceState like you'd get
     * with Activities).
     * 
     * Note that no checking is done to ensure you actually wrote anything to
     * the stream.  If the result is a zero-byte file, that's your
     * responsibility to handle it at deserialize time.
     * 
     * @param i the Intent to serialize
     * @param os what you'll be writing to
     */
    protected abstract void serializeToDisk(Intent i, OutputStream os);
    
    /**
     * Deserializes an Intent previously written to disk by serializeToDisk.
     * This will be called once for each Intent found on disk, and will be
     * called in the order of the queue.  All you have to do is pull back
     * whatever you wrote in serializeToDisk and get an Intent out of it.
     * 
     * @param is what you'll be reading from
     * @return a new Intent to be processed at the right time (if null is
     *         returned, it will be ignored)
     */
    protected abstract Intent deserializeFromDisk(InputStream is);
}
