package com.articulate.sigma.jedit;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.Test;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * GUI tests for a minimal harness that models interplay between
 * Ghost Text and Drop-Down AutoComplete under three AC modes:
 *
 *   - GHOST_ONLY
 *   - DROPDOWN_ONLY
 *   - BOTH
 *
 * Behaviours locked down:
 *   - In GHOST_ONLY:
 *       * Ghost suggestion appears for matching prefix.
 *       * Ctrl+Tab accepts ghost (completes text and clears ghost).
 *       * Plain Tab does NOT accept any drop-down suggestion.
 *
 *   - In DROPDOWN_ONLY:
 *       * No ghost suggestion is rendered.
 *       * Drop-down popup appears for matching prefix.
 *       * Tab / Enter accept the drop-down selection.
 *       * Ctrl+Tab does NOT accept ghost (because ghost is disabled).
 *
 *   - In BOTH:
 *       * When only ghost is active (no popup), Ctrl+Tab accepts ghost.
 *       * When the drop-down popup is visible, Tab accepts the current
 *         list selection while ghost remains inactive.
 *
 * This harness is deliberately self-contained and does NOT depend on
 * jEdit runtime or SUMOjEdit internals; it just enforces the expected
 * user-visible semantics of AC mode switching and keybindings.
 *
 *
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class DualModeAutoCompleteGUITest extends AssertJSwingJUnitTestCase {

    // ---------------------------------------------------------------------
    // Harness
    // ---------------------------------------------------------------------

    private static final class DualModePanel extends JPanel {

        // Mode string mirrors SUMOjEdit property values
        static final String MODE_GHOST_ONLY    = "GHOST_ONLY";
        static final String MODE_DROPDOWN_ONLY = "DROPDOWN_ONLY";
        static final String MODE_BOTH          = "BOTH";

        private String mode = MODE_BOTH;

        private final JTextField editor   = new JTextField(20);
        private final JLabel     ghost    = new JLabel();
        private final DefaultListModel<String> model = new DefaultListModel<>();
        private final JList<String> list   = new JList<>(model);
        private final JPopupMenu popup     = new JPopupMenu();

        private final List<String> candidates = Arrays.asList(
                "instance", "individual", "Animal", "Animate", "agent"
        );

        DualModePanel() {
            super(new BorderLayout());

            editor.setName("dualEditor");
            ghost.setName("dualGhostLabel");

            // Editor row: text + ghost overlay label.
            JPanel row = new JPanel(new BorderLayout());
            row.add(editor, BorderLayout.CENTER);
            row.add(ghost, BorderLayout.EAST);
            add(row, BorderLayout.NORTH);

            // Drop-down list inside popup.
            list.setName("dualCompletionList");
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            JScrollPane scroll = new JScrollPane(list);
            popup.add(scroll);

            installDocumentListener();
            installKeyBindings();
            installListKeyBindings();
        }

        JTextField getEditor() {
            return editor;
        }

        JLabel getGhostLabel() {
            return ghost;
        }

        DefaultListModel<String> getModel() {
            return model;
        }

        JList<String> getList() {
            return list;
        }

        boolean isPopupVisible() {
            return popup.isVisible();
        }

        void setMode(String mode) {
            if (mode == null) return;
            this.mode = mode;
            // Mode change should clear state for predictability.
            hidePopup();
            ghost.setText("");
        }

        String getMode() {
            return mode;
        }

        // -------------------------------------------------------------
        // Ghost logic
        // -------------------------------------------------------------

        private void installDocumentListener() {
            editor.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) { updateForMode(); }

                @Override
                public void removeUpdate(DocumentEvent e) { updateForMode(); }

                @Override
                public void changedUpdate(DocumentEvent e) { updateForMode(); }
            });
        }

        private void updateForMode() {
            // When the document changes, approximate real typing behaviour:
            // if the caret is still at 0 but there is text, move caret to end
            // so prefix calculation uses the full word the user just typed.
            String text = editor.getText();
            int caret = editor.getCaretPosition();
            if (text != null && !text.isEmpty() && caret == 0) {
                editor.setCaretPosition(text.length());
            }

            // Always recompute ghost text first; then decide whether to show popup.
            updateGhost();
            updatePopup();
        }

        private void updateGhost() {
            if (!MODE_GHOST_ONLY.equals(mode) && !MODE_BOTH.equals(mode)) {
                ghost.setText("");
                return;
            }

            String prefix = currentPrefix();
            if (prefix.isEmpty()) {
                ghost.setText("");
                return;
            }

            String best = null;
            for (String cand : candidates) {
                if (cand.toLowerCase().startsWith(prefix.toLowerCase()) &&
                        !cand.equals(prefix)) {
                    if (best == null || cand.length() < best.length()) {
                        best = cand;
                    }
                }
            }

            if (best == null) {
                ghost.setText("");
            } else {
                String remainder = best.substring(prefix.length());
                ghost.setText(remainder);
            }
        }

        void acceptGhost() {
            if (ghost.getText() == null || ghost.getText().isEmpty()) {
                return;
            }
            String prefix = currentPrefix();
            // Only accept ghost when mode allows it.
            if (!MODE_GHOST_ONLY.equals(mode) && !MODE_BOTH.equals(mode)) {
                return;
            }
            editor.setText(prefix + ghost.getText());
            ghost.setText("");
            // In BOTH mode, accepting ghost should also dismiss any dropdown.
            hidePopup();
        }

        // -------------------------------------------------------------
        // Drop-down logic
        // -------------------------------------------------------------

        private void updatePopup() {
            if (!MODE_DROPDOWN_ONLY.equals(mode) && !MODE_BOTH.equals(mode)) {
                hidePopup();
                return;
            }

            String prefix = currentPrefix();
            if (prefix.isEmpty()) {
                hidePopup();
                return;
            }

            List<String> matches = new ArrayList<>();
            String pLower = prefix.toLowerCase();
            for (String cand : candidates) {
                if (cand.toLowerCase().startsWith(pLower)) {
                    matches.add(cand);
                }
            }

            if (matches.isEmpty()) {
                hidePopup();
                return;
            }

            matches.sort(String.CASE_INSENSITIVE_ORDER);
            model.clear();
            for (String m : matches) {
                model.addElement(m);
            }
            list.setSelectedIndex(0);

            if (!popup.isVisible()) {
                popup.show(editor, 0, editor.getHeight());
            }
        }

        void acceptDropDown() {
            if (!isPopupVisible()) {
                return;
            }
            if (!MODE_DROPDOWN_ONLY.equals(mode) && !MODE_BOTH.equals(mode)) {
                return;
            }
            int idx = list.getSelectedIndex();
            if (idx < 0 || idx >= model.size()) {
                return;
            }
            String value = model.getElementAt(idx);
            editor.setText(value);
            hidePopup();
            // When dropdown is driving, ghost should not re-appear immediately.
            ghost.setText("");
        }

        private void hidePopup() {
            popup.setVisible(false);
        }

        // -------------------------------------------------------------
        // Key bindings
        // -------------------------------------------------------------

        private void installKeyBindings() {
            InputMap im = editor.getInputMap(JComponent.WHEN_FOCUSED);
            ActionMap am = editor.getActionMap();

            // Ctrl+Tab -> ghost accept (if mode allows it)
            KeyStroke ctrlTab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.CTRL_DOWN_MASK);
            im.put(ctrlTab, "acceptGhost");
            am.put("acceptGhost", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    acceptGhost();
                }
            });

            // Plain Tab -> drop-down accept (if popup visible and mode allows it)
            KeyStroke tab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);
            im.put(tab, "acceptDropDown");
            am.put("acceptDropDown", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    acceptDropDown();
                }
            });

            // Enter -> drop-down accept as well when popup is visible.
            KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
            im.put(enter, "enterDropDown");
            am.put("enterDropDown", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    acceptDropDown();
                }
            });
        }

        private void installListKeyBindings() {
            list.addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int code = e.getKeyCode();
                    if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_TAB) {
                        acceptDropDown();
                        e.consume();
                    } else if (code == KeyEvent.VK_ESCAPE) {
                        hidePopup();
                        e.consume();
                    }
                }
            });
        }

        private String currentPrefix() {
            String text = editor.getText();
            if (text == null) text = "";
            int caret = editor.getCaretPosition();
            caret = Math.max(0, Math.min(caret, text.length()));

            int start = caret;
            while (start > 0) {
                char c = text.charAt(start - 1);
                if (Character.isWhitespace(c)) break;
                start--;
            }
            return text.substring(start, caret);
        }
    }

    // ---------------------------------------------------------------------
    // Test harness setup
    // ---------------------------------------------------------------------

    private FrameFixture window;
    private DualModePanel panel;

    @Override
    protected void onSetUp() {
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new DualModePanel();
                JFrame f = new JFrame("Dual Mode AC Harness");
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.getContentPane().add(panel);
                f.pack();
                f.setLocationRelativeTo(null);
                return f;
            }
        });
        window = new FrameFixture(robot(), frame);
        window.show();
        window.textBox("dualEditor").focus();
    }

    @Override
    protected void onTearDown() {
        if (window != null) {
            window.cleanUp();
            window = null;
        }
    }

    // ---------------------------------------------------------------------
    // GHOST_ONLY mode tests
    // ---------------------------------------------------------------------

    @Test
    public void testGhostOnlyMode_CtrlTabAcceptsGhost_TabDoesNotTriggerDropdown() {
        GuiActionRunner.execute(() -> panel.setMode(DualModePanel.MODE_GHOST_ONLY));

        // Type "inst" to trigger ghost "ance".
        window.textBox("dualEditor").setText("inst");

        // Ghost should appear; popup should not.
        assertEquals("ance", panel.getGhostLabel().getText());
        assertFalse(panel.isPopupVisible());

        // Logical equivalent of Ctrl+Tab: accept ghost.
        GuiActionRunner.execute(() -> panel.acceptGhost());

        window.textBox("dualEditor").requireText("instance");
        assertEquals("", panel.getGhostLabel().getText());
        assertFalse(panel.isPopupVisible());

        // Logical equivalent of pressing Tab: attempt dropdown accept,
        // which must be a no-op in GHOST_ONLY.
        GuiActionRunner.execute(() -> panel.acceptDropDown());

        window.textBox("dualEditor").requireText("instance");
        assertFalse(panel.isPopupVisible());
    }

    // ---------------------------------------------------------------------
    // DROPDOWN_ONLY mode tests
    // ---------------------------------------------------------------------

    @Test
    public void testDropDownOnlyMode_TabAndEnterAcceptDropdown_GhostDisabled() {
        GuiActionRunner.execute(() -> panel.setMode(DualModePanel.MODE_DROPDOWN_ONLY));

        // Type "an" -> should drive drop-down only.
        GuiActionRunner.execute(() -> {
            panel.getEditor().setText("an");
            panel.getEditor().setCaretPosition(2);
        });

        // Ghost should stay empty.
        assertEquals("", panel.getGhostLabel().getText());

        // Suggestions should exist in the drop-down model.
        assertTrue(panel.getModel().getSize() > 0);
        String firstSuggestion = panel.getModel().getElementAt(0);

        // Logical equivalent of Tab/Enter: accept dropdown.
        GuiActionRunner.execute(() -> panel.acceptDropDown());

        // Editor now has the selected value; popup hidden; ghost still empty.
        assertFalse(panel.isPopupVisible());
        assertEquals("", panel.getGhostLabel().getText());
        assertEquals(firstSuggestion, panel.getEditor().getText());

        // Re-open for same prefix and "Enter" again (same logical action).
        GuiActionRunner.execute(() -> {
            panel.getEditor().setText("an");
            panel.getEditor().setCaretPosition(2);
        });

        assertTrue(panel.getModel().getSize() > 0);
        String firstSuggestionAgain = panel.getModel().getElementAt(0);

        GuiActionRunner.execute(() -> panel.acceptDropDown());

        assertFalse(panel.isPopupVisible());
        assertEquals("", panel.getGhostLabel().getText());
        assertEquals(firstSuggestionAgain, panel.getEditor().getText());

        // Logical equivalent of Ctrl+Tab: ghost accept must be a no-op
        // because DROPDOWN_ONLY disables ghost.
        String before = panel.getEditor().getText();
        GuiActionRunner.execute(() -> panel.acceptGhost());
        window.textBox("dualEditor").requireText(before);
        assertEquals("", panel.getGhostLabel().getText());
    }

    // ---------------------------------------------------------------------
    // BOTH mode tests
    // ---------------------------------------------------------------------

    @Test
    public void testBothMode_CtrlTabAcceptsGhostWhenPopupNotVisible() {
        GuiActionRunner.execute(() -> panel.setMode(DualModePanel.MODE_BOTH));

        // Prefix that gives a ghost suggestion; in BOTH mode the dropdown
        // machinery may also run, but Ctrl+Tab must still accept ghost and
        // leave no popup visible afterwards.
        window.textBox("dualEditor").setText("inst");

        assertEquals("ance", panel.getGhostLabel().getText());

        // Logical equivalent of Ctrl+Tab in BOTH mode.
        GuiActionRunner.execute(() -> panel.acceptGhost());

        window.textBox("dualEditor").requireText("instance");
        assertEquals("", panel.getGhostLabel().getText());
        assertFalse(panel.isPopupVisible());
    }

    @Test
    public void testBothMode_TabAcceptsDropdownWhenPopupVisible() {
        GuiActionRunner.execute(() -> panel.setMode(DualModePanel.MODE_BOTH));

        // Use prefix "an" again to trigger dropdown.
        GuiActionRunner.execute(() -> {
            panel.getEditor().setText("an");
            panel.getEditor().setCaretPosition(2);
        });

        // There should be at least one suggestion.
        assertTrue(panel.getModel().getSize() > 0);
        String firstSuggestion = panel.getModel().getElementAt(0);

        // Logical equivalent of Tab when the popup is visible.
        GuiActionRunner.execute(() -> panel.acceptDropDown());

        assertFalse(panel.isPopupVisible());
        assertEquals(firstSuggestion, panel.getEditor().getText());
        // Ghost should be cleared after dropdown accept.
        assertEquals("", panel.getGhostLabel().getText());
    }
}