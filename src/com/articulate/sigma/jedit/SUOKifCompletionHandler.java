package com.articulate.sigma.jedit;

import com.articulate.sigma.Formula;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditorStarted;
import org.gjt.sp.jedit.textarea.*;

import java.awt.event.*;
import java.awt.Point;
import java.util.*;
import java.util.stream.Collectors;
import javax.swing.*;

/** A Lightweight SUO-KIF code completion handler for SUMOjEdit
 *
 * @author <a href="mailto:terry.norbraten@gmail.com?subject=com.articulate.sigma.jedit.SUOKifCompletionHandler">Terry Norbraten</a>
 * @author GPT-4
 */
public class SUOKifCompletionHandler extends TextAreaExtension implements EBComponent {

    /** SUO-KIF definitions defined in Formula */
    private static final Map<String, java.util.List<String>> SUO_KIF_KEYWORD_GROUPS = new LinkedHashMap<>();

    /* Populate by groups of operators */
    static {
        SUO_KIF_KEYWORD_GROUPS.put("Logical", Formula.LOGICAL_OPERATORS);
        SUO_KIF_KEYWORD_GROUPS.put("Definition", Formula.DEFN_PREDICATES);
        SUO_KIF_KEYWORD_GROUPS.put("Comparison", Formula.COMPARISON_OPERATORS);
        SUO_KIF_KEYWORD_GROUPS.put("Math", Formula.MATH_FUNCTIONS);
        SUO_KIF_KEYWORD_GROUPS.put("Document", Formula.DOC_PREDICATES);
    }

    /** Default no arg constructor */
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

    /** Inner class to handle either Cntl+Space, or Opt+Tab for bringing up
     * the pop up menu for completion.
     */
    class myKeyListener extends KeyAdapter {

        private View v;

        public myKeyListener(View view) {
            v = view;
        }

        @Override
        public void keyPressed(KeyEvent e) {

            if (v.getBuffer().getPath().contains(".kif")) {

                // Trigger on Ctrl+Space universally (avoid Cmd+Space)
                if (e.getKeyCode() == KeyEvent.VK_SPACE && e.isControlDown()) {
                    showCompletionPopup(v);
                    e.consume(); // prevent tab char from being inserted
                }

                // Optional: Tab trigger if it works (easier for macOS)
                if (e.getKeyCode() == KeyEvent.VK_TAB && !e.isShiftDown()) {
                    showCompletionPopup(v);
                    e.consume(); // prevent tab char from being inserted
                }
            } else {
                super.keyPressed(e); // hand back to the EDT
            }
        }
    }

    /** Assigns a key listener to a KIF view
     *
     * @param textArea the text area of the KIF view
     */
    private void attach(JEditTextArea textArea) {

        for (KeyListener kl : textArea.getKeyListeners())
            if (kl instanceof myKeyListener) return;

        textArea.addKeyListener(new myKeyListener(textArea.getView()));
    }

    /**
     * Generate the KIF operator pop up completion menu
     * @param view the jEdit view to display the pop up menu
     */
    private void showCompletionPopup(View view) {

        JEditTextArea textArea = view.getTextArea();
        int caret = textArea.getCaretPosition();
        String prefix = getCurrentPrefix(textArea);
        int start = caret - prefix.length();

        JPopupMenu popup = new JPopupMenu();

        String groupName;
        List<String> groupKeywords;
        JMenu groupMenu;
        JMenuItem item;
        for (Map.Entry<String, List<String>> entry : SUO_KIF_KEYWORD_GROUPS.entrySet()) {
            groupName = entry.getKey();
            groupKeywords = entry.getValue().stream()
                .filter(k -> k.startsWith(prefix))
                .collect(Collectors.toList());

            if (!groupKeywords.isEmpty()) {
                groupMenu = new JMenu(groupName);
                for (String keyword : groupKeywords) {
                    item = new JMenuItem(keyword);
                    item.addActionListener(e -> {
                        Buffer buffer = (Buffer) textArea.getBuffer();
                        buffer.beginCompoundEdit();
                        buffer.remove(start, caret - start);
                        buffer.insert(start, keyword);
                        buffer.endCompoundEdit();
                    });
                    groupMenu.add(item);
                }
                popup.add(groupMenu);
            }
        }

        if (popup.getComponentCount() > 0) {
            Point location = textArea.offsetToXY(caret);
            popup.show(textArea.getPainter(), location.x, location.y + 20);
        }
    }

    /** Capture a prefix to narrow the completion operator assignment
     *
     * @param textArea the text area of the KIF view
     * @return a prefix to narrow the completion operator assignment
     */
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
