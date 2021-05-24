/*
 * QueueService.java
 * Copyright (C)2018 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.tools;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

/**
 * <p>
 * A <code>QueueService</code> is similar in theory to an
 * {@link android.app.IntentService}, with the exception that the
 * <code>Intent</code> is stored in a queue independent of the OS's Intent
 * delivery mechanism and dealt with that way.
 * </p>
 * 
 * <p>
 * Note that while <code>QueueService</code> has some superficial similarities
 * to <code>IntentService</code>, it is NOT a subclass of it.  They just don't
 * work similarly enough under the hood to justify it.
 * </p>
 * 
 * @author Nicholas Killewald
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
        /**
         * Queue should pause until resumed later.  Useful for temporary
         * errors.  The queue will be written back to long-term storage, the
         * queue will be stopped, and the Intent which caused this pause won't
         * be removed (though see {@link #COMMAND_RESUME_SKIP_FIRST}).
         */
        PAUSE,
        /**
         * Queue should stop entirely with no plans to resume it later.  The
         * queue WILL be emptied afterward.
         */
        STOP
    }

    /**
     * Send an Intent with this extra data in it, set to one of the command
     * statics, to send a command.  Any Intent with this will NOT be processed
     * by the queue; don't put actual work data in such an Intent.
     */
    public static final String COMMAND_EXTRA = "net.exclaimindustries.tools.QUEUETHREAD_COMMAND";
    
    /**
     * Command code sent to ask an inactive QueueService to resume processing.
     */
    public static final int COMMAND_RESUME = 0;
    /**
     * Command code sent to ask an inactive QueueService to resume processing,
     * skipping the first thing in the queue.
     */
    public static final int COMMAND_RESUME_SKIP_FIRST = 1;
    /**
     * Command code sent to ask a paused QueueService to give up entirely and
     * empty the queue (and by extension stop the service).  Note that this will
     * NOT stop the queue if it is currently active.
     */
    public static final int COMMAND_ABORT = 2;
    /**
     * Command code sent to ask for the queue count to be sent out as a
     * BroadcastIntent.  Under normal circumstances, this status will be sent
     * after every item is processed.  This may be ignored (and status Intents
     * NOT sent) if the implementation returns false for
     * {@link #queueCountBroadcastsAllowed()}.
     */
    public static final int COMMAND_QUEUE_COUNT = 10;

    /**
     * Intent action broadcast by QueueService whenever the queue count changes
     * or a queue count request is made (though see
     * {@link #queueCountBroadcastsAllowed()}.
     */
    public static final String ACTION_QUEUE_COUNT = "net.exclaimindustries.tools.ACTION_QUEUETHREAD_COUNT";
    /** Intent extra containing the queue count.  Will be an int. */
    public static final String EXTRA_QUEUE_COUNT = "net.exclaimindustries.tools.EXTRA_QUEUETHREAD_COUNT";
    /**
     * Intent extra containing the name of the queue.  Is sent with any
     * broadcasts to differentiate between potential multiple queues.  Will be a
     * String, and will be whatever {@link #getQueueName()} returns.
     */
    public static final String EXTRA_QUEUE_NAME = "net.exclaimindustries.tools.EXTRA_QUEUETHREAD_NAME";

    private Thread mThread;

    @Override
    public void onCreate() {
        super.onCreate();

        // (Re)start the HandlerThread.  We'll wait for further instructions.
        HandlerThread thread = new HandlerThread("QueueService Handler");
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public void onDestroy() {
        // Shut down the looper.
        mServiceLooper.quit();
        
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Here's a trick I picked up from IntentService...
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);
        
        // We're not sticky.  We don't want intents re-sent and we call stopSelf
        // whenever we want to stop entirely.
        return Service.START_NOT_STICKY;
    }
    
    /**
     * <p>
     * Handles the Intent sent in.  Specifically, this looks at the Intent,
     * decides if it's a command or a work unit, and then either acts on the
     * command or shoves the Intent into the queue to be processed, starting the
     * queue-working thread if need be.  This gets called on a separate thread
     * from the rest of the GUI (AND a separate thread from the queue worker).
     * The actual application-specific work happens in {@link #handleIntent(Intent)}.
     * </p>
     * 
     * @param intent the incoming Intent
     */
    private void handleCommand(Intent intent) {
        // First, check if this is a command message.
        if(intent.hasExtra(COMMAND_EXTRA)) {
            // If so, take command.  Make sure it's a valid command.
            int command = intent.getIntExtra(COMMAND_EXTRA, -1);
            
            if(isThreadAlive()) {
                Log.w(DEBUG_TAG, "The queue is active, ignoring command...");
                return;
            }
            
            if(command == -1) {
                // INVALID!
                Log.w(DEBUG_TAG, "Command Intent didn't have a command in it, ignoring...");
                return;
            }

            // It's a good command, send it off!
            switch(command) {
                case COMMAND_QUEUE_COUNT:
                    // Send out the queue count (if permitted).  That's all.
                    dispatchQueueCountIntent();
                    break;
                case COMMAND_RESUME:
                    // Simply restart the thread.  The queue will start from
                    // where it left off.
                    Log.d(DEBUG_TAG, "Restarting the thread now...");
                    doNewThread();
                    break;
                case COMMAND_RESUME_SKIP_FIRST:
                    Log.d(DEBUG_TAG, "Restarting the thread now, skipping the first Intent...");
                    removeNextIntentFromQueue();
                    doNewThread();
                    break;
                case COMMAND_ABORT:
                    // Empty the queue (but call the callback first).
                    Log.d(DEBUG_TAG, "Emptying out the queue (removing " + getQueueCount() + " Intents)...");
                    onQueueEmpty(false);
                    clearQueue();
                    stopSelf();
                    break;
                default:
                    // This shouldn't happen at all.
                    Log.w(DEBUG_TAG, "I don't know what sort of command " + command + " is supposed to be, ignoring...");
            }
        } else {
            // If this isn't a control message, add the intent to the queue.
            Log.d(DEBUG_TAG, "Enqueueing an Intent!");
            addIntentToQueue(intent);
            
            // Next, if the thread isn't already running, make it run.  If it IS
            // running, we'll just process the next one in turn normally.
            if(!isThreadAlive() && resumeOnNewIntent()) {
                Log.d(DEBUG_TAG, "Thread wasn't active, starting now!");
                doNewThread();
            }
        }
    }
    
    private void doNewThread() {
        // Only call this if the old thread isn't running.
        mThread = new Thread(new QueueThread(), "QueueService Runner");
        mThread.start();
    }

    /**
     * Determines if the thread is alive, part of which also involves
     * determining if the thread is not null.
     *
     * @return true if the thread is alive, false if not
     */
    protected boolean isThreadAlive() {
        return mThread != null && mThread.isAlive();
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
            // Load 'er up!
            onQueueLoad();
            onQueueStart();

            while(getQueueCount() > 0) {
                Intent i = peekNextIntentFromQueue();

                Log.d(DEBUG_TAG, "Processing intent...");
                
                ReturnCode r = handleIntent(i);
                
                Log.d(DEBUG_TAG, "Intent processed, return code is " + r);
                
                // Return check!
                if(r == ReturnCode.STOP) {
                    // If the return code we got instructed us to stop entirely,
                    // wipe the queue and bail out.
                    Log.d(DEBUG_TAG, "Return said to stop, stopping now and abandoning " + getQueueCount() + " Intent(s).");
                    onQueueEmpty(false);
                    clearQueue();
                    stopSelf();
                    return;
                } else if(r == ReturnCode.CONTINUE) {
                    // CONTINUE means processing was a success, so we can yoink
                    // the Intent from the front of the queue and scrap it.
                    Log.d(DEBUG_TAG, "Return said to continue.");
                    onQueueItemProcessed();
                    removeNextIntentFromQueue();
                } else if(r == ReturnCode.PAUSE) {
                    // If we were told to pause, well, pause.  We'll be told to
                    // try again later.
                    Log.d(DEBUG_TAG, "Return said to pause.");
                    onQueuePause(i);
                    onQueueUnload();
                    stopSelf();
                    return;
                }
            }
            // If we got here, then hey!  The thread's done!
            Log.d(DEBUG_TAG, "Processing complete.");
            onQueueEmpty(true);
            stopSelf();
        }
    }

    private void dispatchQueueCountIntent() {
        if(queueCountBroadcastsAllowed()) {
            Intent broadcast = new Intent(ACTION_QUEUE_COUNT);
            broadcast.putExtra(EXTRA_QUEUE_COUNT, getQueueCount());
            broadcast.putExtra(EXTRA_QUEUE_NAME, getQueueName());
            Log.d(DEBUG_TAG, "Dispatching queue count...");
            sendBroadcast(broadcast);
        } else {
            Log.d(DEBUG_TAG, "NOT dispatching queue count (queueCountRequestsAllowed() returned false)...");
        }
    }

    /**
     * Adds an Intent to whatever queue is in use.
     *
     * @param i the Intent to be added
     */
    protected abstract void addIntentToQueue(@NonNull Intent i);

    /**
     * Removes the next Intent from the queue.  This is a removal operation, not
     * a peek.
     *
     * @see #peekNextIntentFromQueue()
     */
    protected abstract void removeNextIntentFromQueue();

    /**
     * Gets the next Intent from the queue.  This is a peek operation, not a
     * removal.
     *
     * @return the next Intent in the queue (may be null)
     * @see #removeNextIntentFromQueue()
     */
    @Nullable
    protected abstract Intent peekNextIntentFromQueue();

    /**
     * Returns the number of Intents left in the queue.  You may want to
     * synchronize this against the instance of the service.  Try not to make
     * this too expensive of an operation; it will get called as part of a while
     * loop as the queue is processed.
     *
     * @return the number of Intents left in the queue
     */
    protected abstract int getQueueCount();

    /**
     * Clears everything out of the queue.  The queue must be empty after this,
     * and any storage used must be cleared out, too.
     */
    protected abstract void clearQueue();

    /**
     * Called whenever a new data Intent comes in and the queue is paused to
     * determine if the queue should resume immediately.  If this returns false,
     * the queue will remain paused until an explicit {@link #COMMAND_RESUME}
     * command Intent is sent.  Note that the queue will always start if a new
     * Intent arrives and the queue is empty.
     *
     * @return true to resume on a new Intent, false to remain paused
     */
    protected abstract boolean resumeOnNewIntent();

    /**
     * <p>
     * Whether or not to allow broadcasts for queue counts.  Overriding this to
     * return false will cause any {@link #COMMAND_QUEUE_COUNT} requests to
     * silently fail and stop broadcasts from being sent by default in
     * {@link #onQueueItemProcessed()}.
     * </p>
     *
     * <p>
     * Note that {@link #getQueueCount()} will still be called after each item
     * is processed as a matter of processing everything.
     * </p>
     *
     * @return true to allow queue counts via command Intents, false to not
     */
    protected boolean queueCountBroadcastsAllowed() {
        return true;
    }

    /**
     * Subclasses get this called every time something from the queue comes in
     * to be processed.  This will not be called on the main thread.  There will
     * be no callback on successful processing of an individual Intent, but
     * {@link #onQueuePause(Intent)} will be called if the queue is paused, and
     * {@link #onQueueEmpty(boolean)} will be called at the end of all processing.
     * 
     * @param i Intent to be processed
     * @return a ReturnCode indicating what the queue should do next
     */
    protected abstract ReturnCode handleIntent(Intent i);

    /**
     * This gets called immediately before {@link #onQueueStart()}.  Here, you
     * want to load the queue into memory, if need be.  It's perfectly
     * acceptable to just make this an empty method if your particular
     * implementation keeps everything on storage and not in memory.  It's also
     * perfectly acceptable to make this final, and chances are it will be by
     * the time an actual concrete implementation comes by.
     */
    protected abstract void onQueueLoad();

    /**
     * This gets called immediately before the first Intent is processed in a
     * given run of QueueService.  That is to say, after the service is started
     * due to an Intent coming in OR every time the service is told to resume
     * after being paused.  {@link #handleIntent(Intent)} will be called after
     * this returns.  This would be a good place to set up wakelocks.
     */
    protected abstract void onQueueStart();
    
    /**
     * <p>
     * This gets called if the queue needs to be paused for some reason.  The
     * Intent that caused the pause will be included.  The thread will be killed
     * after this callback returns.  However, {@link #isThreadAlive()} ()} will
     * return true if called during this callback.  Try not to block it.
     * </p>
     * 
     * <p>
     * Note that you aren't doing the actual pausing here.  This method is just
     * here to do status updates or to inform the user that the queue is paused,
     * which might or might not require more input.  If you need more
     * information as to exactly why the queue was paused, you can always stuff
     * more extras in the Intent during onHandleIntent before it gets here.
     * </p>
     * 
     * <p>
     * Now would be a good time to release that wakelock you made back in
     * {@link #onQueueStart()}.
     * </p>
     * @param i Intent that caused the pause
     */
    protected abstract void onQueuePause(Intent i);

    /**
     * Called after each item is successfully processed.  The default
     * implementation broadcasts the current queue count (if
     * {@link #queueCountBroadcastsAllowed()} returns true).
     */
    protected void onQueueItemProcessed() {
        dispatchQueueCountIntent();
    }

    /**
     * <p>
     * This is called right after the queue is done processing and right before
     * the thread is killed and isn't paused.  The boolean indicates if
     * processing was complete.  If false, it means a {@link ReturnCode#STOP}
     * was received or {@link #COMMAND_ABORT} was sent.  The queue will be
     * emptied AFTER this method returns.
     * </p>
     * 
     * <p>
     * This would be another good place to release that {@link #onQueueStart()}
     * wakelock you've been holding onto.  Onto which you've been holding.
     * </p>
     *  
     * @param allProcessed true if the queue emptied normally, false if it was
     *                     aborted before all Intents were processed
     */
    protected abstract void onQueueEmpty(boolean allProcessed);

    /**
     * This is called at the end of processing, whether it be via pausing or
     * stopping.  You would want to unload the queue back to storage at this
     * point, if applicable.  That is, this should be the opposite of
     * {@link #onQueueLoad()}.
     */
    protected abstract void onQueueUnload();

    /**
     * Returns a name to be used by whatever this queue is using for storage.
     * This could wind up being a filename prefix, an SQLite database name, etc.
     * Make sure it's unique within your package's context.  By default, it will
     * just return the class's canonical name (or its normal name, if
     * getCanonicalName() returns null for some reason).
     *
     * @return a database name
     */
    @NonNull
    protected String getQueueName() {
        String name = getClass().getCanonicalName();
        if(name == null) {
           name = getClass().getName();
        }

        return name;
    }

    /**
     * <p>
     * Serializes the given Intent to a String.  Note that at this point, an
     * Intent is solely used as a means of storing data.  This can be called at
     * any time; maybe it's writing into storage when the Intent comes in, maybe
     * it's only when writing it back to storage in the event of a pause.  You
     * can return whatever String you want here in any format you want, but
     * whatever you return, it'll be your responsibility to deserialize it later
     * in {@link #deserializeIntent(String)}.
     * </p>
     *
     * <p>
     * If this returns null, it will be treated as an empty string.  If you
     * choose not to do anything with it, you may return null from
     * {@link #deserializeIntent(String)} later.
     * </p>
     *
     * @param i Intent to serialize
     * @return a String representation of the vital info in the Intent
     * @see #deserializeIntent(String)
     */
    @Nullable
    protected abstract String serializeIntent(@NonNull Intent i);

    /**
     * <p>
     * Deserializes the given String back into an Intent.  Your responsibility
     * is to pull back whatever you wrote in {@link #serializeIntent(Intent)}
     * and get an Intent out of it that {@link #handleIntent(Intent)} will deal
     * with.
     * </p>
     *
     * <p>
     * If this returns null, this entry in the queue will be ignored and
     * removed.  {@link #handleIntent(Intent)} will NOT be called on it.
     * </p>
     *
     * @param s a String to deserialize
     * @return an Intent formed by deserializing the input String
     * @see #serializeIntent(Intent)
     */
    @Nullable
    protected abstract Intent deserializeIntent(@NonNull String s);
}
