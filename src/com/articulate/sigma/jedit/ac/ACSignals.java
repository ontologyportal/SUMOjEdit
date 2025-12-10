package com.articulate.sigma.jedit.ac;

public final class ACSignals {
    private ACSignals() {}

    /** Callbacks are optional; wire them if/when you have controller instances. */
    public interface Listener {
        void applyMode(ACMode mode);
        void dismissTransientUI();
    }

    private static Listener listener;

    /**
     * When testMode == true, tests are allowed to fully control the
     * registered listener without interference from production code.
     * This prevents cross-test/global pollution of the singleton.
     */
    private static boolean testMode = false;

    /**
     * Enable or disable test mode. Intended to be called only from
     * JUnit tests. Production code should never flip this.
     */
    public static void enableTestMode(boolean on) {
        testMode = on;
    }

    public static void register(Listener l) {
        if (testMode) {
            // In test mode we always respect the test's registration
            // and ignore any other attempts to override it.
            listener = l;
            return;
        }
        listener = l;
    }

    public static void onModeChanged(ACMode mode) {
        if (listener != null) {
            listener.dismissTransientUI(); // close any open popup, clear ghost hint
            listener.applyMode(mode);      // re-enable/disable controllers live
        }
    }
}