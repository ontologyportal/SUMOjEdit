package com.articulate.sigma.jedit;

/*
 * SUMOjEdit.java
 * part of the SUMOjEdit plugin for the jEdit text editor
 * Copyright (C) 2019 Infosys
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
import com.articulate.sigma.*;
import com.articulate.sigma.parsing.SuokifApp;
import com.articulate.sigma.parsing.SuokifVisitor;
import com.articulate.sigma.tp.*;
import com.articulate.sigma.trans.*;
import com.articulate.sigma.utils.*;

import com.google.common.io.Files;

import errorlist.DefaultErrorSource;
//import errorlist.ErrorListPanel;
import errorlist.ErrorSource;

import java.awt.*;
import java.io.*;
//import javax.swing.Box;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import javax.swing.Action;
import javax.swing.MenuElement;
import javax.swing.text.View;

import org.gjt.sp.jedit.*;
//import org.gjt.sp.jedit.gui.RolloverButton;
import org.gjt.sp.jedit.io.VFSManager;
import org.gjt.sp.jedit.menu.EnhancedMenu;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.util.Log;
import org.gjt.sp.util.ThreadUtilities;

import tptp_parser.*;

/**
 * ***************************************************************
 * A SUO-KIF editor and error checker
 */
public class SUMOjEdit implements EBComponent, SUMOjEditActions {

    // at top-level fields
    private AutoCompleteManager autoComplete;

    public static boolean log = true;

    protected final KIF kif;
    protected KB kb;
    protected FormulaPreprocessor fp;
    // One ErrorSource per jEdit View so error lists persist per window
    private final Map<org.gjt.sp.jedit.View, DefaultErrorSource> viewErrorSources = new WeakHashMap<>();
    protected DefaultErrorSource errsrc;  // currently selected source for the active View

    // Use ErrRec instead (moved from here, line 77-86)
        // A tiny result holder
        // private static final class ErrRec {
            // final int type;                // ErrorSource.ERROR or ErrorSource.WARNING
            // final String file;
            // final int line, start, end;    // jEdit 0-based line
            // final String msg;
            // ErrRec(int type, String file, int line, int start, int end, String msg) {
                // this.type = type; this.file = file; this.line = line; this.start = start; this.end = end; this.msg = msg;
            // }
        // }

        // --- Debounced, single-EDT-batch error adder to avoid CME from ErrorList ---
        private final java.util.List<ErrRec> _pendingErrs = new java.util.ArrayList<>();
        private volatile boolean _flushScheduled = false;

        // === Error message snippet helpers (limit 100 chars) ===
        private static final int SNIPPET_MAX = 100;

        private static String truncateWithEllipsis(String s, int max) {
            if (s == null) return "";
            s = s.strip();
            if (s.length() <= max) return s;
            return s.substring(0, max) + "…";
        }

        /** Read a specific 0-based line from disk safely (non-EDT, no jEdit deps). */
        private static String safeSnippetFromFile(String filePath, int zeroBasedLine) {
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(
                        java.nio.file.Paths.get(filePath));
                if (zeroBasedLine >= 0 && zeroBasedLine < lines.size()) {
                    return truncateWithEllipsis(lines.get(zeroBasedLine), SNIPPET_MAX);
                }
            } catch (Throwable ignore) {}
            return "";
        }

        /** Fetch line text from the active buffer (EDT only). Falls back to disk if needed. */
        private String snippetFromActiveBufferOrFile(String filePath, int zeroBasedLine) {
            try {
                if (view != null && view.getBuffer() != null) {
                    String current = view.getBuffer().getPath();
                    if (current != null && current.equals(filePath)) {
                        int lc = view.getBuffer().getLineCount();
                        if (zeroBasedLine >= 0 && zeroBasedLine < lc) {
                            int start = view.getBuffer().getLineStartOffset(zeroBasedLine);
                            int end = view.getBuffer().getLineEndOffset(zeroBasedLine);
                            String text = view.getBuffer().getText(start, end - start);
                            return truncateWithEllipsis(text.stripTrailing(), SNIPPET_MAX);
                        }
                    }
                }
            } catch (Throwable ignore) {}
            return safeSnippetFromFile(filePath, zeroBasedLine);
        }

        /** Append formatted snippet to a base message. */
        private String appendSnippet(String baseMsg, String filePath, int zeroBasedLine) {
            String snip = snippetFromActiveBufferOrFile(filePath, zeroBasedLine);
            if (snip.isEmpty()) return baseMsg;
            return baseMsg + " — " + snip;
        }

        private void addErrorsBatch(java.util.List<ErrRec> batch) {
            if (batch == null || batch.isEmpty()) return;

            synchronized (_pendingErrs) {
                _pendingErrs.addAll(batch);
                if (_flushScheduled) return;
                _flushScheduled = true;
            }

            // Coalesce to ONE runnable on the EDT
            ThreadUtilities.runInDispatchThread(() -> {
                java.util.List<ErrRec> toAdd;
                synchronized (_pendingErrs) {
                    toAdd = new java.util.ArrayList<>(_pendingErrs);
                    _pendingErrs.clear();
                    _flushScheduled = false;
                }

                // Pause ErrorList notifications while we bulk-add
                errorlist.ErrorSource.unregisterErrorSource(errsrc);
                try {
                    for (ErrRec e : toAdd) {
                        String msgWithSnippet = appendSnippet(e.msg, e.file, e.line);
                        errsrc.addError(e.type, e.file, e.line, e.start, e.end, msgWithSnippet);
                    }
                } finally {
                    // Re-enable notifications once, after all errors are in
                    errorlist.ErrorSource.registerErrorSource(errsrc);
                }
            });
        }

    // These DefaultErrors are not currently used, but good framework to have
    // in case of extra messages
