package com.articulate.sigma.jedit;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Map;
import java.util.WeakHashMap;

import static org.junit.Assert.*;

/**
 * GUI-dependent test that verifies multi-view error source switching
 * behaves correctly in a Swing environment.  Each logical “view” has
 * its own {@link DefaultErrorSource}.  Activating a view switches
 * which source the Error List panel displays.  Adding an error to
 * one view must not interfere with the other view’s errors.  Closing
 * a view should remove its errors and automatically select another
 * surviving view if present.  This mirrors SUMOjEdit’s
 * viewErrorSources map and ViewUpdate handling.
 *
 * The harness used here is deliberately simple: it stores per-view
 * {@link DefaultErrorSource} instances in a {@link WeakHashMap} and
 * exposes activate, add-error and close operations.  A small Swing
 * frame with buttons drives the harness, and a
 * {@link TestErrorListPanel} reflects whatever errors are present in
 * the currently selected view.
 *
 *
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.fastac.KifTermIndexTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class MultiViewErrorSourceSwitchingGUITest extends AssertJSwingJUnitTestCase {

    /**
     * Harness managing one DefaultErrorSource per logical view.  It is
     * intentionally similar to SUMOjEdit’s internal viewErrorSources
     * map but without any jEdit dependencies.
     */
    private static final class MultiViewHarness {
        private final Map<Object, DefaultErrorSource> viewErrorSources = new WeakHashMap<>();
        private Object currentView;
        private DefaultErrorSource currentErrsrc;

        /**
         * Activate the given view.  If this is the first time this view
         * has been seen, a new {@link DefaultErrorSource} is created and
         * registered; otherwise the existing source is reused.  The
         * currently active view and error source are updated but no
         * other sources are unregistered.
         *
         * @param view identity token for the view (must be non-null)
         * @return the error source for the activated view
         */
        DefaultErrorSource activateView(Object view) {
            if (view == null) {
                throw new IllegalArgumentException("view must not be null");
            }
            currentView = view;
            DefaultErrorSource es = viewErrorSources.get(view);
            if (es == null) {
                String name = "View@" + Integer.toHexString(System.identityHashCode(view));
                es = new DefaultErrorSource(name, null);
                ErrorSource.registerErrorSource(es);
                viewErrorSources.put(view, es);
            }
            currentErrsrc = es;
            return es;
        }

        /**
         * Add a dummy error to the current view’s error source.  The
         * message is prefixed with the current view’s identity for
         * easier assertions.  If no view is currently active, this
         * method does nothing.
         */
        void addDummyError() {
            if (currentErrsrc == null || currentView == null) {
                return;
            }
            // Construct a per-view file name and message so the test can
            // detect which view an error belongs to.  We always use line
            // index 0 and trivial offsets because the position is
            // irrelevant for this test.
            String fileName = "View-" + Integer.toHexString(System.identityHashCode(currentView)) + ".kif";
            String msg = "Error for view " + Integer.toHexString(System.identityHashCode(currentView));
            currentErrsrc.addError(ErrorSource.ERROR, fileName, 0, 0, 1, msg);
        }

        /**
         * Close the given view: its error source is unregistered and
         * cleared.  If it was the current view, selection moves to
         * another surviving view if one exists; otherwise the current
         * view and error source are cleared.
         *
         * @param view identity token for the view
         */
        void closeView(Object view) {
            if (view == null) {
                return;
            }
            DefaultErrorSource es = viewErrorSources.remove(view);
            if (es != null) {
                ErrorSource.unregisterErrorSource(es);
                es.clear();
            }
            if (view.equals(currentView)) {
                if (viewErrorSources.isEmpty()) {
                    currentView = null;
                    currentErrsrc = null;
                } else {
                    Object next = viewErrorSources.keySet().iterator().next();
                    currentView = next;
                    currentErrsrc = viewErrorSources.get(next);
                }
            }
        }

        /**
         * Remove all views and their error sources.  This is used by
         * tearDown to ensure no lingering sources remain registered.
         */
        void closeAllViews() {
            for (DefaultErrorSource es : viewErrorSources.values()) {
                ErrorSource.unregisterErrorSource(es);
                es.clear();
            }
            viewErrorSources.clear();
            currentView = null;
            currentErrsrc = null;
        }

        DefaultErrorSource getErrorSourceFor(Object view) {
            return viewErrorSources.get(view);
        }

        DefaultErrorSource getCurrentErrsrc() {
            return currentErrsrc;
        }

        Object getCurrentView() {
            return currentView;
        }

        int getViewCount() {
            return viewErrorSources.size();
        }
    }

    private FrameFixture window;
    private MultiViewHarness harness;
    private Object viewA;
    private Object viewB;
    private TestErrorListPanel panel;
    private JPanel panelHolder;
    private JLabel statusLabel;

    @Override
    protected void onSetUp() {
        harness = new MultiViewHarness();
        // Use two distinct identity tokens to represent two jEdit views.
        viewA = new Object();
        viewB = new Object();

        // Build the Swing UI on the EDT via GuiQuery.
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                JFrame f = new JFrame("Multi-View Error Switching Test");
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.setLayout(new BorderLayout());

                // Status label shows which view is currently active.
                statusLabel = new JLabel("current: <none>");
                statusLabel.setName("statusLabel");

                // Panel that will hold the TestErrorListPanel.  We swap
                // its contents whenever the current view changes.
                panelHolder = new JPanel(new BorderLayout());

                // Buttons to drive the harness.
                JButton activateA = new JButton("Activate A");
                activateA.setName("activateA");
                activateA.addActionListener(e -> {
                    harness.activateView(viewA);
                    updatePanel();
                });
                JButton activateB = new JButton("Activate B");
                activateB.setName("activateB");
                activateB.addActionListener(e -> {
                    harness.activateView(viewB);
                    updatePanel();
                });
                JButton addErr = new JButton("Add Error");
                addErr.setName("addError");
                addErr.addActionListener(e -> {
                    harness.addDummyError();
                    if (panel != null) {
                        // Refresh the table to pick up the new error.
                        panel.refreshFromSource();
                    }
                });
                JButton closeCur = new JButton("Close Current");
                closeCur.setName("closeCurrent");
                closeCur.addActionListener(e -> {
                    Object cur = harness.getCurrentView();
                    harness.closeView(cur);
                    updatePanel();
                });

                JPanel buttons = new JPanel(new GridLayout(2, 2));
                buttons.add(activateA);
                buttons.add(activateB);
                buttons.add(addErr);
                buttons.add(closeCur);

                f.add(buttons, BorderLayout.NORTH);
                f.add(panelHolder, BorderLayout.CENTER);
                f.add(statusLabel, BorderLayout.SOUTH);

                f.pack();
                f.setLocationRelativeTo(null);
                return f;
            }
        });

        window = new FrameFixture(robot(), frame);
        window.show();
    }

    /**
     * Swap in a new TestErrorListPanel for the current view and update
     * the status label.  Must be called on the EDT.
     */
    private void updatePanel() {
        // Update the status label to reflect the current view.
        Object current = harness.getCurrentView();
        String labelText;
        if (current == null) {
            labelText = "current: <none>";
        } else if (current == viewA) {
            labelText = "current: A";
        } else if (current == viewB) {
            labelText = "current: B";
        } else {
            labelText = "current: <unknown>";
        }
        statusLabel.setText(labelText);

        // Remove any existing panel and create a new one for the
        // currently selected error source.  If there is no active
        // source, create an empty panel backed by a null source.
        panelHolder.removeAll();
        DefaultErrorSource src = harness.getCurrentErrsrc();
        panel = new TestErrorListPanel(src);
        panelHolder.add(panel, BorderLayout.CENTER);
        panel.refreshFromSource();
        panelHolder.revalidate();
        panelHolder.repaint();
    }

    @Override
    protected void onTearDown() {
        if (window != null) {
            window.cleanUp();
            window = null;
        }
        if (harness != null) {
            harness.closeAllViews();
            harness = null;
        }
    }

    /**
     * Test that activating each view, adding an error, and switching
     * between views displays only the errors associated with the
     * currently active view.  Errors in the inactive view remain
     * registered but do not appear in the panel until that view is
     * selected again.
     */
    @Test
    public void testErrorIsolationAcrossViews() {
        // Activate view A and add one error.
        window.button("activateA").click();
        window.button("addError").click();
        // Table should show exactly one error belonging to A.
        window.table("errorTable").requireRowCount(1);
        String msgA = window.table("errorTable").valueAt(org.assertj.swing.data.TableCell.row(0).column(2));
        assertTrue("Error message should contain view A identifier", msgA.contains(Integer.toHexString(System.identityHashCode(viewA))));

        // Activate view B; since no errors have been added, the table should be empty.
        window.button("activateB").click();
        window.table("errorTable").requireRowCount(0);

        // Add an error to view B and verify the table shows it.
        window.button("addError").click();
        window.table("errorTable").requireRowCount(1);
        String msgB = window.table("errorTable").valueAt(org.assertj.swing.data.TableCell.row(0).column(2));
        assertTrue("Error message should contain view B identifier", msgB.contains(Integer.toHexString(System.identityHashCode(viewB))));

        // Ensure that both error sources have exactly one error.
        DefaultErrorSource esA = harness.getErrorSourceFor(viewA);
        DefaultErrorSource esB = harness.getErrorSourceFor(viewB);
        assertNotNull(esA);
        assertNotNull(esB);
        assertEquals(1, esA.getErrorCount());
        assertEquals(1, esB.getErrorCount());

        // Re-activate view A; the table should show only the A error.
        window.button("activateA").click();
        window.table("errorTable").requireRowCount(1);
        String msgA2 = window.table("errorTable").valueAt(org.assertj.swing.data.TableCell.row(0).column(2));
        assertTrue(msgA2.contains(Integer.toHexString(System.identityHashCode(viewA))));

        // Activate view B again; verify only B's error is displayed.
        window.button("activateB").click();
        window.table("errorTable").requireRowCount(1);
        String msgB2 = window.table("errorTable").valueAt(org.assertj.swing.data.TableCell.row(0).column(2));
        assertTrue(msgB2.contains(Integer.toHexString(System.identityHashCode(viewB))));
    }

    /**
     * Test closing views: closing the currently active view should
     * remove its errors and switch selection to a surviving view.  If
     * no views remain, the panel should clear and no error sources
     * remain registered.
     */
    @Test
    public void testClosingViewRemovesErrorsAndSelectsSurvivor() {
        // Populate both views with one error each.
        window.button("activateA").click();
        window.button("addError").click();
        window.button("activateB").click();
        window.button("addError").click();

        // Initially, view B is active.  Close it; view A should
        // automatically become active and its single error should be
        // displayed.
        window.button("closeCurrent").click();
        assertEquals(1, harness.getViewCount());
        assertSame(viewA, harness.getCurrentView());
        window.table("errorTable").requireRowCount(1);
        String msg = window.table("errorTable").valueAt(org.assertj.swing.data.TableCell.row(0).column(2));
        assertTrue(msg.contains(Integer.toHexString(System.identityHashCode(viewA))));

        // Now close the remaining view; everything should clear.
        window.button("closeCurrent").click();
        assertEquals(0, harness.getViewCount());
        assertNull(harness.getCurrentView());
        assertNull(harness.getCurrentErrsrc());
        // The panel still exists but should show no rows.
        window.table("errorTable").requireRowCount(0);
    }
}