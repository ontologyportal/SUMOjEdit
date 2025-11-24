package com.articulate.sigma.jedit;

import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.edt.GuiQuery;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.junit.testcase.AssertJSwingJUnitTestCase;
import org.junit.Test;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI-dependent test that exercises a minimal ErrorList ↔ editor
 * navigation flow:
 *
 *  - A table representing the ErrorList, with one row per error
 *  - A simple text editor component
 *  - When a row is selected, the editor "jumps" to the error's
 *    line/column
 *
 * This is a small Swing harness that mirrors the behaviour of the
 * real ErrorList window: selecting an error updates the caret
 * position in the associated editor.
 */
public class ErrorListNavigationGUITest extends AssertJSwingJUnitTestCase {

    private FrameFixture window;
    private ErrorListPanel panel;

    // Simple immutable record for one error row
    private static final class ErrorEntry {
        final String file;
        final int line;
        final int column;
        final String message;

        ErrorEntry(String file, int line, int column, String message) {
            this.file = file;
            this.line = line;
            this.column = column;
            this.message = message;
        }
    }

    /**
     * Fake editor: records the last requested line/column so we can
     * assert that navigation happened correctly.
     */
    private static final class FakeEditor extends JTextArea {
        int lastLine   = -1;
        int lastColumn = -1;

        FakeEditor() {
            setName("editorArea");
            setText(
                "line 1: something\n" +
                "line 2: something else\n" +
                "line 3: another line\n" +
                "line 4: yet another line\n" +
                "line 5: error here?\n" +
                "line 6: more content\n" +
                "line 7: final line\n"
            );
        }

        void goTo(int line, int column) {
            this.lastLine = line;
            this.lastColumn = column;
        }
    }

    /**
     * Table model which exposes the error entries to a JTable.
     */
    private static final class ErrorTableModel extends AbstractTableModel {

        private final List<ErrorEntry> entries;

        ErrorTableModel(List<ErrorEntry> entries) {
            this.entries = entries;
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return 3; // file, line, message
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0: return "File";
                case 1: return "Line";
                case 2: return "Message";
                default: return "";
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ErrorEntry e = entries.get(rowIndex);
            switch (columnIndex) {
                case 0: return e.file;
                case 1: return e.line;
                case 2: return e.message;
                default: return "";
            }
        }
    }

    /**
     * Panel that hosts:
     *  - a JTable for the errors
     *  - the FakeEditor
     *
     * Selecting a row in the table causes the editor to "jump" to the
     * corresponding line/column (recorded in FakeEditor).
     */
    private static final class ErrorListPanel extends JPanel {

        final FakeEditor editor;
        final JTable table;
        final List<ErrorEntry> entries;

        ErrorListPanel(List<ErrorEntry> entries) {
            super(new BorderLayout());
            this.entries = entries;

            this.editor = new FakeEditor();
            this.table = new JTable(new ErrorTableModel(entries));

            table.setName("errorTable");
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            ListSelectionListener listener = e -> {
                if (e.getValueIsAdjusting()) {
                    return;
                }
                int row = table.getSelectedRow();
                if (row >= 0 && row < entries.size()) {
                    ErrorEntry entry = entries.get(row);
                    editor.goTo(entry.line, entry.column);
                }
            };
            table.getSelectionModel().addListSelectionListener(listener);

            add(new JScrollPane(table), BorderLayout.WEST);
            add(new JScrollPane(editor), BorderLayout.CENTER);
        }
    }

    @Override
    protected void onSetUp() {
        final List<ErrorEntry> demoEntries = new ArrayList<>();
        demoEntries.add(new ErrorEntry("demo1.kif", 5, 10, "First error on line 5"));
        demoEntries.add(new ErrorEntry("demo1.kif", 2, 3,  "Second error on line 2"));
        demoEntries.add(new ErrorEntry("demo2.kif", 7, 1,  "Error in another file"));

        JFrame frame = GuiActionRunner.execute(new GuiQuery<JFrame>() {
            @Override
            protected JFrame executeInEDT() {
                panel = new ErrorListPanel(demoEntries);
                JFrame f = new JFrame("ErrorList Navigation Test");
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
    public void testSelectingSingleErrorMovesEditorToExpectedLineAndColumn() {
        // Select the first error (line 5, column 10)
        window.table("errorTable").selectRows(0);

        FakeEditor editor = panel.editor;
        org.junit.Assert.assertEquals(5, editor.lastLine);
        org.junit.Assert.assertEquals(10, editor.lastColumn);
    }

    @Test
    public void testSelectingDifferentRowsUpdatesEditorNavigation() {
        FakeEditor editor = panel.editor;

        // First row: line 5
        window.table("errorTable").selectRows(0);
        org.junit.Assert.assertEquals(5, editor.lastLine);
        org.junit.Assert.assertEquals(10, editor.lastColumn);

        // Second row: line 2
        window.table("errorTable").selectRows(1);
        org.junit.Assert.assertEquals(2, editor.lastLine);
        org.junit.Assert.assertEquals(3, editor.lastColumn);

        // Third row: line 7
        window.table("errorTable").selectRows(2);
        org.junit.Assert.assertEquals(7, editor.lastLine);
        org.junit.Assert.assertEquals(1, editor.lastColumn);
    }
}