//    private DefaultErrorSource.DefaultError de;
//    private DefaultErrorSource.DefaultError dw;

    private org.gjt.sp.jedit.View view;

    private long pluginStart;

    private boolean isInitialized;

    // add below other fields
    private static volatile java.util.concurrent.ThreadPoolExecutor CHECKER_POOL = new java.util.concurrent.ThreadPoolExecutor(
        getCheckerThreads(),                          // corePoolSize
        getCheckerThreads(),                          // maximumPoolSize
        getKeepAliveSeconds(), java.util.concurrent.TimeUnit.SECONDS,
        new java.util.concurrent.LinkedBlockingQueue<>(),
        r -> {
            Thread t = new Thread(r, "sje-checker");
            t.setDaemon(true);
            return t;
        }
    );

    private static int getCheckerThreads() {
        // jEdit property takes precedence, else system property, else default
        try {
            String prop = org.gjt.sp.jedit.jEdit.getProperty("sumojedit.checker.threads");
            if (prop == null || prop.isBlank()) prop = System.getProperty("sumojedit.checker.threads", "");
            int v = prop.isBlank() ? 0 : Integer.parseInt(prop.trim());
            if (v <= 0) v = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
            return v;
        } catch (NumberFormatException t) {
            return Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        }
    }

    private static int getKeepAliveSeconds() {
        try {
            String prop = org.gjt.sp.jedit.jEdit.getProperty("sumojedit.checker.keepAliveSec");
            if (prop == null || prop.isBlank()) prop = System.getProperty("sumojedit.checker.keepAliveSec", "30");
            return Math.max(1, Integer.parseInt(prop.trim()));
        } catch (NumberFormatException t) {
            return 30;
        }
    }

    /** ***************************************************************
     * Create a non-EDT background Runnable with an overridden toString for
     * label display
     *
     * @param runnable the supplied Runnable to run
     * @param toStringSupplier to provide a toString override label
     * @return a Runnable with an overridden toString
     */
    public static Runnable create(Runnable runnable, Supplier<String> toStringSupplier) {
        return new Runnable() {

            @Override
            public void run() {
                runnable.run();
            }

            @Override
            public String toString() {
                return toStringSupplier.get();
            }
        };
    }

    /** ***************************************************************
     * Default constructor
     */
    public SUMOjEdit() {

        // We want all STD out and err messages to go to the console, easier to read and observe
        Log.init(false, 1);
        Log.log(Log.MESSAGE, SUMOjEdit.this, ": SUMOKBtoTPTPKB.rapidParsing==" + SUMOKBtoTPTPKB.rapidParsing);
        Log.log(Log.MESSAGE, SUMOjEdit.this, ": initializing");

        kif = new KIF();
        kif.filename = "";
        isInitialized = false;
    }

    /** ***************************************************************
     * @return the plugin version with build number
     */
    public static String getVersionWithBuild() {
        String version = jEdit.getProperty("plugin.com.articulate.sigma.jedit.SUMOjEditPlugin.version", "1.1.0");
        String buildNum = jEdit.getProperty("build.number", "0");
        return String.format("SUMOjEdit v%s (Build %s)", version, buildNum);
    }

    /** ***************************************************************
     * Starts the given non-EDT Runnable in the background
     *
     * @param r the Runnable to start
     */
    public void startBackgroundThread(Runnable r) {

        ThreadUtilities.runInBackground(r);
    }

    /** ***************************************************************
     * Starts the KB initialization process for UI use only. Must only be
     * called when jEdit will be an active UI. Not meant for use by the main()
     */
    @SuppressWarnings("SleepWhileInLoop")
    public void init() {

        Runnable r = () -> {

            pluginStart = System.currentTimeMillis();

            // wait for the view to become active
            do
                try {
                    view = jEdit.getActiveView();
                    Thread.sleep(50L);
                } catch (InterruptedException ex) {System.err.println(ex);}
            while (view == null);

            // Display build number in status bar
            ThreadUtilities.runInDispatchThread(() -> {
                view.getStatus().setMessage(BuildInfo.getFullVersion() + " loading...");
            });

            // Set single-threaded mode for jEdit to prevent arity check deadlock
            System.out.println("SUMOjEdit.init(): Setting single-threaded mode for jEdit");
            System.out.println("SUMOjEdit.init(): " + BuildInfo.getFullVersion());
            System.setProperty("sigma.exec.mode", "jedit-single");

            // Set persistent status message about version
            if (view != null) {
                // This will show in the Plugin Manager and About dialogs
                jEdit.setProperty("plugin.com.articulate.sigma.jedit.SUMOjEditPlugin.longdescription",
                    "A syntax aware editor for the Suggested Upper Merged Ontology (SUMO)\n" +
                    BuildInfo.getFullVersion() + "\n" +
                    "Knowledge Base loaded with " + (kb != null ? kb.terms.size() : 0) + " terms");
            }

            // Refresh the ExecutorService to respect the single-threaded constraint
            KButilities.refreshExecutorService();

            // toggle the menu so that we can view the plugin dropdown
            ThreadUtilities.runInDispatchThread(() -> {
                view.getJMenuBar().getSubElements()[8].menuSelectionChanged(true);
            });
            togglePluginMenus(false);
            ThreadUtilities.runInDispatchThread(() -> {
                view.getJMenuBar().getSubElements()[8].menuSelectionChanged(false);
            });

            try {
                System.out.println("SUMOjEdit.init(): Initializing KB with single-threaded executor");
                SUMOtoTFAform.initOnce();
                kb = SUMOtoTFAform.kb;
                fp = SUMOtoTFAform.fp;
                System.out.println("SUMOjEdit.init(): KB initialization successful");
            } catch (Exception e) {
                Log.log(Log.ERROR, this, ":init(): KB init error: ", e);
                // Continue anyway
                if (SUMOtoTFAform.kb != null) {
                    kb = SUMOtoTFAform.kb;
                    fp = SUMOtoTFAform.fp;
                }
            }
            Log.log(Log.MESSAGE, this, ":kb: " + kb);

            /* === Switch back to parallel mode after KB init === */
            System.setProperty("sigma.exec.mode", "parallel");
            KButilities.refreshExecutorService();

            togglePluginMenus(true);

            // Force AC initialization even if KB has issues
            if (view != null) {
                if (kb != null) {
                    autoComplete = new AutoCompleteManager(view, kb);
                    Log.log(Log.MESSAGE, this, ":AutoComplete initialized with KB");
                } else {
                    // Try to get KB from KBmanager as fallback
                    KB fallbackKB = KBmanager.getMgr().getKB("SUMO");
                    if (fallbackKB != null) {
                        kb = fallbackKB;
                        autoComplete = new AutoCompleteManager(view, kb);
                        Log.log(Log.WARNING, this, ":AutoComplete initialized with fallback KB");
                    } else {
                        Log.log(Log.ERROR, this, ":No KB available for AutoComplete");
                    }
                }
            }

            if (view != null && kb != null) {
                autoComplete = new AutoCompleteManager(view, kb);
                Log.log(Log.MESSAGE, this, ":AutoComplete initialized successfully with " + kb.terms.size() + " terms");
            } else {
                Log.log(Log.ERROR, this, ":AutoComplete NOT initialized - view=" + view + ", kb=" + kb);
                if (kb == null) {
                    Log.log(Log.ERROR, this, ":KB is null! AutoComplete will not work.");
                }
            }

            isInitialized = true;
            errsrc = ensureErrorSource(view);   // bind/select the source for this window
            processLoadedKifOrTptp();

            // Update status bar with build info after initialization
            ThreadUtilities.runInDispatchThread(() -> {
                view.getStatus().setMessageAndClear(BuildInfo.getFullVersion() + " ready");
            });
        };
        Runnable rs = create(r, () -> "Initializing " + getClass().getName());
        startBackgroundThread(rs);
    }

    /** ***************************************************************
     * Handles the UI while a KIF or TPTP file is being processed
     */
    private void processLoadedKifOrTptp() {

        if (!isInitialized)
            return;

        Runnable r = () -> {
            boolean isKif = Files.getFileExtension(view.getBuffer().getPath()).equalsIgnoreCase("kif");

            // Treat these as “TPTP-ish” so menus stay enabled
            final String ext = Files.getFileExtension(view.getBuffer().getPath()).toLowerCase();
            final java.util.Set<String> tptpExts = new java.util.HashSet<>(
                    java.util.Arrays.asList("tptp","p","fof","cnf","tff","thf"));
            boolean isTptpLike = tptpExts.contains(ext);

            if (isKif || isTptpLike) {
                togglePluginMenus(true);
                if (isKif) {
                    kif.filename = view.getBuffer().getPath();
                    if (kb != null && !kb.constituents.contains(kif.filename)
                            && new File(kif.filename).length() > 1L) {
                        togglePluginMenus(false);
                        Color clr = view.getStatus().getBackground();
                        ThreadUtilities.runInDispatchThread(() -> {
                            view.getStatus().setBackground(Color.GREEN);
                            view.getStatus().setMessage("processing " + kif.filename);
                        });
                        tellTheKbAboutLoadedKif();
                        checkErrors();
                        ThreadUtilities.runInDispatchThread(() -> {
                            view.getStatus().setBackground(clr);
                            view.getStatus().setMessageAndClear("processing " + kif.filename + " complete");
                        });
                        togglePluginMenus(true);
                    }
                }
                ErrorSource.registerErrorSource(errsrc);
            } else {
                togglePluginMenus(false);
                if (errsrc != null) unload();
            }
            Log.log(Log.MESSAGE, this, ":processLoadedKifOrTptp(): complete");
            if (pluginStart > 0) {
                Log.log(Log.MESSAGE, this, ":initial startup completed in " +
                        (System.currentTimeMillis() - pluginStart) / KButilities.ONE_K + " secs");
                pluginStart = 0L;
            }
        };
        Runnable rs = create(r, () -> "Processing KIF/TPTP file");
        startBackgroundThread(rs);
    }

    /** ***************************************************************
     * Disables the SUMOjEdit plugin menu items during processing of KIF
     * or TPTP. Re-enables post processing.
     *
     * @param enabled if true, enable the plugin menus, if false, disable plugin menus
     */
    private void togglePluginMenus(boolean enabled) {

        ThreadUtilities.runInDispatchThread(() -> {

            // Top view menu bar / Enhanced menu item / Plugins menu / SUMOjEdit plugin menu
            MenuElement[] elems = view.getJMenuBar().getSubElements()[8].getSubElements()[0].getSubElements();
            for (MenuElement elem : elems) {
                if (elem instanceof EnhancedMenu) {
                    if (((EnhancedMenu) elem).getText().toLowerCase().equals(SUMOjEditPlugin.NAME)) {
                        elem.getComponent().setEnabled(enabled);
                        break;
                    }
                }
            }

            // Now, the right click context menu of the editor's text area in the case of customized SUMOjEdit actions
            view.getEditPane().getTextArea().setRightClickPopupEnabled(enabled);
        });
    }

    /** ***************************************************************
     * Adds a loaded KIF as a constituent to the KB so that all terms
     * in the current jEdit buffer can be recognized. If constituent previously
     * loaded, will just return.
     */
    private void tellTheKbAboutLoadedKif() {

        if (!kb.constituents.contains(kif.filename)) {
            long start = System.currentTimeMillis();
            kb.constituents.add(kif.filename);
            kb.reload();
            kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));
            Log.log(Log.MESSAGE, this, ":tellTheKbAboutLoadedKif() completed in " + (System.currentTimeMillis() - start) / KButilities.ONE_K + " secs");
        }
    }

    /* Props at: https://www.jedit.org/api/org/gjt/sp/jedit/msg/package-summary.html */
    @Override
    public void handleMessage(EBMessage msg) { // this occurs on the EDT

        if (msg instanceof BufferUpdate)
            bufferUpdate((BufferUpdate)msg);
//        if (msg instanceof EditorExiting)
//            editorExiting((EditorExiting)msg);
        if (msg instanceof EditPaneUpdate)
            editPaneUpdate((EditPaneUpdate)msg);
        //        if (msg instanceof VFSUpdate)
        //            vfsUpdate((VFSUpdate)msg);
        if (msg instanceof ViewUpdate)
            viewUpdate((ViewUpdate)msg);
    }

    /** ***************************************************************
     * Handler for BufferUpdate CLOSED and LOADED events
     */
    private void bufferUpdate(BufferUpdate bu) {

        if (view == null) return;
        if (bu.getView() == view) {
//            if (bu.getWhat() == BufferUpdate.CLOSED)
//                System.out.println("BufferUpdate.CLOSED");
//            if (bu.getWhat() == BufferUpdate.CREATED)
//                System.out.println("BufferUpdate.CREATED");
//            if (bu.getWhat() == BufferUpdate.DIRTY_CHANGED)
//                System.out.println("BufferUpdate.DIRTY_CHANGED");
//            if (bu.getWhat() == BufferUpdate.LOADED)
//                System.out.println("BufferUpdate.LOADED");
//            if (bu.getWhat() == BufferUpdate.MARKERS_CHANGED)
//                System.out.println("BufferUpdate.MARKERS_CHANGED");
//            if (bu.getWhat() == BufferUpdate.PROPERTIES_CHANGED)
//                System.out.println("BufferUpdate.PROPERTIES_CHANGED");
            if (bu.getWhat() == BufferUpdate.SAVED) { // file saved
//                System.out.println("BufferUpdate.SAVED");
                processLoadedKifOrTptp();
            }
        }
    }

    /** ***************************************************************
     */
