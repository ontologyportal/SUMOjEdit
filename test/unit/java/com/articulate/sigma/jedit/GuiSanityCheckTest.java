package com.articulate.sigma.jedit;

import org.junit.Test;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.assertj.swing.fixture.FrameFixture;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Minimal AssertJ Swing sanity test to verify that the GUI test
 * infrastructure is wired correctly (Ivy dependency, Ant test
 * target, robot, EDT handling, etc.).
 *
 * This does NOT touch SUMOjEdit yet – it just proves we can launch
 * a Swing window, interact with it via AssertJ Swing, and assert
 * on the result.
 */
public class GuiSanityCheckTest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;

    @Override
    protected void onSetUp() {
        // Create a very small Swing UI on the EDT.
        JFrame frame = new JFrame("Sanity");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JLabel label = new JLabel("initial");
        JButton button = new JButton("Copy");
        button.setName("copyButton");
        label.setName("resultLabel");

        button.addActionListener(e -> label.setText("clicked"));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(button, BorderLayout.NORTH);
        panel.add(label, BorderLayout.CENTER);

        frame.setContentPane(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);

        // Wrap the frame in a FrameFixture so AssertJ Swing can drive it.
        window = new FrameFixture(robot(), frame);
        window.show(); // shows the frame to test
    }

    @Override
    protected void onTearDown() {
        if (window != null) {
            window.cleanUp();
        }
    }

    @Test
    public void testButtonClickChangesLabel() {
        // Click the button
        window.button("copyButton").click();

        // Verify the label text was updated
        window.label("resultLabel").requireText("clicked");
    }
}