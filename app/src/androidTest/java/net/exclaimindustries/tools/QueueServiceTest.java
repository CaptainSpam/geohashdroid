package net.exclaimindustries.tools;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ServiceTestRule;
import android.util.Log;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

/**
 * This tests the basic flow of {@link QueueService}.  That is, adding Intents, running
 * through the queue, pausing and stopping as need be, resuming as appropriate,
 * etc.  This does not do anything that involves serialization or storage; that
 * will be covered in their own tests with the specific implementations.
 */
@RunWith(AndroidJUnit4.class)
public class QueueServiceTest {
    @Rule
    public final ServiceTestRule mServiceRule = new ServiceTestRule();

    /**
     * An implementation of {@link QueueService} solely used for this test.
     * Specifically, it tests the basic flow of running through the queue, not
     * any of the storage and serialization parts.  If this service dies early,
     * this is a very bad thing.
     */
    private static class TestQueueService extends QueueService {
        private static final String DEBUG_TAG = "TestQueueService";

        /** The code an Intent will return, stored as a String extra. */
        static final String TEST_RETURN_CODE = "TestReturnCode";
        /** Additional data an Intent will carry, stored as an int extra. */
        static final String TEST_DATA = "TestData";

        /** The active queue. */
        Queue<Intent> mQueue = new ConcurrentLinkedQueue<>();

        /**
         * The output queue.  Every Intent that passes through will go in here
         * IF it returns a CONTINUE code.
         */
        List<Intent> mOutputQueue = new ArrayList<>();

        boolean resumeOnNewIntent = false;

        @Override
        protected void addIntentToQueue(@NonNull Intent i) {
            Log.d(DEBUG_TAG, "addIntentToQueue...");
            mQueue.add(i);
        }

        @Override
        protected void removeNextIntentFromQueue() {
            Log.d(DEBUG_TAG, "removeNextIntentFromQueue...");
            mQueue.remove();
        }

        @Nullable
        @Override
        protected Intent peekNextIntentFromQueue() {
            Log.d(DEBUG_TAG, "peekNextIntentFromQueue...");
            return mQueue.peek();
        }

        @Override
        protected int getQueueCount() {
            Log.d(DEBUG_TAG, "getQueueCount (returning " + mQueue.size() + ")...");
            return mQueue.size();
        }

        @Override
        protected void clearQueue() {
            Log.d(DEBUG_TAG, "clearQueue...");
            mQueue.clear();
        }

        @Override
        protected boolean resumeOnNewIntent() {
            Log.d(DEBUG_TAG, "resumeOnNewIntent (returning " + resumeOnNewIntent + ")...");
            return resumeOnNewIntent;
        }

        @Override
        protected ReturnCode handleIntent(Intent i) {
            Log.d(DEBUG_TAG, "handleIntent (has code of " + i.getStringExtra(TEST_RETURN_CODE) + ")...");

            // The return code is baked into the Intent.  We'll just return
            // whatever it says to return.  If this throws an exception, that's
            // super bad and it SHOULD blow up the test.
            ReturnCode code = ReturnCode.valueOf(i.getStringExtra(TEST_RETURN_CODE));

            if(code == ReturnCode.CONTINUE) {
                // Only add it to the output queue if it's done.
                mOutputQueue.add(i);
            }

            return code;
        }

        @Override
        protected void onQueueLoad() {
            // The queue should never need to be loaded from storage.
            Log.d(DEBUG_TAG, "onQueueLoad...");
        }

        @Override
        protected void onQueueStart() {
            Log.d(DEBUG_TAG, "onQueueStart...");
        }

        @Override
        protected void onQueuePause(Intent i) {
            Log.d(DEBUG_TAG, "onQueuePause...");
        }

        @Override
        protected void onQueueEmpty(boolean allProcessed) {
            Log.d(DEBUG_TAG, "onQueueEmpty (allProcessed: " + allProcessed + ")...");
        }

        @Override
        protected void onQueueUnload() {
            Log.d(DEBUG_TAG, "onQueueUnload...");
        }

        @Nullable
        @Override
        protected String serializeIntent(@NonNull Intent i) {
            // The queue should never need to be serialized.
            Log.d(DEBUG_TAG, "serializeIntent (returning null)...");
            return null;
        }

        @Nullable
        @Override
        protected Intent deserializeIntent(@NonNull String s) {
            // Or deserialized, for that matter.
            Log.d(DEBUG_TAG, "deserializeIntent (from " + s + ")...");
            return null;
        }
    }

    @Before
    public void theBeforeTimes() throws Exception {
        initService();
    }

    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = ApplicationProvider.getApplicationContext();

        assertEquals("net.exclaimindustries.geohashdroid", appContext.getPackageName());
    }

    @Test
    public void doesTheThing() {
    }

    private void initService() throws TimeoutException {
        mServiceRule.startService(
                new Intent(ApplicationProvider.getApplicationContext(),
                        TestQueueService.class));
    }
}
