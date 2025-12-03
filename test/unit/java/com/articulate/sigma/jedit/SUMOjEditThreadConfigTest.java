package com.articulate.sigma.jedit;

import org.gjt.sp.jedit.jEdit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Standalone unit tests for the private thread configuration helpers
 * on {@link SUMOjEdit}:
 *
 *  - getCheckerThreads()
 *  - getKeepAliveSeconds()
 *
 * These methods decide how many background checker threads the plugin
 * uses and how long idle threads may live, based on jEdit properties,
 * system properties and sensible CPU-based defaults.
 *
 * Behaviour under test:
 *
 *  - When no properties are set, getCheckerThreads() falls back to
 *    max(2, availableProcessors - 1).
 *  - A valid jEdit property sumojedit.checker.threads overrides the
 *    default and is used as-is when > 0.
 *  - When the jEdit property is blank/null but the corresponding
 *    system property is set, that system property is used instead.
 *  - Invalid numeric values (e.g. "abc") cause a fall back to the
 *    CPU-based default, ignoring the system property.
 *
 *  - getKeepAliveSeconds() defaults to 30 when no properties are set.
 *  - Valid numeric values from jEdit or system properties are used
 *    and clamped to a minimum of 1.
 *  - Invalid numeric input falls back to 30 seconds.
 *
 * Reflection is used so that the public SUMOjEdit API remains
 * unchanged.
 *
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUMOjEditThreadConfigTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class SUMOjEditThreadConfigTest {

    private String origThreadsProp;
    private String origKeepAliveProp;
    private String origThreadsSys;
    private String origKeepAliveSys;

    private Method getCheckerThreadsMethod;
    private Method getKeepAliveSecondsMethod;

    @Before
    public void setUp() throws Exception {
        // Snapshot original properties so we can restore after each test.
        origThreadsProp    = jEdit.getProperty("sumojedit.checker.threads");
        origKeepAliveProp  = jEdit.getProperty("sumojedit.checker.keepAliveSec");
        origThreadsSys     = System.getProperty("sumojedit.checker.threads");
        origKeepAliveSys   = System.getProperty("sumojedit.checker.keepAliveSec");

        // Reflectively obtain the private static helpers:
        //   private static int getCheckerThreads()
        //   private static int getKeepAliveSeconds()
        getCheckerThreadsMethod = SUMOjEdit.class.getDeclaredMethod("getCheckerThreads");
        getCheckerThreadsMethod.setAccessible(true);

        getKeepAliveSecondsMethod = SUMOjEdit.class.getDeclaredMethod("getKeepAliveSeconds");
        getKeepAliveSecondsMethod.setAccessible(true);
    }

    @After
    public void tearDown() {
        // Restore jEdit properties
        setOrClearJEditProperty("sumojedit.checker.threads", origThreadsProp);
        setOrClearJEditProperty("sumojedit.checker.keepAliveSec", origKeepAliveProp);

        // Restore system properties
        setOrClearSystemProperty("sumojedit.checker.threads", origThreadsSys);
        setOrClearSystemProperty("sumojedit.checker.keepAliveSec", origKeepAliveSys);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void setOrClearJEditProperty(String key, String value) {
        if (value == null) {
            jEdit.setProperty(key, null);
        } else {
            jEdit.setProperty(key, value);
        }
    }

    private void setOrClearSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private int invokeGetCheckerThreads() throws Exception {
        return (Integer) getCheckerThreadsMethod.invoke(null);
    }

    private int invokeGetKeepAliveSeconds() throws Exception {
        return (Integer) getKeepAliveSecondsMethod.invoke(null);
    }

    // ---------------------------------------------------------------------
    // getCheckerThreads() tests
    // ---------------------------------------------------------------------

    /**
     * When no jEdit or system properties are set, getCheckerThreads()
     * should fall back to max(2, availableProcessors - 1).
     */
    @Test
    public void testGetCheckerThreadsDefaultUsesCpuBasedFallback() throws Exception {
        jEdit.setProperty("sumojedit.checker.threads", null);
        System.clearProperty("sumojedit.checker.threads");

        int expected = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        int actual = invokeGetCheckerThreads();

        assertEquals(expected, actual);
    }

    /**
     * A valid numeric jEdit property should be used as-is when > 0,
     * overriding the CPU-based default.
     */
    @Test
    public void testGetCheckerThreadsUsesValidJEditProperty() throws Exception {
        jEdit.setProperty("sumojedit.checker.threads", "7");
        System.clearProperty("sumojedit.checker.threads");

        int actual = invokeGetCheckerThreads();
        assertEquals(7, actual);
    }

    /**
     * When the jEdit property is blank but the system property is set,
     * getCheckerThreads() should fall back to the system property value.
     */
    @Test
    public void testGetCheckerThreadsUsesSystemPropertyWhenJEditBlank() throws Exception {
        jEdit.setProperty("sumojedit.checker.threads", "   "); // blank
        System.setProperty("sumojedit.checker.threads", "5");

        int actual = invokeGetCheckerThreads();
        assertEquals(5, actual);
    }

    /**
     * An invalid numeric jEdit property (e.g. "abc") causes the method
     * to fall back to the CPU-based default, ignoring any system
     * property.
     */
    @Test
    public void testGetCheckerThreadsFallsBackOnInvalidJEditProperty() throws Exception {
        jEdit.setProperty("sumojedit.checker.threads", "not-a-number");
        System.setProperty("sumojedit.checker.threads", "9"); // should be ignored

        int expected = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        int actual = invokeGetCheckerThreads();

        assertEquals(expected, actual);
    }

    // ---------------------------------------------------------------------
    // getKeepAliveSeconds() tests
    // ---------------------------------------------------------------------

    /**
     * With no properties set, getKeepAliveSeconds() should default to 30.
     */
    @Test
    public void testGetKeepAliveSecondsDefaultIsThirty() throws Exception {
        jEdit.setProperty("sumojedit.checker.keepAliveSec", null);
        System.clearProperty("sumojedit.checker.keepAliveSec");

        int actual = invokeGetKeepAliveSeconds();
        assertEquals(30, actual);
    }

    /**
     * A valid numeric jEdit property should be used and clamped to a
     * minimum of 1 second.
     */
    @Test
    public void testGetKeepAliveSecondsUsesJEditPropertyAndClampsToOne() throws Exception {
        // Explicitly set 5 seconds
        jEdit.setProperty("sumojedit.checker.keepAliveSec", "5");
        System.clearProperty("sumojedit.checker.keepAliveSec");
        int seconds = invokeGetKeepAliveSeconds();
        assertEquals(5, seconds);

        // Zero or negative values should be clamped to 1
        jEdit.setProperty("sumojedit.checker.keepAliveSec", "0");
        seconds = invokeGetKeepAliveSeconds();
        assertEquals(1, seconds);

        jEdit.setProperty("sumojedit.checker.keepAliveSec", "-10");
        seconds = invokeGetKeepAliveSeconds();
        assertEquals(1, seconds);
    }

    /**
     * When the jEdit property is blank/null but a system property is
     * present, getKeepAliveSeconds() should use the system property.
     */
    @Test
    public void testGetKeepAliveSecondsUsesSystemPropertyWhenJEditBlank() throws Exception {
        jEdit.setProperty("sumojedit.checker.keepAliveSec", "   "); // blank
        System.setProperty("sumojedit.checker.keepAliveSec", "12");

        int seconds = invokeGetKeepAliveSeconds();
        assertEquals(12, seconds);
    }

    /**
     * Invalid numeric values should cause getKeepAliveSeconds() to fall
     * back to 30 seconds.
     */
    @Test
    public void testGetKeepAliveSecondsFallsBackOnInvalidProperty() throws Exception {
        jEdit.setProperty("sumojedit.checker.keepAliveSec", "abc");
        System.setProperty("sumojedit.checker.keepAliveSec", "99"); // ignored on parse failure

        int seconds = invokeGetKeepAliveSeconds();
        assertEquals(30, seconds);
    }
}