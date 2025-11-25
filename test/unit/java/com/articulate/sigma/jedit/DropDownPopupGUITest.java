package com.articulate.sigma.jedit;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.Test;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * GUI-level tests for a minimal AutoComplete drop-down popup harness.
 *
 * This does NOT depend on the real jEdit TextArea or AutoCompleteManager.
 * It wires a small panel that:
 *
 *  - filters a static vocabulary as the user types
 *  - shows a JPopupMenu with a JList of suggestions
 *  - binds Up/Down to move selection and Enter to accept
 *
 * The goal is to lock in the expected drop-down interaction pattern in
 * a deterministic, self-contained way.
 * 
 * 
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class DropDownPopupGUITest extends AssertJSwingJUnitTestCase {

    /**
     * Minimal panel that simulates drop-down AC behaviour.
     */
    private static final class DropdownPanel extends JPanel {

        private final JTextField editor = new JTextField(20);
        private final JPopupMenu popup = new JPopupMenu();
        private final JList<String> list = new JList<>();
        private final DefaultListModel<String> model = new DefaultListModel<>();
        private final List<String> vocab;

        DropdownPanel(List<String> vocab) {
            super(new BorderLayout());
            this.vocab = vocab;

            editor.setName("dropdownEditor");

            list.setModel(model);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setVisibleRowCount(5);

            popup.add(new JScrollPane(list));

            add(editor, BorderLayout.NORTH);

            installDocumentListener();
            installKeyBindings();
        }

        private void installDocumentListener() {
            editor.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateSuggestions();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateSuggestions();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateSuggestions();
                }
            });
        }

        private void installKeyBindings() {
            // Down arrow: move selection to next item
            editor.getInputMap(JComponent.WHEN_FOCUSED).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "selectNext");
            editor.getActionMap().put("selectNext", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    moveSelection(1);
                }
            });

            // Up arrow: move selection to previous item
            editor.getInputMap(JComponent.WHEN_FOCUSED).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "selectPrev");
            editor.getActionMap().put("selectPrev", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    moveSelection(-1);
                }
            });

            // Enter: accept the currently selected suggestion
            editor.getInputMap(JComponent.WHEN_FOCUSED).put(
                    KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "acceptSelection");
            editor.getActionMap().put("acceptSelection", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    acceptSelection();
                }
            });
        }

        private void updateSuggestions() {
            String prefix = editor.getText();
            if (prefix == null) {
                prefix = "";
            }
            prefix = prefix.trim().toLowerCase();

            model.clear();
            if (prefix.isEmpty()) {
                popup.setVisible(false);
                return;
            }

            for (String v : vocab) {
                if (v.toLowerCase().startsWith(prefix)) {
                    model.addElement(v);
                }
            }

            if (model.isEmpty()) {
                popup.setVisible(false);
            } else {
                if (list.getSelectedIndex() < 0 && model.getSize() > 0) {
                    list.setSelectedIndex(0);
                }
                if (!popup.isVisible()) {
                    popup.show(editor, 0, editor.getHeight());
                }
            }
        }

        private void moveSelection(int delta) {
            if (!popup.isVisible() || model.isEmpty()) {
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
            }
            if (idx >= model.getSize()) {
                idx = model.getSize() - 1;
            }
            list.setSelectedIndex(idx);
            list.ensureIndexIsVisible(idx);
        }

        private void acceptSelection() {
            if (!popup.isVisible()) {
                return;
            }
            int idx = list.getSelectedIndex();
            if (idx < 0 || idx >= model.getSize()) {
                return;
            }
            String value = model.getElementAt(idx);
            editor.setText(value);
            popup.setVisible(false);
        }

        JTextField getEditor() {
            return editor;
        }

        JList<String> getList() {
            return list;
        }

        JPopupMenu getPopup() {
            return popup;
        }
    }

    private FrameFixture window;
    private DropdownPanel panel;

    @Override
    protected void onSetUp() {
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new DropdownPanel(
                        Arrays.asList("Animal", "Animate", "agent", "Zebra"));
                JFrame f = new JFrame("Dropdown AC Harness");
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

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    @Test
    public void testPopupShowsFilteredSuggestions() {
        window.textBox("dropdownEditor").setText("an");

        JPopupMenu popup = panel.getPopup();
        JList<String> list = panel.getList();

        assertTrue("Popup should be visible when there are matches", popup.isVisible());
        // With prefix "an", only "Animal" and "Animate" match.
        assertEquals(2, list.getModel().getSize());
        assertEquals("Animal", list.getModel().getElementAt(0));
        assertEquals("Animate", list.getModel().getElementAt(1));
    }

    @Test
    public void testArrowKeyBindingsAndSelectionMovement() {
        JTextField editor = panel.getEditor();

        // Trigger suggestions.
        window.textBox("dropdownEditor").setText("a");
        assertTrue(panel.getPopup().isVisible());
        assertTrue(panel.getList().getModel().getSize() > 0);

        // Verify key bindings are wired.
        Object nextKey = editor.getInputMap(JComponent.WHEN_FOCUSED).get(
                KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
        Object prevKey = editor.getInputMap(JComponent.WHEN_FOCUSED).get(
                KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
        assertEquals("selectNext", nextKey);
        assertEquals("selectPrev", prevKey);

        Action nextAction = editor.getActionMap().get(nextKey);
        Action prevAction = editor.getActionMap().get(prevKey);
        assertNotNull(nextAction);
        assertNotNull(prevAction);

        // Start at whatever initial selection is, then move down and up.
        JList<String> list = panel.getList();
        int initial = list.getSelectedIndex();
        assertTrue("Initial selection index should be valid", initial >= 0);

        GuiActionRunner.execute(() -> nextAction.actionPerformed(
                new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, "selectNext")));
        int afterNext = list.getSelectedIndex();
        assertTrue("Selection index should move forward or clamp", afterNext >= initial);

        GuiActionRunner.execute(() -> prevAction.actionPerformed(
                new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, "selectPrev")));
        int afterPrev = list.getSelectedIndex();
        // Should remain within bounds
        assertTrue(afterPrev >= 0);
        assertTrue(afterPrev < list.getModel().getSize());
    }

    @Test
    public void testEnterAcceptsSelectedSuggestion() {
        JTextField editor = panel.getEditor();

        // Make sure we have suggestions.
        window.textBox("dropdownEditor").setText("ani");
        assertTrue(panel.getPopup().isVisible());
        JList<String> list = panel.getList();
        assertTrue(list.getModel().getSize() > 0);

        // Select the second suggestion if available (must run on the EDT).
        if (list.getModel().getSize() > 1) {
            GuiActionRunner.execute(() -> list.setSelectedIndex(1));
        }

        Object acceptKey = editor.getInputMap(JComponent.WHEN_FOCUSED).get(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        assertEquals("acceptSelection", acceptKey);

        Action acceptAction = editor.getActionMap().get(acceptKey);
        assertNotNull(acceptAction);

        GuiActionRunner.execute(() -> acceptAction.actionPerformed(
                new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, "acceptSelection")));

        String text = editor.getText();
        assertEquals(list.getSelectedValue(), text);
        assertFalse("Popup should hide after accepting a suggestion", panel.getPopup().isVisible());
    }
}