package com.articulate.sigma.jedit;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.edt.GuiTask;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.Test;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/**
 * GUI-dependent test for the TPTP "format buffer" diagnostics path.
 *
 * This exercises the same error reporting wiring that
 * {@link SUMOjEdit#tptpFormatBuffer()} uses internally:
 *
 *   tptp4X stderr/stdout  -> parseTptpOutput(...)
 *                        -> ErrRec list
 *                        -> addErrorsDirect(...)
 *                        -> DefaultErrorSource
 *                        -> ErrorList-style JTable
 *
 * Instead of invoking the external tptp4X binary, this test feeds a
 * synthetic TPTP file plus a handcrafted tptp4X-style diagnostic line
 * into the private helper {@code parseTptpOutput}.  The resulting
 * {@code ErrRec} is pushed through {@code addErrorsDirect}, and a
 * {@link TestErrorListPanel} verifies that both the adjusted line
 * number and the formatted message (including a snippet from the
 * offending TPTP clause) appear as expected in the GUI table.
 *
 * The intent is to provide a light-weight integration harness for the
 * TPTP buffer formatting error path without depending on an actual
 * tptp4X installation.
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.TPTPFormatBufferGUITest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class TPTPFormatBufferGUITest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;
    private SUMOjEdit sje;
    private TestErrorListPanel panel;

    @Override
    protected void onSetUp() {
        // Fresh SUMOjEdit instance + isolated DefaultErrorSource
        sje = new SUMOjEdit();
        sje.errsrc = new DefaultErrorSource(SUMOjEdit.class.getName(), null);
        ErrorSource.registerErrorSource(sje.errsrc);

        // Ensure view is null so snippet resolution falls back to
        // safeSnippetFromFile(...) rather than touching jEdit buffers.
        try {
            Field viewField = SUMOjEdit.class.getDeclaredField("view");
            viewField.setAccessible(true);
            viewField.set(sje, null);
        } catch (Exception ignore) {
            // If we cannot set it, the path under test still works as
            // long as snippetFromActiveBufferOrFile falls back cleanly.
        }

        // Build a simple ErrorList-like UI around the DefaultErrorSource.
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new TestErrorListPanel(sje.errsrc);
                JFrame f = new JFrame("TPTP Format Buffer GUI Test");
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.getContentPane().add(panel);
                f.pack();
                f.setLocationRelativeTo(null);
                return f;
            }
        });

        window = new FrameFixture(robot(), frame);
        window.show();
    }

    @Override
    protected void onTearDown() {
        if (window != null) {
            window.cleanUp();
        }
        if (sje != null && sje.errsrc != null) {
            ErrorSource.unregisterErrorSource(sje.errsrc);
            sje.errsrc.clear();
        }
        sje = null;
    }

    /**
     * Build a temporary TPTP file containing several declarations,
     * then feed a synthetic tptp4X diagnostic line through the same
     * helpers that {@code tptpFormatBuffer()} uses to convert
     * diagnostics into {@link ErrorSource.Error} entries.
     *
     * Expectations:
     *
     *  - {@code parseTptpOutput(...)} notices the "continuing with
     *    'formula_bad, ...'" pattern, resolves the actual line of the
     *    offending {@code tff(...)} declaration via
     *    {@code findFormulaLine(...)} and returns an {@code ErrRec}
     *    with that adjusted 0-based line index.
     *  - {@code addErrorsDirect(...)} appends a snippet from the
     *    offending line to the message using {@code appendSnippet}
     *    and materialises the error into {@code DefaultErrorSource}.
     *  - {@link TestErrorListPanel} shows one row whose "Line" column
     *    matches the human-readable (1-based) line of the bad formula
     *    and whose "Message" column includes both the cleaned base
     *    message and the TPTP clause snippet.
     */
    @Test
    public void testTptpFormatDiagnosticsPopulateErrorPanel() throws Exception {
        // 1) Create a temporary TPTP file with a clearly-identified
        //    "bad" formula in the middle.
        Path tmp = Files.createTempFile("tptp-format", ".tff");
        try {
            String content = String.join("\n",
                    "% header comment",
                    "tff(formula_ok, type, (a => b)).",
                    "tff(formula_bad, type, (a & [ b )).",
                    "tff(formula_after, type, (b => c))."
            );
            Files.writeString(tmp, content);

            String filePath = tmp.toString();

            // 2) Reflectively obtain the private helpers we need:
            //    parseTptpOutput(String,String,int) and addErrorsDirect(List<ErrRec>).
            Method parseTptpOutput =
                    SUMOjEdit.class.getDeclaredMethod("parseTptpOutput",
                            String.class, String.class, int.class);
            parseTptpOutput.setAccessible(true);

            Method addErrorsDirect =
                    SUMOjEdit.class.getDeclaredMethod("addErrorsDirect", List.class);
            addErrorsDirect.setAccessible(true);

            // 3) Build a synthetic tptp4X-style diagnostic that:
            //    - Uses colon-separated line/column info.
            //    - Mentions "continuing with 'formula_bad, ...'".
            //    - Includes a trailing " — tff(...)" which cleanErrorMessage()
            //      should strip from the user-facing message.
            String diag =
                    "tptp-format.tff:10:5: Token 'tff' continuing with 'formula_bad, type, (a & [ b )' — " +
                    "tff(formula_bad, type, (a & [ b )).";

            @SuppressWarnings("unchecked")
            List<Object> recs = (List<Object>) parseTptpOutput.invoke(
                    sje, filePath, diag, ErrorSource.ERROR);

            // We expect exactly one ErrRec.
            assertEquals("Expected exactly one parsed diagnostic", 1, recs.size());

            // 4) Push the ErrRec through addErrorsDirect(), which will
            //    append snippets and populate the DefaultErrorSource.
            addErrorsDirect.invoke(sje, recs);

            // 5) Refresh the Swing panel from the DefaultErrorSource on the EDT.
            GuiActionRunner.execute(new GuiTask() {
                @Override
                protected void executeInEDT() {
                    panel.refreshFromSource();
                }
            });

            // 6) Assert on what the GUI shows.
            var tableFixture = window.table("errorTable");
            tableFixture.requireRowCount(1);

            javax.swing.JTable table = tableFixture.target();
            // Column index 1 is the "Line" column (1-based); the "bad"
            // formula is on third line in the file (index 2).
            Integer line0 = (Integer) table.getValueAt(0, 1);
            assertEquals("ErrorList should display 1-based line of offending formula",
                    Integer.valueOf(3), line0);

            // Column index 2 is the "Message" column.
            String msg0 = (String) table.getValueAt(0, 2);

            // The cleaned base message should no longer contain the raw tff(...)
            // tail but must still mention "Token 'tff'" and "continuing with 'formula_bad".
            assertTrue("Base message should mention the unexpected token",
                    msg0.contains("Token 'tff'"));
            assertTrue("Base message should retain the 'continuing with' context",
                    msg0.contains("continuing with 'formula_bad"));

            // The snippet separator " — " followed by the offending clause
            // should be present as added by appendSnippet(...).
            assertTrue("Message should include an em-dash separator to the snippet",
                    msg0.contains(" — "));
            assertTrue("Snippet should include the bad TPTP clause text",
                    msg0.contains("tff(formula_bad, type, (a & [ b ))."));
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {
            }
        }
    }
}