//    private void editorExiting(EditorExiting ee) {
//
//        System.out.println(getClass().getName() + " exiting");
//    }

    /** ***************************************************************
     */
    private void editPaneUpdate(EditPaneUpdate eu) {

        if (view == null) return;
//        if (eu.getWhat() == EditPaneUpdate.BUFFERSET_CHANGED)
//            System.out.println("EditPaneUpdate.BUFFERSET_CHANGED");
        if (eu.getWhat() == EditPaneUpdate.BUFFER_CHANGED) { // switching between files or panes and closing panes
//            System.out.println("EditPaneUpdate.BUFFER_CHANGED");
            processLoadedKifOrTptp();
        }
        if (autoComplete != null) autoComplete.refreshIndexOnBufferChange();
//        if (eu.getWhat() == EditPaneUpdate.CREATED)
//            System.out.println("EditPaneUpdate.CREATED");
        if (eu.getWhat() == EditPaneUpdate.DESTROYED) { // jEdit exit
//            System.out.println("EditPaneUpdate.DESTROYED");
            unload();
        }
    }

    /** ***************************************************************
     * Clean up resources when not needed
     */
    private void unload() {

        clearWarnAndErr();
        ErrorSource.unregisterErrorSource(errsrc);
        if (autoComplete != null) autoComplete.dispose();
    }

    /** ***************************************************************
     */
