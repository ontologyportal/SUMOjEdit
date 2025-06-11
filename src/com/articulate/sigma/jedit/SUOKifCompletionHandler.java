package com.articulate.sigma.jedit;

import com.articulate.sigma.Formula;
import java.awt.Point;
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditorStarted;
import org.gjt.sp.jedit.textarea.*;

import javax.swing.*;
import java.awt.event.*;
import java.util.*;

/** A Lightweight SUO-KIF code completion handler for SUMOjEdit
 *
 * @author <a href="mailto:terry.norbraten@gmail.com?subject=com.articulate.sigma.jedit.SUOKifCompletionHandler">Terry Norbraten</a>
 * @author GPT-4
 */
public class SUOKifCompletionHandler extends TextAreaExtension implements EBComponent {

    /** SUO-KIF definitions defined in Formula */
    private static final java.util.List<String> SUO_KIF_KEYWORDS = new ArrayList<>();

    static {
        SUO_KIF_KEYWORDS.addAll(Formula.COMPARISON_OPERATORS);
        SUO_KIF_KEYWORDS.addAll(Formula.DEFN_PREDICATES);
        SUO_KIF_KEYWORDS.addAll(Formula.DOC_PREDICATES);
        SUO_KIF_KEYWORDS.addAll(Formula.LOGICAL_OPERATORS);
        SUO_KIF_KEYWORDS.addAll(Formula.MATH_FUNCTIONS);
    }

    public SUOKifCompletionHandler() {}

    @Override
    public void handleMessage(EBMessage msg) {
        if (msg instanceof EditorStarted) {
            for (View v : jEdit.getViews()) {
                attach(v.getTextArea());
            }
        } else if (msg instanceof BufferUpdate) {
            BufferUpdate bu = (BufferUpdate) msg;
            if (bu.getWhat() == BufferUpdate.LOADED || bu.getWhat() == BufferUpdate.CREATED) {
                View view = jEdit.getActiveView();
                if (view != null) attach(view.getTextArea());
            }
        }
    }

    private void attach(JEditTextArea textArea) {
        textArea.getPainter().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showCompletionPopup(jEdit.getActiveView());
                }
            }
        });
    }

    public void showCompletionPopup(View view) {
        JEditTextArea textArea = view.getTextArea();
        int caret = textArea.getCaretPosition();
        String prefix = getCurrentPrefix(textArea);
        int start = caret - prefix.length();

        JPopupMenu popup = new JPopupMenu();

        SUO_KIF_KEYWORDS.stream()
                .filter(k -> k.startsWith(prefix))
                .forEach(k -> {
                    JMenuItem item = new JMenuItem(k);
                    item.addActionListener(e -> {
                        Buffer buffer = (Buffer) textArea.getBuffer();
                        buffer.beginCompoundEdit();
                        buffer.remove(start, caret - start);
                        buffer.insert(start, k);
                        buffer.endCompoundEdit();
                    });
                    popup.add(item);
                });

        if (popup.getComponentCount() > 0) {
            Point location = textArea.offsetToXY(caret);
            popup.show(textArea.getPainter(), location.x, location.y + 20);
        }
    }

    private String getCurrentPrefix(JEditTextArea textArea) {

        int caret = textArea.getCaretPosition();
        if (caret == 0) {
            return "";
        }

        try {
            String text = textArea.getText(0, caret);
            // Find the last word-like token (alphanumeric or special symbols)
            int i = caret - 1;
            char c;
            while (i >= 0) {
                c = text.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '-' && c != '_') {
                    break;
                }
                i--;
            }
            return text.substring(i + 1).trim();
        } catch (Exception e) {
            return "";
        }
    }

} // end class file SUOKifCompletionHandler.java
