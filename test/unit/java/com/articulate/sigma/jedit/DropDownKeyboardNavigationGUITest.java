package com.articulate.sigma.jedit;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

import static org.junit.Assert.*;

/**
 * GUI-level tests for keyboard interaction with a minimal AutoComplete
 * drop-down popup:
 *
 *  - DOWN / UP arrows move the highlighted suggestion
 *  - ENTER accepts the current suggestion
 *  - TAB also accepts the current suggestion
 *  - ESC closes the popup without modifying the editor text
 *
 * This harness does not depend on jEdit; it models the expected
 * behaviour of SUMOjEdit's drop-down AutoComplete UI.
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class DropDownKeyboardNavigationGUITest extends AssertJSwingJUnitTestCase {

    /**
     * Minimal drop-down AC harness: a text field plus a suggestion list.
     */
    private static final class DropDownPanel extends JPanel {

        private final JTextField editor;
        private final JList<String> list;
        private final DefaultListModel<String> model;
        private boolean popupVisible = false;
        private String lastAccepted = null;

        DropDownPanel() {
            super(new BorderLayout());

            editor = new JTextField("a");
            editor.setName("editorField");
            // Ensure TAB is delivered to the editor's InputMap instead of being
            // consumed as a focus traversal key, so we can bind it for AC accept.
            editor.setFocusTraversalKeysEnabled(false);

            model = new DefaultListModel<>();
            list = new JList<>(model);
            list.setName("acList");
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            JScrollPane scroll = new JScrollPane(list);

            add(editor, BorderLayout.NORTH);
            add(scroll, BorderLayout.CENTER);

            // Seed some demo suggestions
            showSuggestionsForPrefix("a");

            installKeyBindings();
        }

        private void showSuggestionsForPrefix(String prefix) {
            model.clear();
            model.addElement("Animal");
            model.addElement("Animate");
            model.addElement("agent");
            popupVisible = !model.isEmpty();
            if (popupVisible) {
                list.setSelectedIndex(0);
            } else {
                list.clearSelection();
            }
        }

        private void installKeyBindings() {
            InputMap im = editor.getInputMap(JComponent.WHEN_FOCUSED);
            ActionMap am = editor.getActionMap();

            // Navigate suggestions with arrow keys.
            KeyStroke down = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0);
            KeyStroke up   = KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0);

            im.put(down, "acMoveDown");
            im.put(up,   "acMoveUp");

            am.put("acMoveDown", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    moveSelection(+1);
                }
            });
            am.put("acMoveUp", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    moveSelection(-1);
                }
            });

            // ENTER accepts the current suggestion.
            KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
            im.put(enter, "acAcceptEnter");
            am.put("acAcceptEnter", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    acceptCurrentSelection();
                }
            });

            // TAB also accepts the current suggestion.
            KeyStroke tab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0);
            im.put(tab, "acAcceptTab");
            am.put("acAcceptTab", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    acceptCurrentSelection();
                }
            });

            // ESC closes the popup without modifying text.
            KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
            im.put(esc, "acDismiss");
            am.put("acDismiss", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    dismissPopup();
                }
            });
        }

        private void moveSelection(int delta) {
            if (!popupVisible || model.isEmpty()) {
                return;
            }
            int idx = list.getSelectedIndex();
            if (idx < 0) {
                idx = 0;
            } else {
                idx += delta;
            }
            if (idx < 0) {
                idx = 0;
            } else if (idx >= model.size()) {
                idx = model.size() - 1;
            }
            list.setSelectedIndex(idx);
            list.ensureIndexIsVisible(idx);
        }

        private void acceptCurrentSelection() {
            if (!popupVisible) {
                return;
            }
            int idx = list.getSelectedIndex();
            if (idx < 0 || idx >= model.size()) {
                return;
            }
            String value = model.getElementAt(idx);
            editor.setText(value);
            lastAccepted = value;
            dismissPopup();
        }

        private void dismissPopup() {
            popupVisible = false;
            list.clearSelection();
        }

        boolean isPopupVisible() {
            return popupVisible;
        }

        String getLastAccepted() {
            return lastAccepted;
        }
    }

    private FrameFixture window;
    private DropDownPanel panel;

    @Override
    protected void onSetUp() {
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new DropDownPanel();
                JFrame f = new JFrame("Drop-Down Keyboard Navigation");
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.getContentPane().add(panel);
                f.pack();
                f.setLocationRelativeTo(null);
                return f;
            }
        });

        window = new FrameFixture(robot(), frame);
        window.show();
        window.textBox("editorField").focus();
    }

    @Override
    protected void onTearDown() {
        if (window != null) {
            window.cleanUp();
            window = null;
        }
    }

    /**
     * DOWN/UP arrows should move the highlighted suggestion within the list.
     */
    @Test
    public void testArrowKeysNavigateSuggestions() {
        var listFixture = window.list("acList");

        // Initial selection should be the first element.
        listFixture.requireSelection("Animal");

        // DOWN -> "Animate"
        window.textBox("editorField").pressAndReleaseKeys(KeyEvent.VK_DOWN);
        listFixture.requireSelection("Animate");

        // DOWN again -> "agent"
        window.textBox("editorField").pressAndReleaseKeys(KeyEvent.VK_DOWN);
        listFixture.requireSelection("agent");

        // UP -> back to "Animate"
        window.textBox("editorField").pressAndReleaseKeys(KeyEvent.VK_UP);
        listFixture.requireSelection("Animate");
    }

    /**
     * ENTER should accept the current suggestion and close the popup.
     */
    @Test
    public void testEnterAcceptsCurrentSelection() {
        var editorFixture = window.textBox("editorField");
        var listFixture = window.list("acList");

        // Move to "Animate"
        editorFixture.pressAndReleaseKeys(KeyEvent.VK_DOWN);
        listFixture.requireSelection("Animate");

        // ENTER accepts "Animate"
        editorFixture.pressAndReleaseKeys(KeyEvent.VK_ENTER);

        editorFixture.requireText("Animate");
        assertEquals("Animate", panel.getLastAccepted());
        assertFalse(panel.isPopupVisible());
        // After dismissal, list has no active selection
        assertEquals(-1, listFixture.target().getSelectedIndex());
    }

    /**
     * TAB should behave like ENTER and accept the current suggestion.
     */
    @Test
    public void testTabAcceptsCurrentSelection() {
        var editorFixture = window.textBox("editorField");
        var listFixture = window.list("acList");

        // Reset by recreating movement: DOWN twice to "agent"
        editorFixture.pressAndReleaseKeys(KeyEvent.VK_DOWN);
        editorFixture.pressAndReleaseKeys(KeyEvent.VK_DOWN);
        listFixture.requireSelection("agent");

        // TAB accepts "agent"
        editorFixture.pressAndReleaseKeys(KeyEvent.VK_TAB);

        editorFixture.requireText("agent");
        assertEquals("agent", panel.getLastAccepted());
        assertFalse(panel.isPopupVisible());
        assertEquals(-1, listFixture.target().getSelectedIndex());
    }

    /**
     * ESC should dismiss the popup without changing the editor text.
     */
    @Test
    public void testEscDismissesPopupWithoutChangingEditor() {
        var editorFixture = window.textBox("editorField");
        var listFixture = window.list("acList");

        editorFixture.requireText("a");
        assertTrue(panel.isPopupVisible());
        listFixture.requireSelection("Animal");

        // ESC closes the popup but keeps the text "a"
        editorFixture.pressAndReleaseKeys(KeyEvent.VK_ESCAPE);

        editorFixture.requireText("a");
        assertFalse(panel.isPopupVisible());
        assertEquals(-1, listFixture.target().getSelectedIndex());
        assertNull(panel.getLastAccepted());
    }
}