//    private void vfsUpdate(VFSUpdate vu) {
//
//        System.out.println("VFS update"); // file saved
//    }

    /** Select per-View ErrorSource on ACTIVATE; clean up only when a View is CLOSED. */
    private void viewUpdate(ViewUpdate vu) {
        if (vu == null) return;

        if (vu.getWhat() == ViewUpdate.ACTIVATED) {
            final org.gjt.sp.jedit.View newView = vu.getView();
            if (newView != null) {
                this.view = newView;
                // Select (do not recreate/unregister others)
                this.errsrc = ensureErrorSource(newView);
            }
        } else if (vu.getWhat() == ViewUpdate.CLOSED) {
            final org.gjt.sp.jedit.View closed = vu.getView();
            if (closed != null) {
                final DefaultErrorSource es = viewErrorSources.remove(closed);
                if (es != null) {
                    try { errorlist.ErrorSource.unregisterErrorSource(es); } catch (Throwable ignore) {}
                }
                if (this.view == closed) {
                    this.view = jEdit.getActiveView();
                    if (this.view != null) this.errsrc = ensureErrorSource(this.view);
                    else this.errsrc = null;
                }
            }
        }
    }


    /** Ensure the specified View has a registered ErrorSource and select it without touching others. */
    private void switchErrorSourceTo(final org.gjt.sp.jedit.View v) {
        this.errsrc = ensureErrorSource(v);
    }

    /** Get or create an ErrorSource for a View. Never unregister others here. */
    private DefaultErrorSource ensureErrorSource(final org.gjt.sp.jedit.View v) {
        DefaultErrorSource es = viewErrorSources.get(v);
        if (es == null) {
            // unique name per View to avoid collisions in ErrorList
            final String sourceName = getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(v));
            es = new DefaultErrorSource(sourceName, v);
            errorlist.ErrorSource.registerErrorSource(es);
            viewErrorSources.put(v, es);
        }
        return es;
    }

    @Override
    public void setFOF() {

        System.out.println("setFOF(): translation set to TPTP");
        Log.log(Log.MESSAGE, this, ": translation set to TPTP");
        SUMOformulaToTPTPformula.lang = "fof";
        SUMOKBtoTPTPKB.lang = "fof";
    }

    @Override
    public void setTFF() {

        System.out.println("setTFF(): translation set to TFF");
        Log.log(Log.MESSAGE, this, ": translation set to TFF");
        SUMOformulaToTPTPformula.lang = "tff";
        SUMOKBtoTPTPKB.lang = "tff";
    }

    @Override
    public void chooseVamp() {

        System.out.println("chooseVamp(): prover set to Vampire");
        Log.log(Log.MESSAGE, this, ":chooseVamp(): prover set to Vampire");
        KBmanager.getMgr().prover = KBmanager.Prover.VAMPIRE;
    }

    @Override
    public void chooseE() {

        System.out.println("chooseE(): prover set to E");
        Log.log(Log.MESSAGE, this, ":chooseE(): prover set to E");
        KBmanager.getMgr().prover = KBmanager.Prover.EPROVER;
    }

    /** ***************************************************************
     */
    private String queryResultString(TPTP3ProofProcessor tpp) {

        System.out.println("queryExp(): bindings: " + tpp.bindings);
        Log.log(Log.MESSAGE, this, ":queryExp(): bindings: " + tpp.bindings);
        System.out.println("queryExp(): bindingMap: " + tpp.bindingMap);
        Log.log(Log.MESSAGE, this, ":queryExp(): bindingMap: " + tpp.bindingMap);
        System.out.println("queryExp(): proof: " + tpp.proof);
        Log.log(Log.MESSAGE, this, ":queryExp(): proof: " + tpp.proof);
        java.util.List<String> proofStepsStr = new ArrayList<>();
        for (TPTPFormula ps : tpp.proof)
            proofStepsStr.add(ps.toString());
        ThreadUtilities.runInDispatchThread(() -> {
            jEdit.newFile(view);
        });
        StringBuilder result = new StringBuilder();
        if (tpp.bindingMap != null && !tpp.bindingMap.isEmpty())
            result.append("Bindings: ").append(tpp.bindingMap);
        else if (tpp.bindings != null && !tpp.bindings.isEmpty())
            result.append("Bindings: ").append(tpp.bindings);
        if (tpp.proof == null || tpp.proof.isEmpty()) {
            result.append(tpp.status);
        } else {
            if (tpp.containsFalse)
                result.append("\n\n").append(StringUtil.arrayListToCRLFString(proofStepsStr));
            else
                result.append(tpp.status);
        }
        return result.toString();
    }

    @Override
    public void queryExp() {

        String contents = view.getTextArea().getSelectedText();
        if (!checkEditorContents(contents, "Please fully highlight an atom for query"))
            return;
        Runnable r = () -> {
            togglePluginMenus(false);
            Log.log(Log.MESSAGE, this, ":queryExp(): query with: " + contents);
            System.out.println("queryExp(): query with: " + contents);
            String dir = KBmanager.getMgr().getPref("kbDir") + File.separator;
            String type = "tptp";
            String outfile = dir + "temp-comb." + type;
            System.out.println("queryExp(): query on file: " + outfile);
            Log.log(Log.MESSAGE, this, ":queryExp(): query on file: " + outfile);
            Vampire vamp;
            EProver eprover;
            StringBuilder qlist = null;
            TPTP3ProofProcessor tpp = new TPTP3ProofProcessor();
            if (KBmanager.getMgr().prover == KBmanager.Prover.VAMPIRE) {
                vamp = kb.askVampire(contents, 30, 1);
                tpp.parseProofOutput(vamp.output, contents, kb, vamp.qlist);
                qlist = vamp.qlist;
                //Log.log(Log.MESSAGE,this,"queryExp(): completed query with result: " + StringUtil.arrayListToCRLFString(vamp.output));
            }
            if (KBmanager.getMgr().prover == KBmanager.Prover.EPROVER) {
                eprover = kb.askEProver(contents, 30, 1);
                try {
                    //Log.log(Log.MESSAGE,this,"queryExp(): completed query with result: " + StringUtil.arrayListToCRLFString(eprover.output));
                    tpp.parseProofOutput(eprover.output, contents, kb, eprover.qlist);
                    qlist = eprover.qlist;
                } catch (Exception e) {
                    Log.log(Log.ERROR, this, ":queryExp(): ", e);
                }
            }
            tpp.processAnswersFromProof(qlist, contents);

            ThreadUtilities.runInDispatchThread(() -> {
                view.getTextArea().setText(queryResultString(tpp));
            });
            Log.log(Log.MESSAGE, this, ":queryExp(): complete");
        };
        Runnable rs = create(r, () -> "Querying expression");
        startBackgroundThread(rs);
    }

    @Override
    public void browseTerm() {

        clearWarnAndErr();
        String contents = view.getTextArea().getSelectedText();
        if (!checkEditorContents(contents, "Please fully highlight a term to browse"))
            return;

        if (!StringUtil.emptyString(contents) && Formula.atom(contents)
                && kb.terms.contains(contents)) {
            String urlString = "http://sigma.ontologyportal.org:8443/sigma/Browse.jsp?kb=SUMO&lang=EnglishLanguage&flang=SUO-KIF&term="
                    + contents;
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(java.net.URI.create(urlString));
                } catch (IOException e) {
                    Log.log(Log.ERROR, this, ":browseTerm(): ", e);
                }
            }
        }
    }

    /** ***************************************************************
     * @return the line number of where the error/warning begins
     */
    private int getLineNum(String line) {

        int result = -1;

        /* SigmaAntlr error output */
        Pattern p = Pattern.compile("(\\d+):");
        Matcher m = p.matcher(line);
        if (m.find()) {
//                Log.log(Log.MESSAGE, this, ":getLineNum(): found line number: " + m.group(1));
            try {
                result = Integer.parseInt(m.group(1));
            } catch (NumberFormatException nfe) {}
        }
        /* End SigmaAntlr error output */
        /* Kif parse error output */
        if (result < 0) {
            p = Pattern.compile("line(:?) (\\d+)");
            m = p.matcher(line);
            if (m.find()) {
//            Log.log(Log.MESSAGE, this, ":getLineNum(): found line number: " + m.group(2));
                try {
                    result = Integer.parseInt(m.group(2));
                } catch (NumberFormatException nfe) {}
            }
        }
        if (result < 0 ) {
            p = Pattern.compile("line&#58; (\\d+)");
            m = p.matcher(line);
            if (m.find()) {
//                Log.log(Log.MESSAGE, this, ":getLineNum(): found line number: " + m.group(1));
                try {
                    result = Integer.parseInt(m.group(1));
                } catch (NumberFormatException nfe) {}
            }
        }
        /* End Kif parse error output */
        if (result < 0)
            result = 0;
        return result;
    }

    /** ***************************************************************
     * sigmaAntlr generates line offsets
     * @return the line offset of where the error/warning begins
     */
    private int getOffset(String line) {

        int result = -1;
        Pattern p = Pattern.compile("\\:(\\d+)\\:");
        Matcher m = p.matcher(line);
        if (m.find()) {
//            Log.log(Log.MESSAGE, this, ":getOffset(): found offset number: " + m.group(1));
            try {
                result = Integer.parseInt(m.group(1));
            } catch (NumberFormatException nfe) {}
        }
        if (result < 0)
            result = 0;
        return result;
    }

    /** ***************************************************************
     * Utility class that contains searched term line and filepath information
     */
    public class FileSpec {

        public String filepath = "";
        public int line = -1;
    }

    /** ***************************************************************
     * @return a FileSpec with searched term info
     */
    private FileSpec filespecFromForms(java.util.List<Formula> forms, String currentFName) {

        FileSpec fs = new FileSpec();
        for (Formula f : forms) {
            if (FileUtil.noPath(f.getSourceFile()).equals(currentFName) && !f.getSourceFile().endsWith("_Cache.kif")) {
                fs.filepath = f.sourceFile;
                fs.line = f.startLine - 1; // jedit starts from 0, SUMO starts from 1
                return fs;
            }
        }
        for (Formula f : forms) {
            if (!f.getSourceFile().endsWith("_Cache.kif")) {
                fs.filepath = f.sourceFile;
                fs.line = f.startLine - 1; // jedit starts from 0, SUMO starts from 1
                return fs;
            }
        }
        return fs;
    }

    /** ***************************************************************
     * Note that the "definition" of a term is collection of axioms so look for,
     * in order: instance, subclass, subAttribute, subrelation, domain, documentation
     * @param term the term to search for
     * @return a FileSpec with searched term info
     */
    private FileSpec findDefn(String term) {

        if (StringUtil.emptyString(kif.filename))
            kif.filename = view.getBuffer().getPath();

        String currentFName = FileUtil.noPath(kif.filename);
        java.util.List<Formula> forms = kb.askWithRestriction(0, "instance", 1, term);
        if (forms != null && !forms.isEmpty())
            return(filespecFromForms(forms, currentFName));
        forms = kb.askWithRestriction(0, "subclass", 1, term);
        if (forms != null && !forms.isEmpty())
            return(filespecFromForms(forms, currentFName));
        forms = kb.askWithRestriction(0, "subAttribute", 1, term);
        if (forms != null && !forms.isEmpty())
            return(filespecFromForms(forms, currentFName));
        forms = kb.askWithRestriction(0, "subrelation", 1, term);
        if (forms != null && !forms.isEmpty())
            return(filespecFromForms(forms, currentFName));
        forms = kb.askWithRestriction(0, "domain", 1, term);
        if (forms != null && !forms.isEmpty())
            return(filespecFromForms(forms, currentFName));
        forms = kb.askWithRestriction(0, "documentation", 1, term);
        if (forms != null && !forms.isEmpty())
            return(filespecFromForms(forms, currentFName));
        return null; // nothing found
    }

    /** ***************************************************************
     * Warn the user about fully highlighting a term or formula
     *
     * @param contents the editor contents to check
     * @param msg the warning message to convey
     * @return indication of check pass/fail
     */
    private boolean checkEditorContents(String contents, String msg) {

        boolean retVal = true;
        if (contents == null || contents.isBlank() || contents.length() < 2) {
            java.util.List<ErrRec> _chk = new java.util.ArrayList<>();
            _chk.add(new ErrRec(ErrorSource.WARNING, kif.filename, 1, 0, 0, msg));
            addErrorsBatch(_chk);
            if (log) Log.log(Log.WARNING, this, "checkEditorContents(): " + msg);
            retVal = false;
        }
        return retVal;
    }

    @Override
    public void gotoDefn() {

        clearWarnAndErr();
        String contents = view.getTextArea().getSelectedText();
        if (!checkEditorContents(contents, "Please fully highlight a term for definition"))
            return;

        if (StringUtil.emptyString(kif.filename))
            kif.filename = view.getBuffer().getPath();

        String currentFName = FileUtil.noPath(kif.filename);
        if (!StringUtil.emptyString(contents) && Formula.atom(contents)
                && kb.terms.contains(contents)) {
            FileSpec result = findDefn(contents);
            Log.log(Log.MESSAGE, this, ":gotoDefn(): file:"
                    + result.filepath + "\nline: " + (result.line+1));

            try {
                if (!FileUtil.noPath(result.filepath).equals(currentFName)) {
                    jEdit.openFile(view, result.filepath);
                    VFSManager.waitForRequests(); // <- Critical call to allow for complete Buffer loading!
                    int offset = view.getBuffer().getLineStartOffset(result.line);
                    view.getTextArea().moveCaretPosition(offset);
                }
            } catch (Exception e) {
                Log.log(Log.ERROR, this, "gotoDefn()", e);
            }
        }
        else
            Log.log(Log.WARNING, this, "gotoDefn() term: '" + contents + "' not in the KB");
    }

    /** ***************************************************************
     * Performs the actual formula formatting
     *
     * @param contents the content (formula) to format
     */
    private String formatSelectBody(String contents) {

        if (!checkEditorContents(contents, "Please highlight a formula, or CNTL+A"))
            return null;
        if (!parseKif(contents))
            return null;
        if (StringUtil.emptyString(kif.filename))
            kif.filename = view.getBuffer().getPath();

        StringBuilder result = new StringBuilder();
        for (Formula f : kif.formulasOrdered.values())
            result.append(f);
        return result.toString();
    }

    @Override
    public void formatSelect() {

        clearWarnAndErr();

        // Use jEdit's Buffer explicitly to avoid java.nio.Buffer collision
        final org.gjt.sp.jedit.Buffer buf = view.getBuffer();
        final String filePath = (buf != null ? buf.getPath() : null);

        // For THF/TFF/FOF/CNF/P/TPTP, use the tptp4x-based formatter
        if (isTptpFile(filePath)) {
            tptpFormatBuffer();
            return;
        }

        // KIF path unchanged
        String contents = view.getTextArea().getSelectedText();
        String result = formatSelectBody(contents);
        if (!StringUtil.emptyString(result))
            view.getTextArea().setSelectedText(result);
    }

    // NEW: Route whole-buffer formatting to tptp4X for TPTP-like files
    public void formatBuffer() {

        clearWarnAndErr();

        final org.gjt.sp.jedit.Buffer buf = view.getBuffer();
        final String filePath = (buf != null ? buf.getPath() : null);

        // THF/TFF/FOF/CNF/P/TPTP -> external tptp4X pretty-printer
        if (isTptpFile(filePath)) {
            tptpFormatBuffer();
            return;
        }

        // KIF path unchanged (legacy path)
        String contents = view.getTextArea().getText();
        String result = formatSelectBody(contents);
        if (!StringUtil.emptyString(result))
            view.getTextArea().setText(result);
    }

    /** ***************************************************************
     * Pass any KIF parse warnings and/or errors to the ErrorList Plugin. Also
     * any general warnings or errors (future capability)
     */
    private void logKifWarnAndErr() {

        List<DefaultErrorSource.DefaultError> warnings = new ArrayList<>();
        List<DefaultErrorSource.DefaultError> errors = new ArrayList<>();

        int line, offset;
        for (String warn : kif.warningSet) {
            line = getLineNum(warn);
            offset = getOffset(warn);
            if (offset == 0) offset = 1;
            int adjLine = (line == 0 ? line : line - 1);
            String snip = safeSnippetFromFile(kif.filename, adjLine);
            String msgWithSnippet = snip.isEmpty() ? warn : (warn + " — " + snip);
            DefaultErrorSource.DefaultError warning = new DefaultErrorSource.DefaultError(
                errsrc, ErrorSource.WARNING, kif.filename,
                adjLine, offset, offset+1, msgWithSnippet);
            warnings.add(warning);
        }

        for (String err : kif.errorSet) {
            line = getLineNum(err);
            offset = getOffset(err);
            if (offset == 0) offset = 1;
            int adjLine = (line == 0 ? line : line - 1);
            String snip = safeSnippetFromFile(kif.filename, adjLine);
            String msgWithSnippet = snip.isEmpty() ? err : (err + " — " + snip);
            DefaultErrorSource.DefaultError error = new DefaultErrorSource.DefaultError(
                errsrc, ErrorSource.ERROR, kif.filename,
                adjLine, offset, offset+1, msgWithSnippet);
            errors.add(error);
        }

        // Add all warnings and errors on EDT (single EditBus pulse)
        ThreadUtilities.runInDispatchThread(() -> {
            errorlist.ErrorSource.unregisterErrorSource(errsrc);
            try {
                for (DefaultErrorSource.DefaultError warning : warnings) {
                    errsrc.addError(warning);
                }
                for (DefaultErrorSource.DefaultError error : errors) {
                    errsrc.addError(error);
                }
//                if (dw != null && (!dw.getErrorMessage().isBlank() || dw.getExtraMessages().length > 0))
//                    errsrc.addError(dw);
//                if (de != null && (!de.getErrorMessage().isBlank() || de.getExtraMessages().length > 0))
//                    errsrc.addError(de);
            } finally {
                errorlist.ErrorSource.registerErrorSource(errsrc);
            }
        });
    }

    /** ***************************************************************
     * Clears the KIF instance collections to include warnings and errors
     */
    private void clearKif() {

        kif.warningSet.clear();
        kif.errorSet.clear();
        kif.filename = "";
        kif.formulaMap.clear();
        kif.formulas.clear();
        kif.formulasOrdered.clear();
        kif.termFrequency.clear();
        kif.terms.clear();
    }

    /** ***************************************************************
     * Clears out all warnings and errors in both the ErrorList and
     * SigmaKEE trees.
     */
    private void clearWarnAndErr() {

        ThreadUtilities.runInBackground(() -> {
            
            // Clear the error source
            if (!StringUtil.emptyString(kif.filename)) {
                errsrc.removeFileErrors(kif.filename);
            }

            // Clear the ErrorList UI - no EDT wrapping needed for actions
            jEdit.getAction("error-list-clear").invoke(view);
            errsrc.clear();

            // Clear all KIF collections
            clearKif();

            // Clear all error collections from various components
            KButilities.clearErrors();
            if (kb != null) {
                kb.errors.clear();
                kb.warnings.clear();
            }
            FormulaPreprocessor.errors.clear();
            SUMOtoTFAform.errors.clear();
        });
    }

        // Clear KIF and KB errors (these can be done on any thread)
        // clearKif();
        // KButilities.clearErrors();
        // kb.errors.clear();
        // kb.warnings.clear();
        // FormulaPreprocessor.errors.clear();
        // SUMOtoTFAform.errors.clear();

        // Components of the ErrorListPanel: a Box on top of a JScrollPane.
        // We want the Box
