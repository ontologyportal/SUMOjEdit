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
 * GUI-level tests for keyboard interaction with a minimal Ghost Text
 * inline suggestion UI. This harness does NOT depend on jEdit or the
 * real SUMOjEdit plugin; it models the behaviour of:
 *
 *  - Accepting a ghost suggestion via Ctrl+Tab
 *  - Dismissing the suggestion via Escape
 *  - Cancelling the suggestion when the caret moves
 *
 * The panel starts with:
 *   - editor text: "inst"
 *   - ghost suggestion: "ance" (full suggestion "instance")
 * 
 * 
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class GhostTextKeyboardNavigationGUITest extends AssertJSwingJUnitTestCase {

    /**
     * Minimal inline-ghost editor harness.
     */
    private static final class GhostEditorPanel extends JPanel {

        private final JTextField editor;
        private final JLabel ghostLabel;

        private String fullSuggestion = "instance";
        private String lastAccepted   = null;

        GhostEditorPanel() {
            super(new BorderLayout());

            editor = new JTextField("inst");
            editor.setName("editorField");
            // Ensure Ctrl+Tab is delivered to the editor's InputMap instead of
            // being consumed as a focus traversal key.
            editor.setFocusTraversalKeysEnabled(false);

            ghostLabel = new JLabel("ance");
            ghostLabel.setName("ghostLabel");

            JPanel inline = new JPanel(new BorderLayout());
            inline.add(editor, BorderLayout.CENTER);
            inline.add(ghostLabel, BorderLayout.EAST);

            add(inline, BorderLayout.CENTER);

            installKeyBindings();
        }

        private void installKeyBindings() {
            InputMap im = editor.getInputMap(JComponent.WHEN_FOCUSED);
            ActionMap am = editor.getActionMap();

            // Accept ghost suggestion with Ctrl+Tab
            KeyStroke ctrlTab = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.CTRL_DOWN_MASK);
            im.put(ctrlTab, "acceptGhost");
            am.put("acceptGhost", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    acceptGhost();
                }
            });

            // Dismiss ghost suggestion with Escape
            KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
            im.put(esc, "dismissGhost");
            am.put("dismissGhost", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    dismissGhost();
                }
            });

            // Moving the caret cancels the ghost (LEFT/RIGHT here for simplicity)
            KeyStroke left = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0);
            KeyStroke right = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0);
            im.put(left, "moveLeftAndCancelGhost");
            im.put(right, "moveRightAndCancelGhost");

            am.put("moveLeftAndCancelGhost", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    int pos = Math.max(0, editor.getCaretPosition() - 1);
                    editor.setCaretPosition(pos);
                    dismissGhost();
                }
            });
            am.put("moveRightAndCancelGhost", new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    int pos = Math.min(editor.getText().length(), editor.getCaretPosition() + 1);
                    editor.setCaretPosition(pos);
                    dismissGhost();
                }
            });
        }

        private void acceptGhost() {
            String base = editor.getText();
            if (ghostLabel.getText().isEmpty()) {
                return;
            }
            // Only accept if the full suggestion extends the current text
            if (fullSuggestion.startsWith(base)) {
                editor.setText(fullSuggestion);
                ghostLabel.setText("");
                lastAccepted = fullSuggestion;
            }
        }

        private void dismissGhost() {
            ghostLabel.setText("");
        }

        String getLastAccepted() {
            return lastAccepted;
        }
    }

    private FrameFixture window;
    private GhostEditorPanel panel;

    @Override
    protected void onSetUp() {
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new GhostEditorPanel();
                JFrame f = new JFrame("Ghost Text Keyboard Navigation");
                f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                f.getContentPane().add(panel);
                f.pack();
                f.setLocationRelativeTo(null);
                return f;
            }
        });

        window = new FrameFixture(robot(), frame);
        window.show();
        // Ensure keyboard focus on the editor
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
     * Ctrl+Tab should accept the inline ghost suggestion, replacing
     * the editor contents with the full suggestion and clearing the
     * ghost UI.
     */
    @Test
    public void testCtrlTabAcceptsGhostSuggestion() {
        window.textBox("editorField").requireText("inst");
        window.label("ghostLabel").requireText("ance");

        // Press Ctrl+Tab chord using the underlying robot so that the
        // CTRL modifier is actually held down while TAB is sent.
        window.textBox("editorField").focus();
        robot().pressKey(KeyEvent.VK_CONTROL);
        robot().pressAndReleaseKey(KeyEvent.VK_TAB);
        robot().releaseKey(KeyEvent.VK_CONTROL);

        window.textBox("editorField").requireText("instance");
        window.label("ghostLabel").requireText("");

        assertEquals("instance", panel.getLastAccepted());
    }

    /**
     * Escape should dismiss the ghost suggestion but leave the base
     * editor text untouched.
     */
    @Test
    public void testEscapeDismissesGhostWithoutAccepting() {
        window.textBox("editorField").requireText("inst");
        window.label("ghostLabel").requireText("ance");

        window.textBox("editorField").pressAndReleaseKeys(KeyEvent.VK_ESCAPE);

        window.textBox("editorField").requireText("inst");
        window.label("ghostLabel").requireText("");
        assertNull(panel.getLastAccepted());
    }

    /**
     * Moving the caret via LEFT or RIGHT should cancel the inline
     * ghost suggestion so that the user can freely edit the text.
     */
    @Test
    public void testCaretMovementCancelsGhost() {
        window.textBox("editorField").requireText("inst");
        window.label("ghostLabel").requireText("ance");

        // Move caret left by one; our harness cancels ghost on caret move.
        window.textBox("editorField").pressAndReleaseKeys(KeyEvent.VK_LEFT);

        window.label("ghostLabel").requireText("");
        // Text remains as typed.
        window.textBox("editorField").requireText("inst");
        assertNull(panel.getLastAccepted());
    }
}