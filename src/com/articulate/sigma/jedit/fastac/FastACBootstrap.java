package com.articulate.sigma.jedit.fastac;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FastACBootstrap {

    // Toggle: set to true if you want the old dropdown popup enabled again
    private static final boolean ENABLE_POPUP = false;

    private static final AtomicBoolean didRun = new AtomicBoolean(false);

    private FastACBootstrap() {}

    /** Safe to call from anywhere, many times; it will only attach once. */
    public static void runOnce() {
        // Disable the old dropdown if the flag is off
        if (!ENABLE_POPUP) return;

        if (!didRun.compareAndSet(false, true)) return; // already ran

        // Defer until the UI is up to ensure windows/components exist
        SwingUtilities.invokeLater(() -> {
            System.out.println("[FastAC] Bootstrapping autocomplete…");
            // Note: keep your existing attach call as-is.
            // If your class is actually FastACAutoattach (lower 'a'), use that name here.
            FastACAutoAttach.attachEverywhere(SumoWords.all());
        });
    }
}