//        Container c = view.getDockableWindowManager().getDockable("error-list");
//
//        // Toggles the ErrorList sweep (clean) button to clear errors from the UI
//        if (c != null) { // can happen if ErrorList is not visible (selected)
//            ErrorListPanel elp = (ErrorListPanel) c.getComponents()[0];
//
//            // Get ErrorListPanel dimensions
//            double elpWidth = elp.getWidth();
//            double elpHeight = elp.getHeight();
//
//            // Scale the coordinates. ErrorListPanel's (0, 0) starts at upper left corner
//            // ELP dimensions are 1055x236 - Box coords are 1055x36
//            double scaledX = ((elpWidth/2) * elpWidth) / elp.getBounds().width;
//            double scaledY = ((36/2) * elpHeight) / elp.getBounds().height;
//
//            // Get the Box at the scaled coordinates
//            Box box = (Box) elp.getComponentAt((int) scaledX, (int) scaledY);
//            RolloverButton btn = (RolloverButton) box.getComponents()[13];
//            btn.doClick(); // click the clear btn
//        }
    // }

    @Override
    public void showStats() {

        clearWarnAndErr();
        Log.log(Log.MESSAGE, this, ":showStats(): starting");
        if (StringUtil.emptyString(kif.filename))
            kif.filename = view.getBuffer().getPath();
        String contents = view.getTextArea().getText();
        if (!parseKif(contents))
            return;

        Log.log(Log.MESSAGE, this, ":showStats(): path: " + kif.filename);
        String filename = FileUtil.noPath(kif.filename);
        StringBuilder stats = new StringBuilder();
        try {
            int termCount = 0;
            int otherTermCount = 0;
            FileSpec defn;
            String thisNoPath;
            for (String t : kif.terms) {
                if (t == null) {
                    Log.log(Log.WARNING, this, ":showStats(): null term");
                    continue;
                }
                defn = findDefn(t);
                if (defn == null) {
                    if (!Formula.isLogicalOperator(t)) {
                        Log.log(Log.WARNING, this, ":showStats(): no definition found for: " + t);
                        continue;
                    } else
                        thisNoPath = "";
                }
                else
                    thisNoPath = FileUtil.noPath(defn.filepath);
                //Log.log(Log.WARNING, this, " ");
                //Log.log(Log.WARNING, this, "showStats(): filename: " + filename);
                //Log.log(Log.WARNING, this, "showStats(): this no path: " + thisNoPath);
                if (thisNoPath.equals(filename) || StringUtil.emptyString(thisNoPath)) {
                    Log.log(Log.MESSAGE, this, ":showStats(): ******* in this file: " + t);
                    stats.append("******* in this file: ").append(t).append('\n');
                    termCount++;
                } else {
                    if (!Formula.isLogicalOperator(t)) {
                        //Log.log(Log.WARNING, this, "showStats(): not in this: " + t);
                        otherTermCount++;
                    }
                }
            }
            Log.log(Log.MESSAGE, this, ":showStats(): # terms: " + termCount);
            stats.append("# terms: ").append(termCount).append('\n');
            Log.log(Log.MESSAGE, this, ":showStats(): # terms used from other files: " + otherTermCount);
            stats.append("# terms used from other files: ").append(otherTermCount).append('\n');
            Log.log(Log.MESSAGE, this, ":showStats(): # axioms: " + kif.formulaMap.keySet().size());
            stats.append("# axioms: ").append(kif.formulaMap.keySet().size()).append('\n');
            int ruleCount = 0;
            for (Formula f : kif.formulaMap.values()) {
                if (f.isRule())
                    ruleCount++;
            }
            Log.log(Log.MESSAGE, this, ":showStats(): # rules: " + ruleCount);
            stats.append("# rules: ").append(ruleCount).append('\n');
            Log.log(Log.MESSAGE, this, ":showStats(): done reading kif file");
        } catch (Exception e) {
            if (log)
                Log.log(Log.ERROR, this, ":showStats()",e);
            String msg = "Error in SUMOjEdit.showStats() with: " + kif.filename + ": " + e;
            System.err.println(msg);
        }
        jEdit.newFile(view);
        view.getTextArea().setSelectedText(stats.toString());
        Log.log(Log.MESSAGE, this, ":showStats(): complete");
    }

    @Override
    public void checkErrors() {

        clearWarnAndErr();
        Log.log(Log.MESSAGE, this, ":checkErrors(): starting");

        // Always target the active window (supports multiple jEdit Views)
        view = jEdit.getActiveView();
        this.errsrc = ensureErrorSource(view); // select/create for this window (do NOT unregister others)

        // Freeze the file path NOW and keep using it for this run
        final String filePath = view.getBuffer().getPath();

        // TPTP-like files (thf/tff/fof/cnf/p/tptp): no checking (feature removed)
        if (isTptpFile(filePath)) {
            Log.log(Log.MESSAGE, this, ":checkErrors(): TPTP checking disabled; skipping");
            return;
        }

        // KIF path unchanged
        if (StringUtil.emptyString(kif.filename)) {
            kif.filename = filePath; // keep for status/logs, but don't use for error entries
        }
        final String contents = view.getTextArea().getText();

        Runnable r = () -> {
            checkErrorsBody(contents, filePath);
            // Force ErrorList refresh on EDT
            ThreadUtilities.runInDispatchThread(() -> {
                if (view != null) {
                    view.getDockableWindowManager().showDockableWindow("error-list");
                }
            });
            Log.log(Log.MESSAGE, this, ":checkErrors(): complete");
        };
        Runnable rs = create(r, () -> "Checking errors");
        startBackgroundThread(rs);
    }

    /** ***************************************************************
     * Hack to get the ErrorList to show all errors with multiple results mode on.
     * Should not have to do this, ErrorList refresh bug?
     */
