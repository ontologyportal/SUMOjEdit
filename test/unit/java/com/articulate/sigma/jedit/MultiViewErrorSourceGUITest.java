package com.articulate.sigma.jedit;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.After;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

import static org.junit.Assert.*;

/**
 * GUI-dependent test (AssertJ Swing) for a minimal harness that mirrors
 * SUMOjEdit's per-View ErrorSource behaviour:
 *
 *  - One DefaultErrorSource per logical "view"
 *  - Activating a view selects (and, if needed, creates) its source
 *    without unregistering other sources
 *  - Closing a view unregisters only that view's source
 *  - Closing the active view switches selection to a surviving view
 *    if one exists, otherwise clears the selection
 *
 * This test is GUI-dependent because the multi-view harness is driven
 * via a real Swing frame and buttons using AssertJ Swing's robot, but
 * the actual multi-view semantics are captured in the harness logic.
 *
 * The goal is to lock in the same lifecycle semantics that SUMOjEdit
 * implements via its viewErrorSources map, ensureErrorSource(), and
 * viewUpdate(ViewUpdate) (ACTIVATED/CLOSED) handling, without needing
 * a full jEdit runtime in the test environment.
 *
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.fastac.KifTermIndexTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class MultiViewErrorSourceGUITest extends AssertJSwingJUnitTestCase {

    /**
     * Minimal manager that approximates SUMOjEdit's per-View ErrorSource
     * handling using a WeakHashMap keyed by view object identity.
     */
    private static final class MultiViewErrorSourceHarness {

        private final Map<Object, DefaultErrorSource> viewErrorSources =
                new WeakHashMap<>();

        private DefaultErrorSource currentErrsrc;
        private Object currentView;

        /**
         * Activate a view: select or create its ErrorSource and make
         * it the current one, without unregistering any others.
         */
        DefaultErrorSource activateView(Object view) {
            if (view == null) {
                throw new IllegalArgumentException("view must not be null");
            }
            currentView = view;

            DefaultErrorSource es = viewErrorSources.get(view);
            if (es == null) {
                String name = "Harness@" +
                        Integer.toHexString(System.identityHashCode(view));
                // No real jEdit View needed here.
                es = new DefaultErrorSource(name, null);
                ErrorSource.registerErrorSource(es);
                viewErrorSources.put(view, es);
            }
            currentErrsrc = es;
            return es;
        }

        /**
         * Close a view: unregister and clear only that view's
         * ErrorSource. If it was the current view, switch selection
         * to another surviving view (if any), otherwise clear it.
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
         * Helper used by tearDown to ensure we leave no registered
         * ErrorSources behind between tests.
         */
        void closeAllViews() {
            for (DefaultErrorSource es :
                    new ArrayList<>(viewErrorSources.values())) {
                ErrorSource.unregisterErrorSource(es);
                es.clear();
            }
            viewErrorSources.clear();
            currentView = null;
            currentErrsrc = null;
        }

        DefaultErrorSource getCurrentErrsrc() {
            return currentErrsrc;
        }

        Object getCurrentView() {
            return currentView;
        }

        DefaultErrorSource getErrorSourceFor(Object view) {
            return viewErrorSources.get(view);
        }

        int getViewCount() {
            return viewErrorSources.size();
        }
    }

    private FrameFixture window;
    private MultiViewErrorSourceHarness harness;

    // Logical "views" for the harness. These are not real jEdit View
    // instances, just identity tokens standing in for them.
    private Object view1;
    private Object view2;

    private JLabel statusLabel;

    @Override
    protected void onSetUp() {
        harness = new MultiViewErrorSourceHarness();
        view1 = new Object();
        view2 = new Object();

        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                JFrame f = new JFrame("Multi-View ErrorSource Harness");
                f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

                statusLabel = new JLabel("current: <none>");
                statusLabel.setName("currentViewLabel");

                JButton activateV1 = new JButton("Activate V1");
                activateV1.setName("activateV1");
                activateV1.addActionListener(e -> {
                    harness.activateView(view1);
                    updateStatusLabel();
                });

                JButton activateV2 = new JButton("Activate V2");
                activateV2.setName("activateV2");
                activateV2.addActionListener(e -> {
                    harness.activateView(view2);
                    updateStatusLabel();
                });

                JButton closeV1 = new JButton("Close V1");
                closeV1.setName("closeV1");
                closeV1.addActionListener(e -> {
                    harness.closeView(view1);
                    updateStatusLabel();
                });

                JButton closeV2 = new JButton("Close V2");
                closeV2.setName("closeV2");
                closeV2.addActionListener(e -> {
                    harness.closeView(view2);
                    updateStatusLabel();
                });

                JPanel buttons = new JPanel(new GridLayout(2, 2));
                buttons.add(activateV1);
                buttons.add(activateV2);
                buttons.add(closeV1);
                buttons.add(closeV2);

                JPanel root = new JPanel(new BorderLayout());
                root.add(buttons, BorderLayout.CENTER);
                root.add(statusLabel, BorderLayout.SOUTH);

                f.setContentPane(root);
                f.pack();
                f.setLocationRelativeTo(null);
                return f;
            }
        });

        window = new FrameFixture(robot(), frame);
        window.show();
    }

    private void updateStatusLabel() {
        Object current = harness.getCurrentView();
        if (current == null) {
            statusLabel.setText("current: <none>");
        } else if (current == view1) {
            statusLabel.setText("current: V1");
        } else if (current == view2) {
            statusLabel.setText("current: V2");
        } else {
            statusLabel.setText("current: <unknown>");
        }
    }

    @Override
    protected void onTearDown() {
        if (window != null) {
            window.cleanUp();
        }
        if (harness != null) {
            harness.closeAllViews();
        }
    }

    /**
     * Activating a second view should create a distinct ErrorSource
     * and switch the current selection, while keeping the first view's
     * ErrorSource registered and intact.
     */
    @Test
    public void testSecondViewActivationCreatesNewSourceAndSwitchesSelection() {
        window.button("activateV1").click();
        DefaultErrorSource es1 = harness.getErrorSourceFor(view1);
        assertNotNull(es1);
        assertEquals(1, harness.getViewCount());
        window.label("currentViewLabel").requireText("current: V1");

        window.button("activateV2").click();
        DefaultErrorSource es2 = harness.getErrorSourceFor(view2);
        assertNotNull(es2);
        assertNotSame(es1, es2);
        assertEquals(2, harness.getViewCount());
        window.label("currentViewLabel").requireText("current: V2");

        assertSame(es1, harness.getErrorSourceFor(view1));
    }

    /**
     * Re-activating an already-known view must re-use its existing
     * ErrorSource instead of creating a new one.
     */
    @Test
    public void testReactivatingViewReusesExistingErrorSource() {
        window.button("activateV1").click();
        DefaultErrorSource es1 = harness.getErrorSourceFor(view1);

        window.button("activateV2").click();
        DefaultErrorSource es2 = harness.getErrorSourceFor(view2);
        assertEquals(2, harness.getViewCount());

        window.button("activateV1").click();
        DefaultErrorSource es1Again = harness.getErrorSourceFor(view1);

        assertSame("Re-activating V1 must reuse its ErrorSource", es1, es1Again);
        assertEquals("No new ErrorSource should be created",
                     2, harness.getViewCount());
        window.label("currentViewLabel").requireText("current: V1");
        assertSame(view1, harness.getCurrentView());
        assertSame(es1, harness.getCurrentErrsrc());
        assertNotSame(es1, es2);
    }

    /**
     * Closing an inactive view should unregister only that view's
     * ErrorSource, leaving the current view and its ErrorSource
     * unchanged.
     */
    @Test
    public void testClosingInactiveViewDoesNotAffectCurrentSelection() {
        window.button("activateV1").click();
        DefaultErrorSource es1 = harness.getErrorSourceFor(view1);

        window.button("activateV2").click();
        DefaultErrorSource es2 = harness.getErrorSourceFor(view2);
        assertEquals(2, harness.getViewCount());
        window.label("currentViewLabel").requireText("current: V2");

        // Make V1 active again; V2 becomes inactive.
        window.button("activateV1").click();
        window.label("currentViewLabel").requireText("current: V1");
        assertSame(es1, harness.getCurrentErrsrc());

        // Close inactive V2.
        window.button("closeV2").click();
        assertEquals(1, harness.getViewCount());
        assertNull("V2's ErrorSource must be removed",
                   harness.getErrorSourceFor(view2));
        window.label("currentViewLabel").requireText("current: V1");
        assertSame(view1, harness.getCurrentView());
        assertSame(es1, harness.getCurrentErrsrc());
    }

    /**
     * Closing the currently active view should switch selection to
     * another surviving view if one exists; otherwise, the current
     * view and current ErrorSource should both be cleared.
     */
    @Test
    public void testClosingActiveViewSwitchesOrClearsSelection() {
        window.button("activateV1").click();
        DefaultErrorSource es1 = harness.getErrorSourceFor(view1);

        window.button("activateV2").click();
        DefaultErrorSource es2 = harness.getErrorSourceFor(view2);
        assertEquals(2, harness.getViewCount());
        window.label("currentViewLabel").requireText("current: V2");
        assertSame(es2, harness.getCurrentErrsrc());

        // Close the currently active V2: selection should move to V1.
        window.button("closeV2").click();
        assertEquals(1, harness.getViewCount());
        window.label("currentViewLabel").requireText("current: V1");
        assertSame(view1, harness.getCurrentView());
        assertSame(es1, harness.getCurrentErrsrc());
        assertNull(harness.getErrorSourceFor(view2));

        // Now close the last remaining view: everything should clear.
        window.button("closeV1").click();
        assertEquals(0, harness.getViewCount());
        window.label("currentViewLabel").requireText("current: <none>");
        assertNull(harness.getCurrentView());
        assertNull(harness.getCurrentErrsrc());
    }
}