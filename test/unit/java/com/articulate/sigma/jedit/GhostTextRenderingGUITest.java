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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * GUI-level tests for a minimal Ghost Text rendering harness.
 *
 * This does NOT use the real jEdit TextArea or SUMOjEdit AC classes.
 * Instead it wires a small Swing panel that:
 *
 *  - shows a "typed" prefix in a text field
 *  - renders a ghost suggestion for the remainder of the best match
 *  - binds Ctrl+Tab to accept the ghost suggestion
 *
 * The goal is to lock in the expected user-facing behaviour and key
 * binding semantics without depending on the full plugin runtime.
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class GhostTextRenderingGUITest extends AssertJSwingJUnitTestCase {

    /**
     * Minimal panel that simulates ghost text behaviour.
     */
    private static final class GhostTextPanel extends JPanel {

        private final JTextField editor = new JTextField(20);
        private final JLabel ghostLabel = new JLabel();
        private final List<String> candidates;

        GhostTextPanel(List<String> candidates) {
            super(new BorderLayout());
            this.candidates = candidates;

            editor.setName("ghostEditor");
            ghostLabel.setName("ghostOverlayLabel");
            ghostLabel.setForeground(Color.LIGHT_GRAY);

            JPanel inner = new JPanel(new BorderLayout());
            inner.add(editor, BorderLayout.CENTER);
            inner.add(ghostLabel, BorderLayout.EAST);

            add(inner, BorderLayout.NORTH);

            installDocumentListener();
            installKeyBinding();
        }

        private void installDocumentListener() {
            editor.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updateGhost();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updateGhost();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updateGhost();
                }
            });
        }

        private void installKeyBinding() {
            KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
            String actionKey = "acceptGhost";

            editor.getInputMap(JComponent.WHEN_FOCUSED).put(ks, actionKey);
            editor.getActionMap().put(actionKey, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    acceptGhost();
                }
            });
        }

        private void updateGhost() {
            String text = editor.getText();
            if (text == null) {
                text = "";
            }
            text = text.trim();
            if (text.isEmpty()) {
                ghostLabel.setText("");
                return;
            }

            String best = null;
            for (String cand : candidates) {
                if (cand.toLowerCase().startsWith(text.toLowerCase()) &&
                    !cand.equals(text)) {
                    if (best == null || cand.length() < best.length()) {
                        best = cand;
                    }
                }
            }

            if (best == null) {
                ghostLabel.setText("");
            } else {
                // Show only the remaining part of the suggestion, as ghost.
                String remainder = best.substring(text.length());
                ghostLabel.setText(remainder);
            }
        }

        private void acceptGhost() {
            String remainder = ghostLabel.getText();
            if (remainder == null || remainder.isEmpty()) {
                return;
            }
            editor.setText(editor.getText() + remainder);
            ghostLabel.setText("");
        }

        JTextField getEditor() {
            return editor;
        }

        JLabel getGhostLabel() {
            return ghostLabel;
        }
    }

    private FrameFixture window;
    private GhostTextPanel panel;

    @Override
    protected void onSetUp() {
        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new GhostTextPanel(
                        Arrays.asList("instance", "individual", "Human", "Animal"));
                JFrame f = new JFrame("Ghost Text Harness");
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
    public void testGhostAppearsForMatchingPrefix() {
        window.textBox("ghostEditor").setText("inst");

        JLabel ghost = panel.getGhostLabel();
        assertEquals("ance", ghost.getText()); // "instance" remainder
    }

    @Test
    public void testGhostClearsWhenNoMatch() {
        window.textBox("ghostEditor").setText("xyz");

        JLabel ghost = panel.getGhostLabel();
        assertEquals("", ghost.getText());
    }

    @Test
    public void testCtrlTabBindingAndAcceptanceAction() {
        JTextField editor = panel.getEditor();
        JLabel ghost = panel.getGhostLabel();

        // Start with a prefix that has a suggestion.
        window.textBox("ghostEditor").setText("inst");
        assertEquals("ance", ghost.getText());

        // Verify the InputMap actually has Ctrl+Tab bound to "acceptGhost".
        KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK);
        Object actionKey = editor.getInputMap(JComponent.WHEN_FOCUSED).get(ks);
        assertEquals("acceptGhost", actionKey);

        // Invoke the action directly to avoid platform-specific key issues.
        Action a = editor.getActionMap().get(actionKey);
        assertNotNull(a);

        GuiActionRunner.execute(() -> a.actionPerformed(
                new ActionEvent(editor, ActionEvent.ACTION_PERFORMED, "acceptGhost")));

        // After acceptance, the editor should contain the full word and the ghost should clear.
        assertEquals("instance", editor.getText());
        assertEquals("", ghost.getText());
    }
}