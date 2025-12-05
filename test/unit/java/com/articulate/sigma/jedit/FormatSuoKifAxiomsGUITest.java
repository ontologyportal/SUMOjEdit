package com.articulate.sigma.jedit;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiTask;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.Test;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.*;

/**
 * GUI-dependent test for the “Format SUO-KIF Axioms” warning path.
 *
 * This focuses on the user-visible behaviour when the selection is
 * too short for formatting.  Internally, {@link SUMOjEdit} uses
 * {@code checkEditorContents(...)} to enqueue a diagnostic into the
 * private {@code _pendingErrs} list, and then {@code addErrorsBatch}
 * to materialise those diagnostics as {@link ErrorSource.Error}
 * entries.  This test drives that pipeline and verifies that the
 * warning message (“Please highlight something”) actually appears
 * in the ErrorList-style GUI table.
 *
 * The low-level logic of {@code checkEditorContents} and the pure
 * formatting helpers are already covered by the standalone
 * {@link FormatSUOKIFAxiomsTest} and
 * {@link FormatSUOKIFAxiomsEndToEndTest}; here we only assert
 * the GUI integration layer.
 *
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.FormatSuoKifAxiomsGUITest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class FormatSUOKIFAxiomsGUITest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;
    private SUMOjEdit sje;
    private TestErrorListPanel panel;

    @Override
    protected void onSetUp() {
        // Fresh SUMOjEdit instance + isolated DefaultErrorSource
        sje = new SUMOjEdit();
        sje.errsrc = new DefaultErrorSource(SUMOjEdit.class.getName(), null);
        ErrorSource.registerErrorSource(sje.errsrc);

        // Make sure view is null so no jEdit View/Buffer is accidentally touched.
        try {
            Field viewField = SUMOjEdit.class.getDeclaredField("view");
            viewField.setAccessible(true);
            viewField.set(sje, null);
        } catch (Exception ignore) {
            // If we can't set it, tests still pass as long as the code path
            // under test doesn't dereference view.
        }

        // Build a simple ErrorList-like UI around the DefaultErrorSource.
        JFrame frame = GuiActionRunner.execute(new org.assertj.swing.edt.GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new TestErrorListPanel(sje.errsrc);
                JFrame f = new JFrame("Format SUO-KIF Axioms GUI Test");
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
     * When the “Format SUO-KIF Axioms” action is invoked with an
     * empty or 1-character selection, {@code checkEditorContents}
     * should fail and queue a single diagnostic with the message
     * “Please highlight something”.  After batching into the
     * {@link DefaultErrorSource}, that diagnostic must appear as a
     * row in the ErrorList table.
     */
    @Test
    public void testTooShortSelectionWarningAppearsInErrorPanel() throws Exception {
        // Reflectively obtain the private helpers we need.
        Method checkEditorContents =
                SUMOjEdit.class.getDeclaredMethod("checkEditorContents", String.class, String.class);
        checkEditorContents.setAccessible(true);

        Method addErrorsBatch =
                SUMOjEdit.class.getDeclaredMethod("addErrorsBatch", List.class);
        addErrorsBatch.setAccessible(true);

        Field pendField = SUMOjEdit.class.getDeclaredField("_pendingErrs");
        pendField.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Object> pending = (List<Object>) pendField.get(sje);
        pending.clear();

        String warnMsg = "Please highlight something";

        // 1) Drive the same validation logic as the standalone test:
        //    a one-character selection should fail and enqueue a warning.
        boolean resultShort = (Boolean) checkEditorContents.invoke(sje, "a", warnMsg);
        assertFalse("checkEditorContents should reject too-short selections", resultShort);
        assertEquals("Expected one pending diagnostic for single character", 1, pending.size());

        // 2) Batch the pending diagnostics into the DefaultErrorSource,
        //    exactly as the real GUI action does.
        addErrorsBatch.invoke(sje, pending);

        // 3) Refresh the Swing panel from the DefaultErrorSource on the EDT.
        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                panel.refreshFromSource();
            }
        });

        // 4) Assert on what the GUI shows.
        var tableFixture = window.table("errorTable");
        tableFixture.requireRowCount(1);

        javax.swing.JTable table = tableFixture.target();
        // Column index 2 is the “Message” column in TestErrorListPanel.
        String msg0 = (String) table.getValueAt(0, 2);
        assertTrue("Warning message should propagate into the ErrorList GUI",
                msg0.contains("Please highlight something"));
    }

    /**
     * Sanity check: for a valid (multi-character) selection,
     * {@code checkEditorContents} should succeed, no diagnostics
     * should be batched, and the ErrorList table should remain empty.
     */
    @Test
    public void testValidSelectionDoesNotPopulateErrorPanel() throws Exception {
        Method checkEditorContents =
                SUMOjEdit.class.getDeclaredMethod("checkEditorContents", String.class, String.class);
        checkEditorContents.setAccessible(true);

        Method addErrorsBatch =
                SUMOjEdit.class.getDeclaredMethod("addErrorsBatch", List.class);
        addErrorsBatch.setAccessible(true);

        Field pendField = SUMOjEdit.class.getDeclaredField("_pendingErrs");
        pendField.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Object> pending = (List<Object>) pendField.get(sje);
        pending.clear();

        String warnMsg = "Please highlight something";

        // 1) Valid selection: should pass and leave the pending list empty.
        boolean resultOk = (Boolean) checkEditorContents.invoke(sje, "ab", warnMsg);
        assertTrue("Valid selection should be accepted", resultOk);
        assertTrue("No diagnostics expected for valid selection", pending.isEmpty());

        // 2) Even if we call addErrorsBatch with an empty list, the
        //    ErrorSource should remain free of entries.
        addErrorsBatch.invoke(sje, pending);

        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                panel.refreshFromSource();
            }
        });

        var tableFixture = window.table("errorTable");
        tableFixture.requireRowCount(0);
    }
}