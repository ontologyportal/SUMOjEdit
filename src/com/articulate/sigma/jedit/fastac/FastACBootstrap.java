package com.articulate.sigma.jedit.fastac;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FastACBootstrap {
    private static final AtomicBoolean didRun = new AtomicBoolean(false);

    private FastACBootstrap() {}

    /** Safe to call from anywhere, many times; it will only attach once. */
    public static void runOnce() {
        if (!didRun.compareAndSet(false, true)) return; // already ran
        // Defer until the UI is up to ensure windows/components exist
        SwingUtilities.invokeLater(() -> {
            System.out.println("[FastAC] Bootstrapping autocomplete…");
            FastACAutoAttach.attachEverywhere(SumoWords.all());
        });
    }
}
