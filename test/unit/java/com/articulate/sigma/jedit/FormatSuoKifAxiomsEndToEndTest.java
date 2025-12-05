package com.articulate.sigma.jedit;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.articulate.sigma.jedit.SUMOjEdit;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * End-to-end tests for the "Format SUO-KIF Axioms" path.
 *
 * These tests drive SUMOjEdit's private {@code formatSelectBody(String)}
 * which in turn:
 *
 *   - validates the editor contents via {@code checkEditorContents(...)}
 *   - runs the real {@code parseKif(...)} pipeline
 *   - pretty-prints the parsed formulas via {@code kif.formulasOrdered}
 *
 * We deliberately set {@code kif.filename} before invoking the method
 * so that the logic:
 *
 *   if (StringUtil.emptyString(kif.filename))
 *       kif.filename = view.getBuffer().getPath();
 *
 * never requires a real jEdit View or Buffer in this test context.
 * The focus is on SUMOjEdit's contributions:
 *
 *   - no warnings or errors are left over for valid input
 *   - formatting is idempotent on its own output
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class FormatSUOKIFAxiomsEndToEndTest {

    private SUMOjEdit sje;

    @Before
    public void setUp() {
        sje = new SUMOjEdit();
        sje.errsrc = new DefaultErrorSource(sje.getClass().getName(), null);
        ErrorSource.registerErrorSource(sje.errsrc);
    }

    @After
    public void tearDown() {
        if (sje != null && sje.errsrc != null) {
            ErrorSource.unregisterErrorSource(sje.errsrc);
            sje.errsrc.clear();
        }
        sje = null;
    }

    /**
     * Utility to clear KIF state between runs so we can make clean
     * assertions about warnings, errors and formula ordering.
     */
    private void clearKifState() {
        sje.kif.warningSet.clear();
        sje.kif.errorSet.clear();
        sje.kif.formulas.clear();
        sje.kif.formulasOrdered.clear();
    }

    /**
     * For a valid but poorly formatted SUO-KIF snippet, formatSelectBody(...)
     * should:
     *
     *   - return non-null, non-empty output
     *   - leave kif.warningSet and kif.errorSet empty
     *   - populate kif.formulasOrdered with the expected formula count
     */
    @Test
    public void testFormatValidUnformattedKifHasNoDiagnostics() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod(
                "formatSelectBody", String.class);
        m.setAccessible(true);

        // Two simple axioms with messy spacing and blank lines.
        String unformatted =
                "   (instance   Human   Animal)\n" +
                "\n" +
                "    (subclass   Human   Primate)\n";

        // Ensure SUMOjEdit never tries to consult view.getBuffer().getPath().
        sje.kif.filename = "unformatted.kif";
        clearKifState();

        String formatted = (String) m.invoke(sje, unformatted);

        assertNotNull("Formatted output should not be null for valid KIF", formatted);
        assertFalse("Formatted output should not be blank", formatted.trim().isEmpty());

        // parseKif(...) is called inside formatSelectBody(...).
        // For valid input we expect no warnings or errors and
        // a non-empty formulasOrdered map.
        assertTrue("No KIF warnings expected after formatting",
                sje.kif.warningSet.isEmpty());
        assertTrue("No KIF errors expected after formatting",
                sje.kif.errorSet.isEmpty());

        // We fed two axioms; we expect two formulas to be present.
        assertEquals("Expected two formulas after parsing formatted KIF",
                2, sje.kif.formulasOrdered.size());
    }

    /**
     * Once content has been formatted, running it back through the
     * formatter should be a no-op. This captures the requirement that
     * already-formatted axioms remain unchanged.
     */
    @Test
    public void testFormatSelectBodyIsIdempotentOnFormattedOutput() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod(
                "formatSelectBody", String.class);
        m.setAccessible(true);

        String input =
                "(=> (instance ?X Human) (instance ?X Animal))\n";

        // First formatting pass on the shared fixture.
        sje.kif.filename = "idempotent.kif";
        clearKifState();
        String once = (String) m.invoke(sje, input);

        assertNotNull("First formatting pass should produce output", once);
        assertFalse("First formatting output should not be blank", once.trim().isEmpty());
        assertTrue("No KIF warnings expected after first formatting pass",
                sje.kif.warningSet.isEmpty());
        assertTrue("No KIF errors expected after first formatting pass",
                sje.kif.errorSet.isEmpty());

        // Second formatting pass on a fresh SUMOjEdit instance to avoid
        // any KIF parser state bleed-through between runs.
        SUMOjEdit second = new SUMOjEdit();
        second.errsrc = new DefaultErrorSource(second.getClass().getName(), null);
        ErrorSource.registerErrorSource(second.errsrc);
        try {
            Method m2 = SUMOjEdit.class.getDeclaredMethod(
                    "formatSelectBody", String.class);
            m2.setAccessible(true);

            second.kif.filename = "idempotent.kif";
            second.kif.warningSet.clear();
            second.kif.errorSet.clear();
            second.kif.formulas.clear();
            second.kif.formulasOrdered.clear();

            String twice = (String) m2.invoke(second, once);

            assertNotNull("Second formatting pass should also produce output", twice);
            assertEquals("Formatting should be idempotent on its own output",
                    once, twice);
            assertTrue("No KIF warnings expected after second formatting pass",
                    second.kif.warningSet.isEmpty());
            assertTrue("No KIF errors expected after second formatting pass",
                    second.kif.errorSet.isEmpty());
        } finally {
            ErrorSource.unregisterErrorSource(second.errsrc);
            second.errsrc.clear();
        }
    }
}