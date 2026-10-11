import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * CounterThreads demonstrates thread coordination in Java.
 * <p>
 * Two threads act as counters. Thread one counts up from 0 to 20. Once thread one
 * reaches 20, thread two counts down from 20 to 0. A lock protects the shared
 * counter, and a condition hands control from thread one to thread two so that
 * thread two sleeps instead of busy-waiting.
 *
 * @author Connor (Lightbearer77)
 */
public class CounterThreads {

    private static final int COUNT_MAX = 20;    // Upper bound for the count-up thread.
    private static final int COUNT_MIN = 0;     // Lower bound for the count-down thread.
    private static final int LABEL_WIDTH = 21;  // Column width so the colons line up.
    private static final String LINE_FORMAT = "%-" + LABEL_WIDTH + "s: %d%n";

    // Immutable string constants: the labels cannot be altered by either thread.
    private static final String COUNT_UP_LABEL = "Thread 1 (count up)";
    private static final String COUNT_DOWN_LABEL = "Thread 2 (count down)";

    /**
     * State shared by both threads. Every field is read or written only while
     * holding the lock.
     */
    private static final class SharedState {
        // Guards counter and countUpFinished.
        private final ReentrantLock lock = new ReentrantLock();
        // Signals that the count-up phase is done.
        private final Condition handoff = lock.newCondition();
        // The value both threads operate on.
        private int counter = 0;
        // Predicate for the condition.
        private boolean countUpFinished = false;
    }

    /**
     * Prints one labeled count line. The caller must already hold the lock so
     * that lines from the two threads can never interleave.
     *
     * @param label the name of the thread printing the value
     * @param value the counter value to print
     */
    private static void printCount(String label, int value) {
        System.out.printf(LINE_FORMAT, label, value);
    }

    /**
     * Thread one: counts from COUNT_MIN up to COUNT_MAX, then signals thread two.
     *
     * @param state the shared state; accessed only while holding its lock
     */
    private static void countUp(SharedState state) {
        for (int i = COUNT_MIN; i <= COUNT_MAX; i++) {
            state.lock.lock();
            try {
                state.counter = i;
                printCount(COUNT_UP_LABEL, state.counter);
            } finally {
                state.lock.unlock();  // Always released, even on an exception.
            }
        }

        state.lock.lock();
        try {
            state.countUpFinished = true;
            state.handoff.signal();  // Wake thread two now that 20 has been reached.
        } finally {
            state.lock.unlock();
        }
    }

    /**
     * Thread two: waits for thread one to finish, then counts down to COUNT_MIN.
     *
     * @param state the shared state; accessed only while holding its lock
     */
    private static void countDown(SharedState state) {
        state.lock.lock();
        try {
            // The loop guards against spurious wakeups, like C++'s predicate form.
            while (!state.countUpFinished) {
                state.handoff.awaitUninterruptibly();
            }
        } finally {
            state.lock.unlock();
        }

        for (int i = COUNT_MAX; i >= COUNT_MIN; i--) {
            state.lock.lock();
            try {
                state.counter = i;
                printCount(COUNT_DOWN_LABEL, state.counter);
            } finally {
                state.lock.unlock();
            }
        }
    }

    /**
     * Program entry point. Starts both threads and waits for them to finish.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        SharedState state = new SharedState();

        // Thread two is started first on purpose. It must block until thread one
        // finishes, so the order comes from the condition, not from start order.
        Thread downThread = new Thread(() -> countDown(state), "count-down");
        Thread upThread = new Thread(() -> countUp(state), "count-up");

        downThread.start();
        upThread.start();

        try {
            upThread.join();
            downThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  // Preserve the interrupt status.
            System.err.println("Main thread was interrupted while waiting.");
            return;
        }

        System.out.println("Both threads finished. Final counter value: "
                + state.counter);
    }
}
