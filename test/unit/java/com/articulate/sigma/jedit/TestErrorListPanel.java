package com.articulate.sigma.jedit;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Minimal Swing panel that simulates the jEdit ErrorList UI for testing.
 *
 * It is backed by a {@link DefaultErrorSource} and exposes:
 *  - a non-editable JTable named "errorTable"
 *  - a JLabel named "detailsLabel" that shows the selected error
 *
 * The panel does NOT depend on jEdit or the real ErrorList plugin. It just
 * reflects whatever is currently in the {@link DefaultErrorSource}.
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class TestErrorListPanel extends JPanel {

    private final DefaultErrorSource source;
    private final DefaultTableModel model;
    private final JTable table;
    private final JLabel detailsLabel;

    private ErrorSource.Error[] backing = new ErrorSource.Error[0];

    /**
     * Best-effort extraction of the 0-based line number from an ErrorSource.Error.
     * We avoid depending on a specific ErrorList API version by using reflection.
     */
    private static int extractLine(ErrorSource.Error err) {
        if (err == null) {
            return 0;
        }
        // Try a getLineNumber() method first, if present.
        try {
            Method m = err.getClass().getMethod("getLineNumber");
            Object v = m.invoke(err);
            if (v instanceof Integer) {
                return (Integer) v;
            }
        } catch (Exception ignored) {
            // fall through
        }

        // Fallback: try a "line" field.
        try {
            Field f = err.getClass().getDeclaredField("line");
            f.setAccessible(true);
            return f.getInt(err);
        } catch (Exception ignored) {
            // fall through
        }

        // As a last resort, just say 0.
        return 0;
    }

    public TestErrorListPanel(DefaultErrorSource src) {
        super(new BorderLayout());
        this.source = src;

        String[] columns = { "Type", "Line", "Message" };
        this.model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        this.table = new JTable(model);
        this.table.setName("errorTable");

        this.detailsLabel = new JLabel("");
        this.detailsLabel.setName("detailsLabel");

        JScrollPane scroll = new JScrollPane(table);

        add(scroll, BorderLayout.CENTER);
        add(detailsLabel, BorderLayout.SOUTH);

        // When a row is selected, show a human-readable summary in the label.
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int row = table.getSelectedRow();
            if (row < 0 || row >= backing.length) {
                detailsLabel.setText("");
                return;
            }
            ErrorSource.Error err = backing[row];
            String type = (err.getErrorType() == ErrorSource.ERROR) ? "ERROR" : "WARNING";
            int line1 = extractLine(err) + 1; // convert 0-based to 1-based for display
            String file = err.getFilePath();
            String msg = err.getErrorMessage();
            detailsLabel.setText(type + " @" + file + ":" + line1 + " - " + msg);
        });
    }

    /**
     * Reload all errors from the underlying DefaultErrorSource and redraw
     * the table. This method must be called on the EDT.
     */
    public void refreshFromSource() {
        // DefaultErrorSource.getAllErrors() may return null after clear(),
        // so normalise that to an empty array for deterministic behaviour.
        ErrorSource.Error[] current = (source != null) ? source.getAllErrors() : null;
        if (current == null) {
            current = new ErrorSource.Error[0];
        }
        backing = current;

        model.setRowCount(0);

        for (ErrorSource.Error err : backing) {
            String type = (err.getErrorType() == ErrorSource.ERROR) ? "ERROR" : "WARNING";
            int line1 = extractLine(err) + 1;
            String msg = err.getErrorMessage();
            model.addRow(new Object[] { type, line1, msg });
        }
    }

    public JTable getTable() {
        return table;
    }

    public JLabel getDetailsLabel() {
        return detailsLabel;
    }
}