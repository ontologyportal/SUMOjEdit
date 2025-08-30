package com.articulate.sigma.jedit.ac;

public final class ACSignals {
    private ACSignals() {}

    /** Callbacks are optional; wire them if/when you have controller instances. */
    public interface Listener {
        void applyMode(ACMode mode);
        void dismissTransientUI();
    }

    private static Listener listener;

    public static void register(Listener l) { listener = l; }

    public static void onModeChanged(ACMode mode) {
        if (listener != null) {
            listener.dismissTransientUI(); // close any open popup, clear ghost hint
            listener.applyMode(mode);      // re-enable/disable controllers live
        }
    }
}