//    private void errorListRefreshHack() {
//
//        ErrorSource.unregisterErrorSource(errsrc);
//        view.getBuffer().reload(view);
//        ErrorSource.registerErrorSource(errsrc);
//    }

    /** Utility method to parse KIF
     *
     * @param contents the contents of a KIF file to parse
     * @return true if successful parse, no error or warnings
     */
    private boolean parseKif(String contents) {

        boolean retVal = false;
        try (Reader r = new StringReader(contents)) {
            kif.parse(r);
            Log.log(Log.MESSAGE, this, ":parseKif(): done reading kif file");
            retVal = true;
        } catch (Exception e) {
            if (log)
                Log.log(Log.ERROR, this, ":checkErrorsBody()", e);
            String msg = "Error in SUMOjEdit.parseKif() with: " + kif.filename + ": " + e;
            System.err.print(msg);
        } finally {
            logKifWarnAndErr();
        }
        return retVal;
    }

    /** ***************************************************************
     * Check for a variety of syntactic and semantic errors and warnings in a
     * given buffer
     *
     * @param contents the SUO-KIF to check
     */
    protected void checkErrorsBody(String contents, final String filePath) {

        List<ErrRec> msgs = KifFileChecker.check(contents, filePath);
        addErrorsDirect(msgs);
    }

    private void addErrorsDirect(java.util.List<ErrRec> errors) {
        if (errors == null || errors.isEmpty()) return;
        errorlist.ErrorSource.unregisterErrorSource(errsrc);
        try {
            final org.gjt.sp.jedit.Buffer buf = (view != null ? view.getBuffer() : null);

            for (ErrRec e : errors) {
                int line = Math.max(0, e.line);
                int startCol = Math.max(0, e.start);
                int endCol = Math.max(startCol + 1, e.end);

                // Clamp to actual buffer content if available
                if (buf != null) {
                    int maxLine = Math.max(0, buf.getLineCount() - 1);
                    if (line > maxLine) line = maxLine;

                    try {
                        int lineLen = buf.getLineLength(line);
                        int lineStart = buf.getLineStartOffset(line);
                        String lineText = buf.getText(lineStart, lineLen);

                        // If span is 1 char (common from col+1), expand to cover the token
                        if (endCol <= startCol + 1) {
                            int s = Math.min(startCol, Math.max(0, lineText.length()));
                            int epos = s;
                            while (epos < lineText.length()) {
                                char ch = lineText.charAt(epos);
                                if (!(Character.isLetterOrDigit(ch) || ch == '_' || ch == '-')) break;
                                epos++;
                            }
                            endCol = Math.max(epos, startCol + 1);
                        }

                        // Keep within line bounds
                        if (startCol > lineText.length()) startCol = Math.max(0, lineText.length() - 1);
                        if (endCol > lineText.length())   endCol   = lineText.length();
                    } catch (Throwable ignore) {
                        // fall back to original indices
                    }
                }

                String msgWithSnippet = appendSnippet(e.msg, e.file, line);
                errsrc.addError(e.type, e.file, line, startCol, endCol, msgWithSnippet);
            }
        } finally {
            errorlist.ErrorSource.registerErrorSource(errsrc);
        }
    }

    /**
     * ***************************************************************
     * Backward-compatible shim: delegate to the 2-arg version.
     */
    protected void checkErrorsBody(String contents) {
        String fn = (this.kif != null && !StringUtil.emptyString(this.kif.filename))
                ? this.kif.filename
                : "untitled.kif";
        checkErrorsBody(contents, fn);
    }

    /**
     * Find all occurrences of a term in the buffer and report errors for each
     */
        private void reportAllOccurrencesInBuffer(final String filePath,
                                                String term, String errorMessage,
                                                String[] bufferLines, int errorType) {
            final int n = bufferLines.length;
            final int chunk = Math.max(50, n / Math.max(2, Runtime.getRuntime().availableProcessors() - 1));

            final java.util.List<java.util.concurrent.Callable<java.util.List<ErrRec>>> tasks =
                    new java.util.ArrayList<>();

            for (int start = 0; start < n; start += chunk) {
                final int s = start;
                final int e = Math.min(n, start + chunk);
                tasks.add(() -> {
                    java.util.List<ErrRec> local = new java.util.ArrayList<>();
                    for (int lineNum = s; lineNum < e; lineNum++) {
                        final String line = bufferLines[lineNum];
                        int searchStart = 0;
                        while (searchStart < line.length()) {
                            int pos = findTermInLine(line, term, searchStart);
                            if (pos == -1) break;
                            local.add(new ErrRec(
                                errorType, filePath, lineNum, pos, pos + term.length(), errorMessage
                            ));
                            searchStart = pos + term.length();
                        }
                    }
                    return local;
                });
            }

            try {
                // run on our dedicated pool (NOT the clamped common pool)
                java.util.List<java.util.concurrent.Future<java.util.List<ErrRec>>> futures =
                        CHECKER_POOL.invokeAll(tasks);

                // merge results
                java.util.List<ErrRec> merged = new java.util.ArrayList<>();
                for (java.util.concurrent.Future<java.util.List<ErrRec>> f : futures) {
                    merged.addAll(f.get());
                }

                // single EDT hop for this term’s occurrences
                addErrorsBatch(merged);
            } catch (InterruptedException | ExecutionException ex) {
                // safe fallback: sequential scan, same results but still batched onto the EDT
                java.util.List<ErrRec> fallback = new java.util.ArrayList<>();
                for (int lineNum = 0; lineNum < bufferLines.length; lineNum++) {
                    final String line = bufferLines[lineNum];
                    int searchStart = 0;
                    while (searchStart < line.length()) {
                        int pos = findTermInLine(line, term, searchStart);
                        if (pos == -1) break;
                        fallback.add(new ErrRec(
                            errorType, filePath, lineNum, pos, pos + term.length(), errorMessage
                        ));
                        searchStart = pos + term.length();
                    }
                }
                addErrorsBatch(fallback);
            }
        }

    /**
     * Find a term in a line with word boundary checking
     */
    private int findTermInLine(String line, String term, int startPos) {
        int pos = line.indexOf(term, startPos);
        while (pos != -1) {
            // Check if this is a complete term (not part of a larger term)
            boolean validStart = (pos == 0 || !isTermChar(line.charAt(pos - 1)));
            boolean validEnd = (pos + term.length() >= line.length()
                            || !isTermChar(line.charAt(pos + term.length())));

            if (validStart && validEnd) {
                return pos;
            }
            pos = line.indexOf(term, pos + 1);
        }
        return -1;
    }

    /**
     * Check if a character can be part of a term
     */
    private boolean isTermChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    /**
     * Find where a formula appears in the buffer
     */
    private int findFormulaInBuffer(String formulaStr, String[] bufferLines) {
        // Get the first meaningful line of the formula (skip empty lines)
        String[] formulaLines = formulaStr.split("\n");
        String firstLine = "";
        for (String line : formulaLines) {
            if (!line.trim().isEmpty()) {
                firstLine = line.trim();
                break;
            }
        }

        if (firstLine.isEmpty()) return -1;

        // Search for this line in the buffer
        for (int i = 0; i < bufferLines.length; i++) {
            if (bufferLines[i].contains(firstLine)) {
                return i;
            }
        }

        // If not found, try a shorter match (first 20 chars)
        if (firstLine.length() > 20) {
            String shortMatch = firstLine.substring(0, 20);
            for (int i = 0; i < bufferLines.length; i++) {
                if (bufferLines[i].contains(shortMatch)) {
                    return i;
                }
            }
        }

        return -1;
    }

    @Override
    public void toTPTP() {

        clearWarnAndErr();
        Log.log(Log.MESSAGE, this, ":toTPTP(): starting");
        if (StringUtil.emptyString(kif.filename))
            kif.filename = view.getBuffer().getPath();
        String contents = view.getTextArea().getText();
        String selected = view.getTextArea().getSelectedText();
        if (!StringUtil.emptyString(selected))
            contents = selected;

        if (!parseKif(contents))
            return;

        StringBuilder sb = new StringBuilder();
        try {
            //Log.log(Log.WARNING,this,"toTPTP(): done reading kif file");
            java.util.List<Formula> ordered = kif.lexicalOrder();
            String /*pred,*/ tptpStr;
            TPTPVisitor sv;
            Map<String, TPTPFormula> hm;
            for (Formula f : ordered) {
                //Log.log(Log.WARNING,this,"toTPTP(): SUO-KIF: " + f.getFormula());
//                pred = f.car();
                //Log.log(Log.WARNING,this,"toTPTP pred: " + pred);
                //Log.log(Log.WARNING,this,"toTPTP kb: " + kb);
                //Log.log(Log.WARNING,this,"toTPTP kb cache: " + kb.kbCache);
                //Log.log(Log.WARNING,this,"toTPTP gather pred vars: " + PredVarInst.gatherPredVars(kb,f));
                //if (f.predVarCache != null && f.predVarCache.size() > 0)
                //  Log.log(Log.WARNING,this,"toTPTP Formula.isHigherOrder(): pred var cache: " + f.predVarCache);
                if (f.isHigherOrder(kb) || (f.predVarCache != null && !f.predVarCache.isEmpty()))
                    continue;
                tptpStr = "fof(f4434,axiom," + SUMOformulaToTPTPformula.process(f, false) + ",[file('kb_" + f.getSourceFile() + "_" + f.startLine + "',unknown)]).";
                //Log.log(Log.WARNING,this,"toTPTP(): formatted as TPTP: " + tptpStr);
                sv = new TPTPVisitor();
                sv.parseString(tptpStr);
                hm = sv.result;
                for (String s : hm.keySet())
                    sb.append(hm.get(s).formula).append("\n\n");
            }
            jEdit.newFile(view);
            view.getTextArea().setText(sb.toString());
        } catch (Exception e) {
            if (log)
                Log.log(Log.ERROR, this, ":toTPTP()", e);
            String msg = "Error in SUMOjEdit.toTPTP() with " + kif.filename + ": " + e;
            System.err.println(msg);
        }
        Log.log(Log.MESSAGE, this, ":toTPTP(): complete");
    }

    @Override
    public void fromTPTP() {

        clearWarnAndErr();
        Log.log(Log.MESSAGE, this, ":fromTPTP(): starting");
        String contents = view.getTextArea().getText();
        String selected = view.getTextArea().getSelectedText();
        if (!StringUtil.emptyString(selected))
            contents = selected;
        if (StringUtil.emptyString(kif.filename))
            kif.filename = view.getBuffer().getPath();
        try {
            TPTPVisitor sv = new TPTPVisitor();
            if (new File(kif.filename).exists())
                sv.parseFile(kif.filename);
            else
                sv.parseString(contents);
            Map<String, TPTPFormula> hm = sv.result;
            jEdit.newFile(view);
            StringBuilder result = new StringBuilder();
            for (String s : hm.keySet())
                result.append(hm.get(s).formula).append("\n\n");
            view.getTextArea().setText(result.toString());
            if (StringUtil.emptyString(result))
                Log.log(Log.WARNING, this, ":fromTPTP(): empty result");
            else
                Log.log(Log.MESSAGE, this, ":fromTPTP(): result.length: " + result.length());
        } catch (Exception e) {
            if (log)
                Log.log(Log.ERROR, this, ":fromTPTP()", e);
            String msg = "Error in SUMOjEdit.fromTPTP() with: " + kif.filename + ": " + e;
            System.err.println(msg);
        }
        Log.log(Log.MESSAGE, this, ":fromTPTP(): complete");
    }

    // === BEGIN: Drop-down AutoComplete (self-contained, Java 11 compatible) ===

    /** Menu action entrypoint. Called by actions.xml via SUMOjEditPlugin.sje.autoComplete() */
    @Override
    public void autoComplete() {
        // Respect AC mode: only show drop-down in DROPDOWN_ONLY or BOTH
        String mode = jEdit.getProperty("sumojedit.ac.mode", "BOTH");
        boolean dropdownEnabled = "DROPDOWN_ONLY".equals(mode) || "BOTH".equals(mode);
        if (!dropdownEnabled) return;

        final org.gjt.sp.jedit.View v = jEdit.getActiveView();
        if (v == null) return;
        final org.gjt.sp.jedit.textarea.JEditTextArea ta = v.getTextArea();
        if (ta == null) return;

        SimpleCompletionPopup.show(ta);
    }

    /** Minimal, reliable completion popup that reads tokens from the current buffer. */
    private static final class SimpleCompletionPopup {
        private static javax.swing.JPopupMenu active;

        static void show(final org.gjt.sp.jedit.textarea.JEditTextArea ta) {
            try {
                final int caret = ta.getCaretPosition();
                final java.awt.Point p = ta.offsetToXY(caret);
                if (p == null) return;

                final String prefix = currentPrefix(ta);
                if (prefix.isEmpty()) return;

                final java.util.LinkedHashSet<String> cands = new java.util.LinkedHashSet<>();
                collectTokens(ta.getBuffer(), cands, 128_000);

                final java.util.ArrayList<String> list = new java.util.ArrayList<>();
                final String preLower = prefix.toLowerCase();
                for (String s : cands) {
                    if (s == null || s.length() <= prefix.length()) continue;
                    if (s.toLowerCase().startsWith(preLower)) list.add(s);
                    if (list.size() >= 200) break;
                }
                if (list.isEmpty()) return;
                java.util.Collections.sort(list, String.CASE_INSENSITIVE_ORDER);

                final javax.swing.JList<String> jlist = new javax.swing.JList<>(list.toArray(new String[0]));
                jlist.setVisibleRowCount(Math.min(12, list.size()));
                jlist.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
                jlist.setSelectedIndex(0);

                final javax.swing.JScrollPane scroller = new javax.swing.JScrollPane(jlist);
                scroller.setBorder(javax.swing.BorderFactory.createEmptyBorder(0,0,0,0));

                final javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
                popup.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.GRAY));
                popup.add(scroller);

                dismiss();

                jlist.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                        if (e.getClickCount() >= 2) { accept(ta, prefix, jlist.getSelectedValue()); }
                    }
                });

                // FIXED: Enhanced key handling to properly manage Tab key
                jlist.addKeyListener(new java.awt.event.KeyAdapter() {
                    @Override public void keyPressed(java.awt.event.KeyEvent e) {
                        int code = e.getKeyCode();
                        if (code == java.awt.event.KeyEvent.VK_ENTER) {
                            accept(ta, prefix, jlist.getSelectedValue());
                            e.consume();
                        } else if (code == java.awt.event.KeyEvent.VK_ESCAPE) {
                            dismiss();
                            e.consume();
                        } else if (code == java.awt.event.KeyEvent.VK_TAB) {
                            // Accept the selection with Tab
                            accept(ta, prefix, jlist.getSelectedValue());
                            e.consume();
                        }
                    }
                });

                // FIXED: Add a key event dispatcher specifically for this popup
                java.awt.KeyEventDispatcher popupDispatcher = new java.awt.KeyEventDispatcher() {
                    @Override
                    public boolean dispatchKeyEvent(java.awt.event.KeyEvent e) {
                        // Only handle events when our popup is active
                        if (popup != active || !popup.isVisible()) return false;

                        if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED) {
                            if (e.getKeyCode() == java.awt.event.KeyEvent.VK_TAB) {
                                // Tab pressed - accept selection and close
                                accept(ta, prefix, jlist.getSelectedValue());
                                e.consume();
                                // Remove this dispatcher after use
                                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                                    .removeKeyEventDispatcher(this);
                                return true;
                            }
                        }
                        return false;
                    }
                };

                // Install the dispatcher temporarily while popup is shown
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .addKeyEventDispatcher(popupDispatcher);

                // Add popup listener to clean up dispatcher when popup closes
                popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                    @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                            .removeKeyEventDispatcher(popupDispatcher);
                    }
                    @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {}
                    @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
                });

                int yBase = p.y + ta.getPainter().getFontMetrics().getAscent();
                popup.show(ta, p.x, yBase + 2);
                active = popup;

                ThreadUtilities.runInDispatchThread(jlist::requestFocusInWindow);
            } catch (Throwable ignore) {}
        }

        private static void accept(final org.gjt.sp.jedit.textarea.JEditTextArea ta,
                                   final String prefix,
                                   final String chosen) {
            try {
                dismiss();
                if (chosen == null || chosen.length() <= prefix.length()) return;
                final org.gjt.sp.jedit.buffer.JEditBuffer buf = ta.getBuffer();
                if (buf == null) return;
                final int caret = ta.getCaretPosition();
                final String suffix = chosen.substring(prefix.length());
                buf.beginCompoundEdit();
                try {
                    buf.insert(caret, suffix);
                } finally {
                    buf.endCompoundEdit();
                }
                ta.setCaretPosition(caret + suffix.length());
            } catch (Throwable ignore) {}
        }

        private static void dismiss() {
            try {
                if (active != null && active.isVisible()) active.setVisible(false);
            } catch (Throwable ignore) {} finally {
                active = null;
            }
        }

        private static String currentPrefix(final org.gjt.sp.jedit.textarea.JEditTextArea ta) {
            final org.gjt.sp.jedit.buffer.JEditBuffer buf = ta.getBuffer();
            if (buf == null) return "";
            int caret = ta.getCaretPosition();
            int start = caret;
            while (start > 0) {
                String ch = buf.getText(start - 1, 1);
                if (ch == null || ch.isEmpty()) break;
                char c = ch.charAt(0);
                if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) break;
                start--;
            }
            int len = caret - start;
            return (len <= 0) ? "" : buf.getText(start, len);
        }

        private static void collectTokens(final org.gjt.sp.jedit.buffer.JEditBuffer buf,
                                          final java.util.Set<String> out,
                                          final int maxChars) {
            try {
                int len = Math.min(buf.getLength(), Math.max(64_000, maxChars));
                if (len <= 0) return;
                String text = buf.getText(0, len);
                int n = text.length();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    char c = text.charAt(i);
                    if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                        sb.append(c);
                    } else {
                        if (sb.length() > 0) { out.add(sb.toString()); sb.setLength(0); }
                    }
                }
                if (sb.length() > 0) out.add(sb.toString());
            } catch (Throwable ignore) {}
        }
    }
    // === END: Drop-down AutoComplete ===

    /** ***************************************************************
     */
    public static void showHelp() {

        System.out.println("Diagnostics");
        System.out.println("  options:");
        System.out.println("  -h - show this help screen");
        System.out.println("  -d - <fname> - test diagnostics");
        System.out.println("  -q - run a default query");
    }

    /** ***************************************************************
     * Test method for this class.
     * @param args command line arguments
     */
    public static void main(String args[]) {

        System.out.println("INFO: In SUMOjEdit.main()");
        SUMOjEdit sje = null;
        KB kb = null;
        if (args != null && args.length > 0 && args[0].equals("-h"))
            showHelp();
        else {
            SUMOtoTFAform.initOnce();
            sje = new SUMOjEdit();
            kb = sje.kb = SUMOtoTFAform.kb;
            sje.fp = SUMOtoTFAform.fp;
            sje.errsrc = new DefaultErrorSource(sje.getClass().getName(), null);
            ErrorSource.registerErrorSource(sje.errsrc);
        }

        if (args != null && args.length > 1 && args[0].equals("-d")) {
            String contents = String.join("\n", FileUtil.readLines(args[1], false));
            sje.kif.filename = args[1];
            sje.checkErrorsBody(contents, args[1]);
        } else if (args != null && args.length > 0 && args[0].equals("-q")) {
            String contents = "(routeBetween ?X MenloParkCA MountainViewCA)";
            System.out.println("E input: " + contents);
            EProver eprover = kb.askEProver(contents, 30, 1);
            TPTP3ProofProcessor tpp = new TPTP3ProofProcessor();
            tpp.parseProofOutput(eprover.output, contents, kb, eprover.qlist);
            //tpp.processAnswersFromProof(contents);
            java.util.List<String> proofStepsStr = new ArrayList<>();
            for (TPTPFormula ps : tpp.proof) {
                proofStepsStr.add(ps.toString());
            }
            StringBuilder result = new StringBuilder();
            if (tpp.bindingMap != null && !tpp.bindingMap.isEmpty()) {
                result.append("Bindings: ").append(tpp.bindingMap);
            } else if (tpp.bindings != null && !tpp.bindings.isEmpty()) {
                result.append("Bindings: ").append(tpp.bindings);
            }
            if (tpp.proof == null || tpp.proof.isEmpty()) {
                result.append(tpp.status);
            } else {
                result.append("\n\n").append(StringUtil.arrayListToCRLFString(proofStepsStr));
            }
            System.out.println("\nE result: " + result.toString());
            System.out.println();
        } else {
            showHelp();
        }
    }

        // ===== TPTP integration via external tptp4X =====
    private static final String PROP_TPTP4X_PATH = "sumojedit.tptp4x.path";
    // Accept optional filename before line/col, e.g. "file.tff:12:34: msg"
    private static final java.util.regex.Pattern TPTP_LOC =
        java.util.regex.Pattern.compile("(?:[^:]+:)?(\\d+):(\\d+):\\s*(.*)");
    private static final java.util.Set<String> TPTP_EXTS = java.util.Set.of("tptp","p","fof","cnf","tff","thf");

    private String resolveTptp4xPath() {
        String p = jEdit.getProperty(PROP_TPTP4X_PATH);
        return (p != null && !p.isBlank()) ? p : System.getProperty("user.home") + "/bin/tptp4X";
    }

    // NEW: ensure tptp4X exists and is executable; surface a clear ErrorList message if not
    private boolean ensureTptp4x(String filePath) {
        String p = resolveTptp4xPath();
        File f = new File(p);
        if (!f.exists() || !f.canExecute()) {
            addErrorsDirect(java.util.List.of(
                new ErrRec(ErrorSource.ERROR,
                           (filePath != null ? filePath : "untitled"),
                           0, 0, 1,
                           "tptp4X not found or not executable at: " + p +
                           " (set jEdit property 'sumojedit.tptp4x.path')")));
            ThreadUtilities.runInDispatchThread(() ->
                view.getDockableWindowManager().showDockableWindow("error-list"));
            return false;
        }
        return true;
    }

    private static boolean isTptpFile(String path) {
        if (path == null) return false;
        int i = path.lastIndexOf('.');
        if (i < 0) return false;
        String ext = path.substring(i+1).toLowerCase(java.util.Locale.ROOT);
        return TPTP_EXTS.contains(ext);
    }

    private static java.nio.file.Path writeTemp(String text, String suffix) throws java.io.IOException {
        var tmp = java.nio.file.Files.createTempFile("sje-", suffix);
        java.nio.file.Files.writeString(tmp, text);
        return tmp;
    }

    private static final class ProcOut {
        final String out, err; final int code;
        ProcOut(String o, String e, int c){ out=o; err=e; code=c; }
    }

    private ProcOut runTptp4x(java.nio.file.Path file, String... args)
            throws java.io.IOException, InterruptedException {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(resolveTptp4xPath());
        java.util.Collections.addAll(cmd, args);
        cmd.add(file.toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Process pr = pb.start();
        String out = new String(pr.getInputStream().readAllBytes());
        String err = new String(pr.getErrorStream().readAllBytes());
        int code = pr.waitFor();
        return new ProcOut(out, err, code);
    }

    private java.util.List<ErrRec> parseTptpOutput(String filePath, String text, int errType) {
        java.util.List<ErrRec> list = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) return list;
        for (String ln : text.split("\\R")) {
            if (ln.isBlank()) continue;
            var m = TPTP_LOC.matcher(ln);
            if (m.find()) {
                int line = Math.max(0, Integer.parseInt(m.group(1)) - 1);
                int col  = Math.max(0, Integer.parseInt(m.group(2)) - 1);
                String msg = m.group(3).isBlank() ? "tptp4X" : m.group(3).trim();
                list.add(new ErrRec(errType, filePath, line, col, col+1, msg));
            } else {
                final String trimmed = ln.trim();
                // Ignore pure comment lines so they never appear as bogus warnings
                if (trimmed.startsWith("%")) continue;

                // No line/col available; attach to start of file so user still sees it
                list.add(new ErrRec(errType, filePath, 0, 0, 1, trimmed));
            }
        }
        return list;
    }

    @Override
    public void tptpFormatBuffer() {
        clearWarnAndErr();
        final var view = jEdit.getActiveView();
        final var ta   = view.getTextArea();
        final var buf  = view.getBuffer();

        final String filePath = (buf.getPath() != null && !buf.getPath().isBlank())
            ? buf.getPath()
            : buf.getName();  // unsaved buffers: fall back to buffer name (not a directory)

        final boolean isTptp = isTptpFile(filePath);
        final String selected = ta.getSelectedText();
        final String text     = (selected != null && !selected.isBlank()) ? selected : ta.getText();

        final boolean replaceWhole = (selected == null || selected.isBlank()) && isTptp;

        // Preserve original extension for tptp4X input (helps parser selection)
        final String ext;
        {
            int dot = filePath.lastIndexOf('.');
            ext = (dot >= 0 && dot < filePath.length() - 1)
                    ? filePath.substring(dot + 1).toLowerCase(java.util.Locale.ROOT)
                    : "tptp";
        }

        startBackgroundThread(create(() -> {
            try {
                if (isTptp) {
                    // NEW: preserve comments for both selection and whole-buffer paths
                    final String merged = formatTptpPreserveCommentsClauseSafe(text, ext);
                    ThreadUtilities.runInDispatchThread(() -> {
                        if (replaceWhole) ta.setText(merged);
                        else ta.setSelectedText(merged);
                    });
                    return;
                }

                // Non-TPTP fallback: old generic path
                var tmp = writeTemp(text, "." + ext);
                var po  = runTptp4x(tmp, "-f","tptp", "-u","human");
                if (po.code == 0 && !po.out.isBlank()) {
                    ThreadUtilities.runInDispatchThread(() -> {
                        if (replaceWhole) ta.setText(po.out);
                        else if (selected != null && !selected.isBlank()) ta.setSelectedText(po.out);
                        else { jEdit.newFile(view); view.getTextArea().setText(po.out); }
                    });
                } else {
                    addErrorsDirect(parseTptpOutput(filePath, po.err.isBlank() ? po.out : po.err, ErrorSource.WARNING));
                    ThreadUtilities.runInDispatchThread(() ->
                        view.getDockableWindowManager().showDockableWindow("error-list"));
                }
            } catch (Throwable t) {
                addErrorsDirect(java.util.List.of(
                    new ErrRec(ErrorSource.ERROR, filePath, 0, 0, 1, "Format failed: " + t.getMessage())
                ));
                ThreadUtilities.runInDispatchThread(() ->
                    view.getDockableWindowManager().showDockableWindow("error-list"));
            }
        }, () -> "Format TPTP"));
    }

    /**
     * Robust formatting that preserves all '%' comments exactly:
     *  - Lines starting with '%' are copied verbatim.
     *  - Inline comments (code ... '%' comment) are detected (outside quotes),
     *    the code part is formatted, and the original '%' tail is reattached
     *    to the LAST line of that formatted clause.
     *  - Only complete clauses (ending with '.') are sent to tptp4X.
     */
    private String formatTptpPreserveCommentsClauseSafe(String text, String ext) {
        final String[] lines = text.split("\\R", -1); // keep structure
        final java.util.List<String> out = new java.util.ArrayList<>();
        final StringBuilder clause = new StringBuilder();

        // We'll attach the next inline comment tail to the last formatted line of the flushed clause
        final String[] pendingInlineComment = new String[1]; // null or original "% ..."

        // Find first '%' that is NOT inside single-quoted string (TPTP style)
        java.util.function.IntUnaryOperator firstCommentPos = (/*unused*/ignored) -> { throw new UnsupportedOperationException(); };
        firstCommentPos = (ignored) -> -1; // placeholder to satisfy Java syntax in this snippet

        java.util.function.Function<String,Integer> firstPctOutsideQuotes = (line) -> {
            boolean inStr = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '\'') {
                    // TPTP escapes quotes by doubling ''
                    if (inStr) {
                        if (i + 1 < line.length() && line.charAt(i + 1) == '\'') {
                            i++; // skip escaped quote
                        } else {
                            inStr = false;
                        }
                    } else {
                        inStr = true;
                    }
                } else if (c == '%' && !inStr) {
                    return i;
                }
            }
            return -1;
        };

        java.util.function.Consumer<String> flushClause = (inlineComment) -> {
            if (clause.length() == 0) {
                // Even with no clause, if there's an inline-only comment, just output it as-is.
                if (inlineComment != null) out.add(inlineComment + "\n");
                pendingInlineComment[0] = null;
                return;
            }
            final String code = clause.toString();
            String formatted = code;
            try {
                var tmp = writeTemp(code, "." + ext);
                var po  = runTptp4x(tmp, "-f","tptp", "-u","human");
                if (po.code == 0 && !po.out.isBlank()) {
                    formatted = po.out.replace("\r\n","\n").replace("\r","\n");
                }
            } catch (Throwable ignore) { /* keep original */ }

            // If we have an inline comment tail, attach it to the last line of 'formatted'
            if (inlineComment != null) {
                String[] flines = formatted.split("\\R", -1);
                if (flines.length == 0) {
                    // degenerate, just emit the comment line
                    out.add(inlineComment + "\n");
                } else {
                    int last = flines.length - 1;
                    // Preserve exact spacing before '%' by inserting a single space if needed
                    if (!flines[last].endsWith(" ") && !inlineComment.startsWith(" ")) {
                        flines[last] = flines[last] + " " + inlineComment;
                    } else {
                        flines[last] = flines[last] + inlineComment;
                    }
                    formatted = String.join("\n", flines);
                }
            }

            out.add(formatted);
            clause.setLength(0);
            pendingInlineComment[0] = null;
        };

        for (String ln : lines) {
            final String trimmed = ln.trim();

            // Lines that are blank or start with '%' -> verbatim (but flush any pending clause first)
            if (trimmed.isEmpty() || trimmed.startsWith("%")) {
                flushClause.accept(pendingInlineComment[0]);  // handle any deferred inline comment
                out.add(ln + (ln.endsWith("\n") ? "" : "\n")); // keep exact line (with \n)
                continue;
            }

            // Detect inline comment outside quotes
            final int ic = firstPctOutsideQuotes.apply(ln);
            if (ic >= 0) {
                String codePart = ln.substring(0, ic);
                String commentTail = ln.substring(ic);  // includes '%' and whatever follows
                // If code part is non-empty, add it to the clause buffer
                if (!codePart.trim().isEmpty()) {
                    clause.append(codePart).append('\n');
                    // If codePart closes a clause, flush and attach the inline comment to the last formatted line
                    if (codePart.trim().endsWith(".")) {
                        flushClause.accept(commentTail);
                    } else {
                        // Clause not closed yet: remember the inline comment, but we will attach it
                        // when we eventually flush (at the '.' or end of selection/buffer)
                        pendingInlineComment[0] = commentTail;
                    }
                } else {
                    // No code before '%': treat as a pure comment line
                    flushClause.accept(pendingInlineComment[0]);
                    out.add(ln + (ln.endsWith("\n") ? "" : "\n"));
                }
                continue;
            }

            // Normal code line (no inline '%'): accumulate into current clause
            clause.append(ln).append('\n');
            if (trimmed.endsWith(".")) {
                // Clause boundary; flush with any pending inline comment to attach right now
                flushClause.accept(pendingInlineComment[0]);
            }
        }

        // End of input: flush any pending clause; if there's a deferred inline comment, put it on a new line
        if (clause.length() > 0) {
            // No '.' seen; don't force a format pass—emit original clause
            String code = clause.toString();
            if (pendingInlineComment[0] != null) {
                // attach the comment to the last line of the original code text
                String[] fl = code.split("\\R", -1);
                if (fl.length > 0) {
                    int last = fl.length - 1;
                    if (!fl[last].endsWith(" ") && !pendingInlineComment[0].startsWith(" ")) {
                        fl[last] = fl[last] + " " + pendingInlineComment[0];
                    } else {
                        fl[last] = fl[last] + pendingInlineComment[0];
                    }
                    code = String.join("\n", fl);
                } else {
                    code = code + pendingInlineComment[0];
                }
            }
            out.add(code);
            clause.setLength(0);
            pendingInlineComment[0] = null;
        } else if (pendingInlineComment[0] != null) {
            // Just in case: no code to attach to; emit the inline comment as its own line
            out.add(pendingInlineComment[0] + "\n");
            pendingInlineComment[0] = null;
        }

        return String.join("", out);
    }

    /** METHOD REMOVED */
//  @Override
//  public void tptpCheckBuffer()

}