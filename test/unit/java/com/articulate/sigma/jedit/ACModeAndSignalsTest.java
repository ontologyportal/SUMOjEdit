package com.articulate.sigma.jedit;

import com.articulate.sigma.jedit.ac.ACMode;
import com.articulate.sigma.jedit.ac.ACSignals;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for the AutoComplete mode flags and signalling helpers:
 *
 *  - {@link ACMode} boolean helpers (ghostEnabled, dropdownEnabled, enabled)
 *  - {@link ACSignals} listener dispatch semantics
 *
 * These tests deliberately avoid calling {@link ACMode#current()} and
 * {@link ACMode#save(ACMode)}, since those hit jEdit's property store and
 * settings persistence. We only exercise pure logic and the listener wiring.
 *
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.ac.ACModeAndSignalsTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class ACModeAndSignalsTest {

    private static final class CapturingListener implements ACSignals.Listener {
        final List<String> calls = new ArrayList<>();
        ACMode lastMode = null;

        @Override
        public void applyMode(ACMode mode) {
            calls.add("apply");
            lastMode = mode;
        }

        @Override
        public void dismissTransientUI() {
            calls.add("dismiss");
        }
    }

    @Before
    public void setUp() {
        // Ensure a clean slate before each test.
        ACSignals.register(null);
    }

    @After
    public void tearDown() {
        // Do not leak listeners into other tests.
        ACSignals.register(null);
    }

    // ---------------------------------------------------------------------
    // ACMode flag tests
    // ---------------------------------------------------------------------

    @Test
    public void testACModeOffFlags() {
        ACMode m = ACMode.OFF;
        assertFalse(m.enabled());
        assertFalse(m.ghostEnabled());
        assertFalse(m.dropdownEnabled());
    }

    @Test
    public void testACModeGhostOnlyFlags() {
        ACMode m = ACMode.GHOST_ONLY;
        assertTrue(m.enabled());
        assertTrue(m.ghostEnabled());
        assertFalse(m.dropdownEnabled());
    }

    @Test
    public void testACModeDropdownOnlyFlags() {
        ACMode m = ACMode.DROPDOWN_ONLY;
        assertTrue(m.enabled());
        assertFalse(m.ghostEnabled());
        assertTrue(m.dropdownEnabled());
    }

    @Test
    public void testACModeBothFlags() {
        ACMode m = ACMode.BOTH;
        assertTrue(m.enabled());
        assertTrue(m.ghostEnabled());
        assertTrue(m.dropdownEnabled());
    }

    // ---------------------------------------------------------------------
    // ACSignals dispatch tests
    // ---------------------------------------------------------------------

    @Test
    public void testOnModeChangedWithNoListenerDoesNothing() {
        // Should not throw even if no listener is registered.
        ACSignals.onModeChanged(ACMode.GHOST_ONLY);
    }

    @Test
    public void testOnModeChangedInvokesListenerInOrder() {
        CapturingListener listener = new CapturingListener();
        ACSignals.register(listener);

        ACSignals.onModeChanged(ACMode.DROPDOWN_ONLY);

        // Expect two calls: dismiss first, then apply
        assertEquals(2, listener.calls.size());
        assertEquals("dismiss", listener.calls.get(0));
        assertEquals("apply", listener.calls.get(1));
        assertEquals(ACMode.DROPDOWN_ONLY, listener.lastMode);
    }

    @Test
    public void testRegisterReplacesPreviousListener() {
        CapturingListener first = new CapturingListener();
        CapturingListener second = new CapturingListener();

        ACSignals.register(first);
        ACSignals.onModeChanged(ACMode.OFF);

        ACSignals.register(second);
        ACSignals.onModeChanged(ACMode.BOTH);

        // First listener should have seen only the first call.
        assertEquals(2, first.calls.size());
        assertEquals("dismiss", first.calls.get(0));
        assertEquals("apply", first.calls.get(1));
        assertEquals(ACMode.OFF, first.lastMode);

        // Second listener should have seen only the second call.
        assertEquals(2, second.calls.size());
        assertEquals("dismiss", second.calls.get(0));
        assertEquals("apply", second.calls.get(1));
        assertEquals(ACMode.BOTH, second.lastMode);
    }
}