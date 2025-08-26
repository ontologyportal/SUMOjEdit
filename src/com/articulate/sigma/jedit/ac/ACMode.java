package com.articulate.sigma.jedit.ac;

import org.gjt.sp.jedit.jEdit;

public enum ACMode {
    OFF,
    GHOST_ONLY,
    DROPDOWN_ONLY,
    BOTH;

    public static final String PROP_KEY = "sumojedit.ac.mode";

    public static ACMode current() {
        String s = jEdit.getProperty(PROP_KEY);
        if (s == null || s.isBlank()) return BOTH; // default
        try { return ACMode.valueOf(s); } catch (IllegalArgumentException e) { return BOTH; }
    }

    public static void save(ACMode mode) {
        jEdit.setProperty(PROP_KEY, mode.name());
        // Persist to disk
        jEdit.saveSettings();
        // Let AC code apply new behavior immediately
        ACSignals.onModeChanged(mode);
    }

    public boolean ghostEnabled()    { return this == GHOST_ONLY    || this == BOTH; }
    public boolean dropdownEnabled() { return this == DROPDOWN_ONLY || this == BOTH; }
    public boolean enabled()         { return this != OFF; }
}
