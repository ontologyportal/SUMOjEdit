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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * GUI tests for a harness that approximates SUMOjEdit's drop-down
 * completion popup (SimpleCompletionPopup-style behavior).
 *
 * Covered behaviors:
 *  - Prefix-based token collection from a "buffer"
 *  - Case-insensitive prefix filtering & sorting
 *  - Popup visibility rules (no popup for empty prefix / no matches)
 *  - Keyboard navigation with UP/DOWN
 *  - ENTER / TAB accept the current selection and hide the popup
 *  - ESC hides the popup without changing the editor text
 *
 * NOTE: This class does NOT depend on jEdit runtime and uses a small
 * harness frame instead.
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class DropDownPopupGUITest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;
    private CompletionHarnessFrame frame;

    @Override
    protected void onSetUp() {
        frame = GuiActionRunner.execute(new GuiQuery<CompletionHarnessFrame>() {
            @Override
            protected CompletionHarnessFrame executeInEDT() {
                return new CompletionHarnessFrame();
            }
        });
        window = new FrameFixture(robot(), frame);
        window.show();
    }

    // -------------------------------------------------------------------------
    // Basic popup behavior
    // -------------------------------------------------------------------------

    @Test
    public void testPopupAppearsWithExpectedSuggestions() {
        GuiActionRunner.execute(() -> {
            frame.setBufferText("Animal Animate agent Zebra");
            frame.getEditor().setText("an");
            frame.getEditor().setCaretPosition(2); // after "an"
            frame.showPopupForCurrentPrefix();
        });

        // Popup must be visible.
        GuiActionRunner.execute(() -> assertTrue(frame.isPopupVisible()));

        // Suggestions filtered and sorted: "Animal", "Animate" (case-insensitive).
        window.list("completionList").requireItemCount(2);
        String[] contents = window.list("completionList").contents();
        assertEquals("Animal", contents[0]);
        assertEquals("Animate", contents[1]);

        // First item is selected by default.
        window.list("completionList").requireSelection(0);
    }

    @Test
    public void testArrowKeysNavigateSelection() {
        GuiActionRunner.execute(() -> {
            frame.setBufferText("Animal Animate agent");
            frame.getEditor().setText("an");
            frame.getEditor().setCaretPosition(2);
            frame.showPopupForCurrentPrefix();
        });

        window.list("completionList").requireItemCount(2);
        window.list("completionList").requireSelection(0);

        // Press DOWN: should move to index 1.
        window.list("completionList").pressAndReleaseKeys(KeyEvent.VK_DOWN);
        window.list("completionList").requireSelection(1);

        // Press UP: should move back to index 0.
        window.list("completionList").pressAndReleaseKeys(KeyEvent.VK_UP);
        window.list("completionList").requireSelection(0);
    }

    @Test
    public void testEnterAcceptsSelectionAndHidesPopup() {
        GuiActionRunner.execute(() -> {
            frame.setBufferText("Animal Animate agent");
            frame.getEditor().setText("an");
            frame.getEditor().setCaretPosition(2);
            frame.showPopupForCurrentPrefix();
        });

        window.list("completionList").requireItemCount(2);
        window.list("completionList").requireSelection(0);

        // Press ENTER on the list: accept current selection.
        window.list("completionList").pressAndReleaseKeys(KeyEvent.VK_ENTER);

        window.textBox("completionEditor").requireText("Animal");
        GuiActionRunner.execute(() -> assertFalse(frame.isPopupVisible()));
    }

    @Test
    public void testTabAcceptsSelectionAndHidesPopup() {
        GuiActionRunner.execute(() -> {
            frame.setBufferText("Animal Animate agent");
            frame.getEditor().setText("an");
            frame.getEditor().setCaretPosition(2);
            frame.showPopupForCurrentPrefix();
        });

        window.list("completionList").requireItemCount(2);
        window.list("completionList").requireSelection(0);

        // Press TAB on the list: accept current selection.
        window.list("completionList").pressAndReleaseKeys(KeyEvent.VK_TAB);

        window.textBox("completionEditor").requireText("Animal");
        GuiActionRunner.execute(() -> assertFalse(frame.isPopupVisible()));
    }

    @Test
    public void testEscapeClosesPopupWithoutChangingText() {
        GuiActionRunner.execute(() -> {
            frame.setBufferText("Animal Animate agent");
            frame.getEditor().setText("an");
            frame.getEditor().setCaretPosition(2);
            frame.showPopupForCurrentPrefix();
        });

        window.list("completionList").requireItemCount(2);
        window.list("completionList").requireSelection(0);

        // Press ESC: close popup without touching editor text.
        window.list("completionList").pressAndReleaseKeys(KeyEvent.VK_ESCAPE);

        window.textBox("completionEditor").requireText("an");
        GuiActionRunner.execute(() -> assertFalse(frame.isPopupVisible()));
    }

    // -------------------------------------------------------------------------
    // Prefix filtering & sorting
    // -------------------------------------------------------------------------

    @Test
    public void testSuggestionsArePrefixFilteredAndSorted() {
        GuiActionRunner.execute(() -> {
            frame.setBufferText("zebra Zeta agent Animate animal");
            frame.getEditor().setText("a");
            frame.getEditor().setCaretPosition(1);
            frame.showPopupForCurrentPrefix();
        });

        List<String> items = GuiActionRunner.execute(new GuiQuery<List<String>>() {
            @Override
            protected List<String> executeInEDT() {
                DefaultListModel<String> model = frame.getListModel();
                List<String> result = new ArrayList<>();
                for (int i = 0; i < model.size(); i++) {
                    result.add(model.get(i));
                }
                return result;
            }
        });

        assertTrue("Should have at least three 'a*' tokens", items.size() >= 3);
        for (String s : items) {
            assertTrue("Every suggestion must start with 'a' (case-insensitive)",
                    s.toLowerCase().startsWith("a"));
        }

        List<String> sorted = new ArrayList<>(items);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        assertEquals("Suggestions should be sorted case-insensitively", sorted, items);

        window.list("completionList").requireSelection(0);
    }

    @Test
    public void testNoPopupForEmptyPrefix() {
        GuiActionRunner.execute(() -> {
            frame.setBufferText("Animal Animate agent");
            frame.getEditor().setText(" ");
            frame.getEditor().setCaretPosition(1); // caret after a space -> empty prefix.
            frame.showPopupForCurrentPrefix();
        });

        GuiActionRunner.execute(() -> assertFalse(frame.isPopupVisible()));
    }

    // -------------------------------------------------------------------------
    // Harness
    // -------------------------------------------------------------------------

    /**
     * Minimal frame that mirrors the behavior of a SimpleCompletionPopup
     * backed by:
     *  - a single-line editor (JTextField)
     *  - a JList inside a JPopupMenu
     *  - a synthetic "buffer" string for token collection.
     */
    private static class CompletionHarnessFrame extends JFrame {

        private final JTextField editor = new JTextField(30);
        private final DefaultListModel<String> model = new DefaultListModel<>();
        private final JList<String> list = new JList<>(model);
        private final JPopupMenu popup = new JPopupMenu();

        // Simulated buffer analogous to a jEdit buffer's full text.
        private String bufferText = "";

        CompletionHarnessFrame() {
            super("DropDownPopup Harness");
            setName("dropDownPopupFrame");
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setLayout(new BorderLayout());

            editor.setName("completionEditor");
            add(editor, BorderLayout.NORTH);

            list.setName("completionList");
            list.setVisibleRowCount(8);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            JScrollPane scroller = new JScrollPane(list);
            scroller.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            popup.add(scroller);

            installEditorDocumentListener();
            installListKeyHandlers();
            pack();
            setLocationRelativeTo(null);
        }

        JTextField getEditor() {
            return editor;
        }

        DefaultListModel<String> getListModel() {
            return model;
        }

        boolean isPopupVisible() {
            return popup.isVisible();
        }

        void setBufferText(String text) {
            this.bufferText = (text == null) ? "" : text;
        }

        /**
         * Public entry point used by tests: compute prefix from the current
         * caret position, collect tokens from the buffer, filter & sort, then
         * show or hide the popup accordingly.
         */
        void showPopupForCurrentPrefix() {
            String prefix = currentPrefix();
            if (prefix.isEmpty()) {
                hidePopup();
                return;
            }

            Set<String> tokens = new LinkedHashSet<>();
            collectTokens(bufferText, tokens);

            List<String> candidates = new ArrayList<>();
            String lower = prefix.toLowerCase();
            for (String t : tokens) {
                if (t.toLowerCase().startsWith(lower)) {
                    candidates.add(t);
                }
            }

            if (candidates.isEmpty()) {
                hidePopup();
                return;
            }

            candidates.sort(String.CASE_INSENSITIVE_ORDER);
            model.clear();
            for (String c : candidates) {
                model.addElement(c);
            }

            list.setSelectedIndex(0);
            popup.show(editor, 0, editor.getHeight());
            popup.setVisible(true);
            list.requestFocusInWindow();
        }

        // -----------------------------------------------------------------
        // Helpers mirroring SimpleCompletionPopup logic
        // -----------------------------------------------------------------

        private String currentPrefix() {
            String text = editor.getText();
            if (text == null) return "";
            int caret = editor.getCaretPosition();
            if (caret < 0 || caret > text.length()) return "";

            int start = caret;
            while (start > 0) {
                char c = text.charAt(start - 1);
                if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) {
                    break;
                }
                start--;
            }

            int len = caret - start;
            if (len <= 0) {
                return "";
            }
            return text.substring(start, caret);
        }

        private void collectTokens(String text, Set<String> out) {
            if (text == null || text.isEmpty()) return;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                    sb.append(c);
                } else {
                    if (sb.length() > 0) {
                        out.add(sb.toString());
                        sb.setLength(0);
                    }
                }
            }
            if (sb.length() > 0) {
                out.add(sb.toString());
            }
        }

        private void acceptSelection() {
            String selection = list.getSelectedValue();
            if (selection == null) {
                hidePopup();
                return;
            }

            String text = editor.getText();
            if (text == null) {
                text = "";
            }

            int caret = editor.getCaretPosition();
            String prefix = currentPrefix();
            if (prefix.isEmpty()) {
                hidePopup();
                return;
            }

            int start = caret - prefix.length();
            if (start < 0) {
                start = 0;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(text, 0, start);
            sb.append(selection);
            if (caret < text.length()) {
                sb.append(text.substring(caret));
            }

            String newText = sb.toString();
            editor.setText(newText);
            editor.setCaretPosition(start + selection.length());

            hidePopup();
        }

        private void hidePopup() {
            popup.setVisible(false);
        }

        private void installEditorDocumentListener() {
            editor.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    // In real SUMOjEdit we would trigger popup automatically.
                    // For safety we don't auto-open here; the tests call
                    // showPopupForCurrentPrefix() explicitly to avoid timing issues.
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                }
            });
        }

        private void installListKeyHandlers() {
            // Disable focus traversal so TAB is delivered as a normal key event.
            list.setFocusTraversalKeysEnabled(false);

            list.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int code = e.getKeyCode();
                    if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_TAB) {
                        acceptSelection();
                        e.consume();
                    } else if (code == KeyEvent.VK_ESCAPE) {
                        hidePopup();
                        e.consume();
                    }
                }
            });

            // We still want UP / DOWN default behavior for changing selection,
            // so no extra wiring is necessary beyond focus on the list.
        }
    }
}