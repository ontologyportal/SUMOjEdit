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
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * GUI-dependent end-to-end test for the SUO-KIF error pipeline:
 *
 *   KIF warningSet / errorSet
 *     -> SUMOjEdit.logKifWarnAndErr()
 *     -> DefaultErrorSource
 *     -> TestErrorListPanel JTable
 *
 * This intentionally reuses the same internal KIF wiring as the
 * standalone SUOKIFErrorCheckTest, but adds a Swing harness to
 * verify what ends up on screen.
 *
 *
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckGUITest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class SUOKIFErrorCheckGUITest extends AssertJSwingJUnitTestCase {

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
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new TestErrorListPanel(sje.errsrc);
                JFrame f = new JFrame("SUO-KIF Error Check GUI Test");
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
     * Drive the same logKifWarnAndErr() pipeline used in the standalone
     * tests, then verify that the TestErrorListPanel renders the two
     * diagnostics (one WARNING on line 2, one ERROR on line 4) as
     * sorted rows in the JTable.
     */
    @Test
    public void testLogKifWarnAndErrPopulatesErrorPanel() throws Exception {
        // 1) Create a temporary KIF file with at least four lines so that
        //    line numbers in the diagnostics are meaningful.
        File tmp = File.createTempFile("logKifWarnAndErr-gui", ".kif");
        tmp.deleteOnExit();
        Files.writeString(tmp.toPath(), "a\nb\nc\nd\n");

        // 2) Populate the underlying KIF diagnostic sets just like the
        //    standalone SUOKIFErrorCheckTest does.
        sje.kif.filename = tmp.getAbsolutePath();
        sje.kif.warningSet.clear();
        sje.kif.errorSet.clear();
        sje.kif.warningSet.add("2:1: warning message");
        sje.kif.errorSet.add("line: 4 error message");

        // 3) Invoke the private logKifWarnAndErr() to translate KIF sets
        //    into ErrorSource entries.
        Method log = SUMOjEdit.class.getDeclaredMethod("logKifWarnAndErr");
        log.setAccessible(true);
        log.invoke(sje);

        // 4) Refresh the Swing panel from the DefaultErrorSource on the EDT.
        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                panel.refreshFromSource();
            }
        });

        // 5) Assert on what the GUI shows.
        // The TestErrorListPanel's JTable has columns: Type, Line, Message.
        var tableFixture = window.table("errorTable");
        tableFixture.requireRowCount(2);

        javax.swing.JTable table = tableFixture.target();

        // Row 0 should be the WARNING on line 2, row 1 the ERROR on line 4,
        // because SUMOjEdit sorts by line before feeding the ErrorSource.
        String type0 = (String) table.getValueAt(0, 0);
        String type1 = (String) table.getValueAt(1, 0);
        assertEquals("WARNING", type0);
        assertEquals("ERROR", type1);

        Integer line0 = (Integer) table.getValueAt(0, 1);
        Integer line1 = (Integer) table.getValueAt(1, 1);
        assertEquals(Integer.valueOf(2), line0);
        assertEquals(Integer.valueOf(4), line1);

        String msg0 = (String) table.getValueAt(0, 2);
        String msg1 = (String) table.getValueAt(1, 2);
        assertTrue(msg0.contains("warning message"));
        assertTrue(msg1.contains("error message"));
    }
}