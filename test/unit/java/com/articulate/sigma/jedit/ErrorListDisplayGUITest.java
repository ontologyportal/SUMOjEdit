package com.articulate.sigma.jedit;

import com.articulate.sigma.ErrRec;
import com.articulate.sigma.jedit.SUMOjEdit;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;
import org.assertj.swing.data.TableCell;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.edt.GuiTask;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.After;
import org.junit.Test;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * GUI-level tests for how SUMOjEdit's error wiring feeds into a Swing
 * Error List-like panel.
 *
 * This suite exercises:
 *  - SUMOjEdit.addErrorsDirect(List<ErrRec>)
 *  - DefaultErrorSource population
 *  - TestErrorListPanel rendering and row selection behaviour
 *
 * It intentionally uses a minimal Swing wrapper rather than the real
 * jEdit ErrorList plugin to keep tests deterministic and independent
 * of jEdit's docking / plugin lifecycle.
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class ErrorListDisplayGUITest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;
    private SUMOjEdit sje;
    private TestErrorListPanel panel;

    @Override
    protected void onSetUp() {
        // Initialise SUMOjEdit and its DefaultErrorSource.
        sje = new SUMOjEdit();
        sje.errsrc = new DefaultErrorSource(SUMOjEdit.class.getName(), null);
        ErrorSource.registerErrorSource(sje.errsrc);

        // Ensure view is null so addErrorsDirect() does not try to touch jEdit buffers.
        try {
            Field viewField = SUMOjEdit.class.getDeclaredField("view");
            viewField.setAccessible(true);
            viewField.set(sje, null);
        } catch (Exception ignore) {
            // If the field cannot be set, tests will still run as long as SUMOjEdit
            // does not dereference a non-existent jEdit View.
        }

        // Build the Swing UI on the EDT.
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new TestErrorListPanel(sje.errsrc);
                JFrame f = new JFrame("Error List Display Test");
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
            window = null;
        }
    }

    @After
    public void cleanErrorSource() {
        if (sje != null && sje.errsrc != null) {
            ErrorSource.unregisterErrorSource(sje.errsrc);
            sje.errsrc.clear();
        }
        sje = null;
    }

    /**
     * Verify that addErrorsDirect() sorts diagnostics by line and that
     * the TestErrorListPanel displays severity, line and message as expected.
     */
    @Test
    public void testTableShowsErrorsFromAddErrorsDirectSortedByLine() throws Exception {
        // Two diagnostics out of order by line to exercise the sort.
        ErrRec later = new ErrRec(
                ErrRec.ERROR,
                "TestFile.kif",
                5,   // 0-based line
                1,
                2,
                "later message");
        ErrRec earlier = new ErrRec(
                ErrRec.WARNING,
                "TestFile.kif",
                1,   // 0-based line
                1,
                3,
                "earlier message");

        Method addDirect = SUMOjEdit.class.getDeclaredMethod("addErrorsDirect", java.util.List.class);
        addDirect.setAccessible(true);
        addDirect.invoke(sje, Arrays.asList(later, earlier));

        // Refresh the UI from the error source on the EDT.
        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                panel.refreshFromSource();
            }
        });

        var table = window.table("errorTable");
        table.requireRowCount(2);

        // Row 0 should correspond to the earlier line (1 -> display "2"),
        // and be a WARNING.
        assertEquals("WARNING", table.valueAt(TableCell.row(0).column(0)));
        assertEquals("2", table.valueAt(TableCell.row(0).column(1)));
        assertEquals("earlier message", table.valueAt(TableCell.row(0).column(2)));

        // Row 1 should correspond to the later line (5 -> display "6"),
        // and be an ERROR.
        assertEquals("ERROR", table.valueAt(TableCell.row(1).column(0)));
        assertEquals("6", table.valueAt(TableCell.row(1).column(1)));
        assertEquals("later message", table.valueAt(TableCell.row(1).column(2)));
    }

    /**
     * Verify that selecting a row in the table updates the detailsLabel with a
     * human-readable summary including file, 1-based line number and message.
     */
    @Test
    public void testDetailsLabelUpdatesOnRowSelection() throws Exception {
        ErrRec rec = new ErrRec(
                ErrRec.ERROR,
                "Example.kif",
                2,   // 0-based line => 3 for display
                0,
                1,
                "problem here");

        Method addDirect = SUMOjEdit.class.getDeclaredMethod("addErrorsDirect", java.util.List.class);
        addDirect.setAccessible(true);
        addDirect.invoke(sje, java.util.Collections.singletonList(rec));

        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                panel.refreshFromSource();
            }
        });

        var table = window.table("errorTable");
        table.requireRowCount(1);

        // Select the only row.
        table.selectRows(0);

        String detail = window.label("detailsLabel").text();
        assertTrue(detail.contains("Example.kif:3"));
        assertTrue(detail.contains("problem here"));
        assertTrue(detail.startsWith("ERROR @"));
    }
}