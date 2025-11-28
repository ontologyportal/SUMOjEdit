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
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * GUI-level tests for how updates to SUMOjEdit's DefaultErrorSource are
 * reflected in the minimal Error List panel:
 *
 *  - initial population via addErrorsDirect()
 *  - clearing all diagnostics
 *  - re-populating with a different set of diagnostics
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class ErrorListUpdateGUITest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;
    private SUMOjEdit sje;
    private TestErrorListPanel panel;

    @Override
    protected void onSetUp() {
        sje = new SUMOjEdit();
        sje.errsrc = new DefaultErrorSource(SUMOjEdit.class.getName(), null);
        ErrorSource.registerErrorSource(sje.errsrc);

        // Avoid any interaction with real jEdit Buffer instances.
        try {
            Field viewField = SUMOjEdit.class.getDeclaredField("view");
            viewField.setAccessible(true);
            viewField.set(sje, null);
        } catch (Exception ignore) {
            // Best-effort; tests only rely on errsrc.
        }

        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new TestErrorListPanel(sje.errsrc);
                JFrame f = new JFrame("Error List Update Test");
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
     * Verify that clearing the DefaultErrorSource empties the panel and that
     * re-populating the source produces the new rows as expected.
     */
    @Test
    public void testClearAndRepopulateErrorList() throws Exception {
        Method addDirect = SUMOjEdit.class.getDeclaredMethod("addErrorsDirect", java.util.List.class);
        addDirect.setAccessible(true);

        // Step 1: add a single error and verify one row.
        ErrRec first = new ErrRec(
                ErrRec.ERROR,
                "Initial.kif",
                0,
                0,
                1,
                "first error");

        addDirect.invoke(sje, Collections.singletonList(first));

        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                panel.refreshFromSource();
            }
        });

        var table = window.table("errorTable");
        table.requireRowCount(1);
        assertEquals("first error", table.valueAt(TableCell.row(0).column(2)));

        // Step 2: clear the error source and refresh; table should be empty.
        sje.errsrc.clear();

        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                panel.refreshFromSource();
            }
        });

        table.requireRowCount(0);

        // Step 3: add two new errors and verify both appear.
        ErrRec e1 = new ErrRec(
                ErrRec.WARNING,
                "Repop.kif",
                3,
                0,
                1,
                "repop warning");
        ErrRec e2 = new ErrRec(
                ErrRec.ERROR,
                "Repop.kif",
                1,
                0,
                1,
                "repop error");

        addDirect.invoke(sje, Arrays.asList(e1, e2));

        GuiActionRunner.execute(new GuiTask() {
            @Override
            protected void executeInEDT() {
                panel.refreshFromSource();
            }
        });

        table.requireRowCount(2);

        // Sanity check content; we only care that the new messages are present.
        String msg0 = table.valueAt(TableCell.row(0).column(2));
        String msg1 = table.valueAt(TableCell.row(1).column(2));
        assertTrue(msg0.equals("repop warning") || msg0.equals("repop error"));
        assertTrue(msg1.equals("repop warning") || msg1.equals("repop error"));
        assertNotEquals(msg0, msg1);
    }
}