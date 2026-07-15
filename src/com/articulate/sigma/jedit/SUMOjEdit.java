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
import com.articulate.sigma.editor.*;
import com.articulate.sigma.nlg.LanguageFormatter;
import com.articulate.sigma.parsing.SuokifApp;
import com.articulate.sigma.parsing.SuokifVisitor;
import com.articulate.sigma.tp.*;
import com.articulate.sigma.trans.*;
import com.articulate.sigma.utils.*;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;

import com.google.common.io.Files;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.MenuElement;

import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.io.VFSManager;
import org.gjt.sp.jedit.menu.EnhancedMenu;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.textarea.Selection;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.util.Log;
import org.gjt.sp.util.ThreadUtilities;

import tptp_parser.*;

/******************************************************************
 * A SUO-KIF editor and error checker
 */
public class SUMOjEdit implements EBComponent, SUMOjEditActions {

    /******************************************************************
     * Utility class that contains searched term line and filepath information
     */
    public class FileSpec {

        public String filepath = "";
        public int line = -1;
    }

    /******************************************************************
     */
    private static final class ProcOut {
        
        final String out, err; final int code;
        ProcOut(String o, String e, int c){ out=o; err=e; code=c; }
    }

    /**  */
    private AutoCompleteManager autoComplete;
    /**  */
    public static boolean log = true;
    /**  */
    protected final KIF kif;
    /**  */
    protected KB kb;
    /**  */
    protected FormulaPreprocessor fp;
    /**  */
    private final Map<org.gjt.sp.jedit.View, DefaultErrorSource> viewErrorSources = new WeakHashMap<>();
    /**  */
    protected DefaultErrorSource errsrc; 
    /**  */
    private final java.util.List<ErrRec> _pendingErrs = new java.util.ArrayList<>();
    /**  */
    private volatile boolean _flushScheduled = false;
    /**  */
    boolean testKeepPendingErrs = false;
    /**  */
    private final Set<String> notifiedNotInKB = new HashSet<>();
    /**  */
    private static final int SNIPPET_MAX = 100;
    /**  */
    private org.gjt.sp.jedit.View view;
    /**  */
    private long pluginStart;
    /**  */
    private boolean isInitialized;
    /**  */
    private static final String PROP_TPTP4X_PATH = "sumojedit.tptp4x.path";
    /**  */
    private static final java.util.regex.Pattern TPTP_LOC_COLON = java.util.regex.Pattern.compile("(?:[^:]+:)?(\\d+):(\\d+):\\s*(.*)");
    /**  */
    private static final java.util.regex.Pattern TPTP_LOC_LINECHAR = java.util.regex.Pattern.compile("(?i)\\bLine\\s+(\\d+)\\s+(?:Char|Column|Col)\\s+(\\d+)\\s*[:,-]?\\s*(.*)");
    /**  */
    private static final java.util.regex.Pattern TPTP_LOC_LINE_COMMA_COL = java.util.regex.Pattern.compile("(?i)\\bline\\s+(\\d+)\\s*,\\s*(?:column|col)\\s*(\\d+)\\s*[:,-]?\\s*(.*)");
    /**  */
    private static final java.util.Set<String> TPTP_EXTS = java.util.Set.of("tptp","p","fof","cnf","tff","thf");
    /**  */
    private static volatile java.util.concurrent.ThreadPoolExecutor CHECKER_POOL = new java.util.concurrent.ThreadPoolExecutor(
        getCheckerThreads(),
        getCheckerThreads(),
        getKeepAliveSeconds(), java.util.concurrent.TimeUnit.SECONDS,
        new java.util.concurrent.LinkedBlockingQueue<>(),
        r -> {
            Thread t = new Thread(r, "sje-checker");
            t.setDaemon(true);
            return t;
        }
    );

    /******************************************************************
     * Default constructor
     */
    public SUMOjEdit() {

        Log.init(false, 1);
        Log.log(Log.MESSAGE, SUMOjEdit.this, ": SUMOKBtoTPTPKB.rapidParsing==" + SUMOKBtoTPTPKB.rapidParsing);
        Log.log(Log.MESSAGE, SUMOjEdit.this, ": initializing");
        kif = new KIF();
        kif.filename = "";
        isInitialized = false;
    }

    /******************************************************************
     */
    private static String truncateWithEllipsis(String s, int max) {

        if (s == null) return "";
        s = s.strip();
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    /******************************************************************
     * Read a specific 0-based line from disk safely (non-EDT, no jEdit deps).
     */
    private static String safeSnippetFromFile(String filePath, int zeroBasedLine) {

        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(filePath));
            if (zeroBasedLine >= 0 && zeroBasedLine < lines.size()) {
                String line = lines.get(zeroBasedLine);
                if (line == null) return "";
                line = line.strip();
                if (line.length() <= SNIPPET_MAX) return line;
                return line.substring(0, SNIPPET_MAX);
            }
        } 
        catch (Throwable ignore) {}
        return "";
    }

    /******************************************************************
     * Fetch line text from the active buffer (EDT only). Falls back to disk if needed.
     */
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
        } 
        catch (Throwable ignore) {}
        return safeSnippetFromFile(filePath, zeroBasedLine);
    }

    /******************************************************************
     * Append formatted snippet to a base message.
     */
    private String appendSnippet(String baseMsg, String filePath, int zeroBasedLine) {

        if (baseMsg == null) return "";
        String snip = snippetFromActiveBufferOrFile(filePath, zeroBasedLine);
        String normalized = normalizeBaseMessage(baseMsg, snip);
        if (snip == null || snip.isEmpty()) return normalized;
        if (normalized.contains(snip)) return normalized;
        return normalized + " — " + snip;
    }

    /******************************************************************
     * Rewrite verbose KIF diagnostics to show only the offending term. 
     */
    private String normalizeBaseMessage(String baseMsg, String lineText) {

        if (baseMsg == null) return "";
        String msg = baseMsg.strip();
        if (msg.contains(" — ")) return msg;
        try {
            if (msg.startsWith("Term not below Entity:")) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\\(instance\\s+[^\\s)]+\\s+([^\\)\\s]+)\\)")
                        .matcher(msg);
                if (m.find()) return "Term not below Entity: " + m.group(1);
                String offender = extractNotBelowEntityOffender(msg);
                if (offender != null && !offender.isEmpty()) return "Term not below Entity: " + offender;
            }
        } 
        catch (Throwable ignore) {}
        return msg;
    }

    /******************************************************************
     * Extract the most likely offending constant from "Term not below Entity: ( ... )"
     * by choosing the last non-variable token in the formula.
     * Examples:
     *  (attribute ?DR PrivateAttribute)  -> PrivateAttribute
     *  (subclass CashPayment Payment)    -> Payment
     */
    private static String extractNotBelowEntityOffender(String msg) {

        if (msg == null) return "";
        int p = msg.indexOf('(');
        if (p < 0) return "";
        String formula = msg.substring(p);
        String[] toks = formula.split("[^A-Za-z0-9_\\-\\?]+");
        for (int i = toks.length - 1; i >= 0; i--) {
            String t = toks[i];
            if (t == null || t.isEmpty()) continue;
            if (t.charAt(0) == '?') continue;
            if ("and".equals(t) || "or".equals(t) || "not".equals(t)) continue;
            return t;
        }
        return "";
    }

    /******************************************************************
     */ 
    private void addErrorsBatch(java.util.List<ErrRec> batch) {

        if (batch == null || batch.isEmpty()) return;
        synchronized (_pendingErrs) {
            _pendingErrs.addAll(batch);
            if (testKeepPendingErrs) return;
            if (_flushScheduled) return;
            _flushScheduled = true;
        }
        ThreadUtilities.runInDispatchThread(() -> {
            java.util.List<ErrRec> toAdd;
            synchronized (_pendingErrs) {
                toAdd = new java.util.ArrayList<>(_pendingErrs);
                _pendingErrs.clear();
                _flushScheduled = false;
            }
            errorlist.ErrorSource.unregisterErrorSource(errsrc);
            try {
                for (ErrRec e : toAdd) {
                    String msgWithSnippet = appendSnippet(e.msg, e.file, e.line);
                    errsrc.addError(e.type, e.file, e.line, e.start, e.end, msgWithSnippet);
                }
            } 
            finally {
                errorlist.ErrorSource.registerErrorSource(errsrc);
            }
        });
    }

    /******************************************************************
     */
    private static int getCheckerThreads() {
        
        try {
            String prop = org.gjt.sp.jedit.jEdit.getProperty("sumojedit.checker.threads");
            if (prop == null || prop.isBlank()) prop = System.getProperty("sumojedit.checker.threads", "");
            int v = prop.isBlank() ? 0 : Integer.parseInt(prop.trim());
            if (v <= 0) v = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
            return v;
        } 
        catch (NumberFormatException t) {
            return Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        }
    }

    /******************************************************************
     */
    private static int getKeepAliveSeconds() {

        try {
            String prop = org.gjt.sp.jedit.jEdit.getProperty("sumojedit.checker.keepAliveSec");
            if (prop == null || prop.isBlank()) prop = System.getProperty("sumojedit.checker.keepAliveSec", "30");
            return Math.max(1, Integer.parseInt(prop.trim()));
        } 
        catch (NumberFormatException t) {
            return 30;
        }
    }

    /******************************************************************
     * Create a non-EDT background Runnable with an overridden toString for
     * label display
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

    /******************************************************************
     * @return the plugin version with build number
     */
    public static String getVersionWithBuild() {

        String version = jEdit.getProperty("plugin.com.articulate.sigma.jedit.SUMOjEditPlugin.version", "1.1.0");
        String buildNum = jEdit.getProperty("build.number", "0");
        return String.format("SUMOjEdit v%s (Build %s)", version, buildNum);
    }

    /******************************************************************
     * Starts the given non-EDT Runnable in the background
     * @param r the Runnable to start
     */
    public void startBackgroundThread(Runnable r) {

        ThreadUtilities.runInBackground(r);
    }

    /******************************************************************
     * Starts the KB initialization process for UI use only. Must only be
     * called when jEdit will be an active UI. Not meant for use by the main()
     */
    @SuppressWarnings("SleepWhileInLoop")
    public void init() {

        Runnable r = () -> {
            pluginStart = System.currentTimeMillis();
            do
                try {
                    view = jEdit.getActiveView();
                    Thread.sleep(50L);
                } 
                catch (InterruptedException ex) {System.err.println(ex);}
            while (view == null);
            ThreadUtilities.runInDispatchThread(() -> {
                view.getStatus().setMessage(BuildInfo.getFullVersion() + " loading...");
            });
            System.setProperty("sigma.exec.mode", "jedit-single");
            if (view != null) {
                jEdit.setProperty("plugin.com.articulate.sigma.jedit.SUMOjEditPlugin.longdescription",
                    "A syntax aware editor for the Suggested Upper Merged Ontology (SUMO)\n" +
                    BuildInfo.getFullVersion() + "\n" +
                    "Knowledge Base loaded with " + (kb != null ? kb.terms.size() : 0) + " terms");
            }
            KButilities.refreshExecutorService();
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
            } 
            catch (Exception e) {
                Log.log(Log.ERROR, this, ":init(): KB init error: ", e);
                if (SUMOtoTFAform.kb != null) {
                    kb = SUMOtoTFAform.kb;
                    fp = SUMOtoTFAform.fp;
                }
            }
            Log.log(Log.MESSAGE, this, ":kb: " + kb);
            System.setProperty("sigma.exec.mode", "parallel");
            KButilities.refreshExecutorService();
            togglePluginMenus(true);
            if (view != null) {
                if (kb != null) {
                    autoComplete = new AutoCompleteManager(view, kb);
                    Log.log(Log.MESSAGE, this, ":AutoComplete initialized with KB");
                } 
                else {
                    KB fallbackKB = KBmanager.getMgr().getKB("SUMO");
                    if (fallbackKB != null) {
                        kb = fallbackKB;
                        autoComplete = new AutoCompleteManager(view, kb);
                        Log.log(Log.WARNING, this, ":AutoComplete initialized with fallback KB");
                    } 
                    else Log.log(Log.ERROR, this, ":No KB available for AutoComplete");
                }
            }
            if (view != null && kb == null) {
                kb = KBmanager.getMgr().getKB("SUMO");
                if (kb != null) Log.log(Log.WARNING, this, ":Using fallback SUMO KB for autocomplete");
            }
            if (view != null && kb != null) {
                if (autoComplete != null) autoComplete.dispose();
                autoComplete = new AutoCompleteManager(view, kb);
                Log.log(Log.MESSAGE, this, ":Autocomplete initialized with " + kb.terms.size() + " terms");
            }
            else {
                Log.log(Log.ERROR, this, ":Autocomplete not initialized; view=" + view + ", kb=" + kb);
            }
            isInitialized = true;
            errsrc = ensureErrorSource(view); 
            processLoadedKifOrTptp();
            ThreadUtilities.runInDispatchThread(() -> {
                view.getStatus().setMessageAndClear(BuildInfo.getFullVersion() + " ready");
            });
        };
        Runnable rs = create(r, () -> "Initializing " + getClass().getName());
        startBackgroundThread(rs);
    }

    /******************************************************************
     * Handles the UI while a KIF or TPTP file is being processed
     */
    private void processLoadedKifOrTptp() {

        if (!isInitialized) return;
        Runnable r = () -> {
            boolean isKif = Files.getFileExtension(view.getBuffer().getPath()).equalsIgnoreCase("kif");
            final String ext = Files.getFileExtension(view.getBuffer().getPath()).toLowerCase();
            final java.util.Set<String> tptpExts = new java.util.HashSet<>(java.util.Arrays.asList("tptp","p","fof","cnf","tff","thf"));
            boolean isTptpLike = tptpExts.contains(ext);
            if (isKif || isTptpLike) {
                togglePluginMenus(true);
                if (isKif) {
                    kif.filename = view.getBuffer().getPath();
                    if (kb != null && !kb.constituents.contains(kif.filename) && new File(kif.filename).length() > 1L) {
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
            } 
            else {
                ThreadUtilities.runInDispatchThread(() -> {
                    view.getEditPane().getTextArea().setRightClickPopupEnabled(false);
                });
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

    /******************************************************************
     * Disables the SUMOjEdit plugin menu items during processing of KIF
     * or TPTP. Re-enables post processing.
     * @param enabled if true, enable the plugin menus, if false, disable plugin menus
     */
    private void togglePluginMenus(boolean enabled) {

        ThreadUtilities.runInDispatchThread(() -> {
            MenuElement[] elems = view.getJMenuBar().getSubElements()[8].getSubElements()[0].getSubElements();
            for (MenuElement elem : elems) {
                if (elem instanceof EnhancedMenu) {
                    if (((EnhancedMenu) elem).getText().toLowerCase().equals(SUMOjEditPlugin.NAME)) {
                        elem.getComponent().setEnabled(enabled);
                        break;
                    }
                }
            }
            view.getEditPane().getTextArea().setRightClickPopupEnabled(enabled);
        });
    }

    /******************************************************************
     * Adds a loaded KIF as a constituent to the KB so that all terms
     * in the current jEdit buffer can be recognized. If constituent previously
     * loaded, will just return.
     */
    private void tellTheKbAboutLoadedKif() {

        if (!kb.constituents.contains(kif.filename)) {
            long start = System.currentTimeMillis();
            kb.constituents.add(kif.filename);
            kb.reload();
            kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getDefaultKbName());
            notifiedNotInKB.remove(kif.filename);
            Log.log(Log.MESSAGE, this, ":tellTheKbAboutLoadedKif() completed in " + (System.currentTimeMillis() - start) / KButilities.ONE_K + " secs");
        }
    }

    /******************************************************************
     * Props at: https://www.jedit.org/api/org/gjt/sp/jedit/msg/package-summary.html
     */
    @Override
    public void handleMessage(EBMessage msg) {

        if (msg instanceof BufferUpdate) bufferUpdate((BufferUpdate)msg);
        if (msg instanceof EditPaneUpdate) editPaneUpdate((EditPaneUpdate)msg);
        if (msg instanceof ViewUpdate) viewUpdate((ViewUpdate)msg);
    }

    /******************************************************************
     * Handler for BufferUpdate CLOSED and LOADED events
     */
    private void bufferUpdate(BufferUpdate bu) {

        if (view == null) return;
        if (bu.getView() == view && bu.getWhat() == BufferUpdate.SAVED) processLoadedKifOrTptp();
    }

    /******************************************************************
     */
    private void editPaneUpdate(EditPaneUpdate update) {
        
        if (view == null) return;
        if (update.getWhat() == EditPaneUpdate.BUFFER_CHANGED) {
            processLoadedKifOrTptp();
            if (autoComplete != null) autoComplete.refreshIndexOnBufferChange();
        }
        if (update.getWhat() == EditPaneUpdate.DESTROYED) unload();
    }

    /******************************************************************
     * Clean up resources when not needed
     */
    private void unload() {

        clearWarnAndErr();
        ErrorSource.unregisterErrorSource(errsrc);
        if (autoComplete != null) autoComplete.dispose();
    }

    
    /******************************************************************
     * Select per-View ErrorSource on ACTIVATE; clean up only when a View is CLOSED.
     */
    private void viewUpdate(ViewUpdate vu) {

        if (vu == null) return;
        if (vu.getWhat() == ViewUpdate.ACTIVATED) {
            final org.gjt.sp.jedit.View newView = vu.getView();
            if (newView != null) {
                this.view = newView;
                this.errsrc = ensureErrorSource(newView);
            }
        } 
        else if (vu.getWhat() == ViewUpdate.CLOSED) {
            final org.gjt.sp.jedit.View closed = vu.getView();
            if (closed != null) {
                final DefaultErrorSource es = viewErrorSources.remove(closed);
                if (es != null) try { errorlist.ErrorSource.unregisterErrorSource(es); } catch (Throwable ignore) {}
                if (this.view == closed) {
                    this.view = jEdit.getActiveView();
                    if (this.view != null) this.errsrc = ensureErrorSource(this.view);
                    else this.errsrc = null;
                }
            }
        }
    }

    /******************************************************************
     * Ensure the specified View has a registered ErrorSource and select it without touching others.
     */
    private void switchErrorSourceTo(final org.gjt.sp.jedit.View v) {
        this.errsrc = ensureErrorSource(v);
    }

    /******************************************************************
     * Get or create an ErrorSource for a View. Never unregister others here. 
     */
    private DefaultErrorSource ensureErrorSource(final org.gjt.sp.jedit.View v) {

        DefaultErrorSource es = viewErrorSources.get(v);
        if (es == null) {
            final String sourceName = getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(v));
            es = new DefaultErrorSource(sourceName, v);
            errorlist.ErrorSource.registerErrorSource(es);
            viewErrorSources.put(v, es);
        }
        return es;
    }

    /******************************************************************
     */
    @Override
    public void setFOF() {

        System.out.println("setFOF(): translation set to TPTP");
        Log.log(Log.MESSAGE, this, ": translation set to TPTP");
        SUMOformulaToTPTPformula.setLang("fof");
        SUMOKBtoTPTPKB.setLang("fof");
    }


    /******************************************************************
     */
    @Override
    public void setTFF() {

        System.out.println("setTFF(): translation set to TFF");
        Log.log(Log.MESSAGE, this, ": translation set to TFF");
        SUMOformulaToTPTPformula.setLang("tff");
        SUMOKBtoTPTPKB.setLang("tff");
    }


    /******************************************************************
     */
    @Override
    public void chooseVamp() {

        System.out.println("chooseVamp(): prover set to Vampire");
        Log.log(Log.MESSAGE, this, ":chooseVamp(): prover set to Vampire");
        KBmanager.getMgr().prover = KBmanager.Prover.VAMPIRE;
    }

    /******************************************************************
     */
    @Override
    public void chooseE() {

        System.out.println("chooseE(): prover set to E");
        Log.log(Log.MESSAGE, this, ":chooseE(): prover set to E");
        KBmanager.getMgr().prover = KBmanager.Prover.EPROVER;
    }

    /******************************************************************
     */
    @Override
    public void chooseLeo() {
        System.out.println("chooseLeo(): prover set to LEO");
        Log.log(Log.MESSAGE, this, ":chooseLeo(): prover set to LEO");
        KBmanager.getMgr().prover = KBmanager.Prover.LEO;
    }

    /******************************************************************
     * Configure Automated Theorem Prover (ATP) options via a single dialog.
     * This is a pure configurator. No Ask/Tell controls. Saves selections in jEdit properties.
     */
    public void configureATP() {

        final org.gjt.sp.jedit.View v = jEdit.getActiveView();
        if (v == null) return;
        String kbNameGuess = KBmanager.getMgr().getDefaultKbName();
        String kbName = jEdit.getProperty("sumojedit.atp.kb", (kbNameGuess != null && !kbNameGuess.isBlank()) ? kbNameGuess : "SUMO");
        String flang  = jEdit.getProperty("sumojedit.atp.formalLanguage", "SUO-KIF");
        int    maxAns = Math.max(1, parseIntSafe(jEdit.getProperty("sumojedit.atp.maxAnswers","1"), 1));
        int    tlim   = Math.max(1, parseIntSafe(jEdit.getProperty("sumojedit.atp.timeLimitSec","30"), 30));
        String mode   = jEdit.getProperty("sumojedit.atp.mode","fof");       // fof|tff|thf
        boolean cwa  = Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.closedWorld","false"));
        String eng    = jEdit.getProperty("sumojedit.atp.engine","vampire");  // LEO|EPROVER|VAMPIRE
        String vampM  = jEdit.getProperty("sumojedit.atp.vampire.mode","casc"); // casc|avatar|custom
        boolean mp    = Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.ModusPonens","false"));
        boolean drop1 = Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.dropOnePremise","false"));
        boolean showEn= Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.showEnglish","true"));
        boolean useLLM= Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.useLLM","false"));
        String viewOpt= jEdit.getProperty("sumojedit.atp.proofView","tptp");  // tptp|suokif|algonl|llm
        javax.swing.JPanel p = new javax.swing.JPanel(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.insets = new java.awt.Insets(2,6,2,6);
        c.gridx=0; c.gridy=0; c.anchor=java.awt.GridBagConstraints.WEST;
        p.add(new javax.swing.JLabel("KB:"), c);
        final javax.swing.JComboBox<String> kbCombo = new javax.swing.JComboBox<>(new String[]{kbName, "SUMO"});
        kbCombo.setEditable(true);
        kbCombo.setSelectedItem(kbName);
        c.gridx=1; c.fill=java.awt.GridBagConstraints.HORIZONTAL; c.weightx=1.0;
        p.add(kbCombo, c);
        c.gridx=0; c.gridy++; c.weightx=0; c.fill=java.awt.GridBagConstraints.NONE;
        p.add(new javax.swing.JLabel("Formal Language:"), c);
        final javax.swing.JComboBox<String> flangBox =
            new javax.swing.JComboBox<>(new String[]{"OWL","SUO-KIF","TPTP","traditionalLogic"});
        flangBox.setSelectedItem(flang);
        c.gridx=1; c.fill=java.awt.GridBagConstraints.HORIZONTAL; c.weightx=1.0;
        p.add(flangBox, c);
        c.gridx=0; c.gridy++; c.weightx=0; c.fill=java.awt.GridBagConstraints.NONE;
        p.add(new javax.swing.JLabel("Maximum answers:"), c);
        final javax.swing.JSpinner maxAnsSp = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(maxAns,1,1000,1));
        c.gridx=1; c.fill=java.awt.GridBagConstraints.NONE;
        p.add(maxAnsSp, c);
        c.gridx=0; c.gridy++; c.fill=java.awt.GridBagConstraints.NONE;
        p.add(new javax.swing.JLabel("Query time limit (sec):"), c);
        final javax.swing.JSpinner tlimSp = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(tlim,1,3600,1));
        c.gridx=1; p.add(tlimSp, c);
        c.gridx=0; c.gridy++; p.add(new javax.swing.JLabel("Mode:"), c);
        final javax.swing.JRadioButton rTPTP = new javax.swing.JRadioButton("fof mode", "fof".equalsIgnoreCase(mode));
        final javax.swing.JRadioButton rTFF  = new javax.swing.JRadioButton("tff mode",  "tff".equalsIgnoreCase(mode));
        final javax.swing.JRadioButton rTHF  = new javax.swing.JRadioButton("thf mode",  "thf".equalsIgnoreCase(mode));
        javax.swing.ButtonGroup gMode = new javax.swing.ButtonGroup();
        gMode.add(rTPTP); gMode.add(rTFF); gMode.add(rTHF);
        javax.swing.JPanel modeRow = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,6,0));
        modeRow.add(rTPTP); modeRow.add(rTFF); modeRow.add(rTHF);
        c.gridx=1; p.add(modeRow, c);
        c.gridx=1; c.gridy++; final javax.swing.JCheckBox cbCWA = new javax.swing.JCheckBox("Closed World Assumption", cwa);
        p.add(cbCWA, c);
        c.gridx=0; c.gridy++; p.add(new javax.swing.JLabel("Inference engine:"), c);
        final javax.swing.JRadioButton rLEO = new javax.swing.JRadioButton("LEO-III", "LEO".equalsIgnoreCase(eng));
        final javax.swing.JRadioButton rE   = new javax.swing.JRadioButton("EProver", "EPROVER".equalsIgnoreCase(eng));
        final javax.swing.JRadioButton rVam = new javax.swing.JRadioButton("Vampire", "VAMPIRE".equalsIgnoreCase(eng) || (!"LEO".equalsIgnoreCase(eng) && !"eprover".equalsIgnoreCase(eng)));
        javax.swing.ButtonGroup gEng = new javax.swing.ButtonGroup(); gEng.add(rLEO); gEng.add(rE); gEng.add(rVam);
        javax.swing.JPanel engRow = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,6,0));
        engRow.add(rLEO); engRow.add(rE); engRow.add(rVam);
        c.gridx=1; p.add(engRow, c);
        c.gridx=1; c.gridy++;
        final javax.swing.JRadioButton rCASC   = new javax.swing.JRadioButton("CASC mode",   "casc".equalsIgnoreCase(vampM));
        final javax.swing.JRadioButton rAvatar = new javax.swing.JRadioButton("Avatar mode", "avatar".equalsIgnoreCase(vampM));
        final javax.swing.JRadioButton rCustom = new javax.swing.JRadioButton("Custom mode", "custom".equalsIgnoreCase(vampM));
        javax.swing.ButtonGroup gVamp = new javax.swing.ButtonGroup(); gVamp.add(rCASC); gVamp.add(rAvatar); gVamp.add(rCustom);
        javax.swing.JPanel vampRow = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,6,0));
        vampRow.add(new javax.swing.JLabel("Vampire:"));
        vampRow.add(rCASC); vampRow.add(rAvatar); vampRow.add(rCustom);
        p.add(vampRow, c);
        c.gridx=1; c.gridy++;
        final javax.swing.JCheckBox cbMP   = new javax.swing.JCheckBox("Modus Ponens", mp);
        final javax.swing.JCheckBox cbDrop = new javax.swing.JCheckBox("Drop One-Premise Formulas", drop1);
        javax.swing.JPanel stratRow = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,6,0));
        stratRow.add(cbMP); stratRow.add(cbDrop);
        p.add(stratRow, c);
        c.gridx=1; c.gridy++;
        final javax.swing.JCheckBox cbShowEn = new javax.swing.JCheckBox("Show English Paraphrases", showEn);
        final javax.swing.JCheckBox cbUseLLM = new javax.swing.JCheckBox("Use LLM for Paraphrasing", useLLM);
        javax.swing.JPanel paraRow = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,6,0));
        paraRow.add(cbShowEn); paraRow.add(cbUseLLM);
        p.add(paraRow, c);
        c.gridx=0; c.gridy++; p.add(new javax.swing.JLabel("Proof result view:"), c);
        final javax.swing.JRadioButton vTPTP = new javax.swing.JRadioButton("TPTP language", "tptp".equalsIgnoreCase(viewOpt));
        final javax.swing.JRadioButton vKIF  = new javax.swing.JRadioButton("SUO-KIF",        "suokif".equalsIgnoreCase(viewOpt));
        final javax.swing.JRadioButton vALG  = new javax.swing.JRadioButton("Algorithmic NL", "algonl".equalsIgnoreCase(viewOpt));
        final javax.swing.JRadioButton vLLM  = new javax.swing.JRadioButton("LLM paraphrase", "llm".equalsIgnoreCase(viewOpt));
        javax.swing.ButtonGroup gView = new javax.swing.ButtonGroup(); gView.add(vTPTP); gView.add(vKIF); gView.add(vALG); gView.add(vLLM);
        javax.swing.JPanel viewRow = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,6,0));
        viewRow.add(vTPTP); viewRow.add(vKIF); viewRow.add(vALG); viewRow.add(vLLM);
        c.gridx=1; p.add(viewRow, c);
        java.awt.event.ActionListener engToggle = e -> {
            boolean ena = rVam.isSelected();
            rCASC.setEnabled(ena); rAvatar.setEnabled(ena); rCustom.setEnabled(ena);
            cbMP.setEnabled(ena);
            if (!ena) { cbMP.setSelected(false); cbDrop.setSelected(false); cbDrop.setEnabled(false); }
        };
        rLEO.addActionListener(engToggle); rE.addActionListener(engToggle); rVam.addActionListener(engToggle);
        java.awt.event.ActionListener mpToggle = e -> {
            boolean ena = cbMP.isSelected();
            cbDrop.setEnabled(ena);
            if (!ena) cbDrop.setSelected(false);
        };
        cbMP.addActionListener(mpToggle);
        engToggle.actionPerformed(null);
        mpToggle.actionPerformed(null);
        Object[] options = { "Save Preferences", "Cancel" };
        int res = javax.swing.JOptionPane.showOptionDialog(
                v, p, "Configure Automated Theorem Prover (ATP)",
                javax.swing.JOptionPane.DEFAULT_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);
        if (res == 1 || res == javax.swing.JOptionPane.CLOSED_OPTION) return;
        jEdit.setProperty("sumojedit.atp.kb",   String.valueOf(kbCombo.getSelectedItem()));
        jEdit.setProperty("sumojedit.atp.formalLanguage", String.valueOf(flangBox.getSelectedItem()));
        jEdit.setProperty("sumojedit.atp.maxAnswers", String.valueOf(((Number)maxAnsSp.getValue()).intValue()));
        jEdit.setProperty("sumojedit.atp.timeLimitSec", String.valueOf(((Number)tlimSp.getValue()).intValue()));
        jEdit.setProperty("sumojedit.atp.mode", rTHF.isSelected() ? "thf" : (rTFF.isSelected() ? "tff" : "fof"));
        jEdit.setProperty("sumojedit.atp.closedWorld", String.valueOf(cbCWA.isSelected()));
        jEdit.setProperty("sumojedit.atp.engine", rLEO.isSelected() ? "LEO" : (rE.isSelected() ? "EPROVER" : "VAMPIRE"));
        jEdit.setProperty("sumojedit.atp.vampire.mode", rAvatar.isSelected() ? "avatar" : (rCustom.isSelected() ? "custom" : "casc"));
        jEdit.setProperty("sumojedit.atp.ModusPonens", String.valueOf(cbMP.isSelected()));
        jEdit.setProperty("sumojedit.atp.dropOnePremise", String.valueOf(cbDrop.isSelected()));
        jEdit.setProperty("sumojedit.atp.showEnglish", String.valueOf(cbShowEn.isSelected()));
        jEdit.setProperty("sumojedit.atp.useLLM", String.valueOf(cbUseLLM.isSelected()));
        jEdit.setProperty("sumojedit.atp.proofView", vKIF.isSelected() ? "suokif" : (vALG.isSelected() ? "algonl" : (vLLM.isSelected() ? "llm" : "tptp")));
    }


    /******************************************************************
     */
    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return def; }
    }

    /******************************************************************
     */
    private String queryResultString(TPTP3ProofProcessor tpp) {

        StringBuilder result = new StringBuilder();
        if (tpp.bindingMap != null && !tpp.bindingMap.isEmpty())
            result.append("Bindings: ").append(tpp.bindingMap);
        else if (tpp.bindings != null && !tpp.bindings.isEmpty())
            result.append("Bindings: ").append(tpp.bindings);
        if (!StringUtil.emptyString(tpp.status)) {
            if (result.length() > 0) result.append("\n");
            result.append(tpp.status);
        }
        if (tpp.proof != null && !tpp.proof.isEmpty()) {
            java.util.List<String> proofStepsStr = new ArrayList<>();
            for (TPTPFormula ps : tpp.proof)
                proofStepsStr.add(ps.toString());
            result.append("\n\n").append(StringUtil.arrayListToCRLFString(proofStepsStr));
        }
        return result.toString();
    }

    /******************************************************************
     */
    public ATPQuery createATPQueryFromJEdit(String query) {

        String language = jEdit.getProperty("sumojedit.atp.mode", "fof");
        if ("tptp".equalsIgnoreCase(language)) language = "fof";
        return new ATPQuery(
            kb,
            String.valueOf(new java.util.Random().nextInt(10000)),
            query,
            null,
            "CUSTOM",
            jEdit.getProperty("sumojedit.atp.engine", "vampire"),
            language,
            jEdit.getProperty("sumojedit.atp.vampire.mode", "casc"),
            Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.closedWorld", "false")),
            Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.ModusPonens", "false")),
            Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.dropOnePremise", "false")),
            false,
            Math.max(1, parseIntSafe( jEdit.getProperty("sumojedit.atp.timeLimitSec", "30"), 30)),
            Math.max(1, parseIntSafe(jEdit.getProperty("sumojedit.atp.maxAnswers", "1"), 1))
        );
    }

    /******************************************************************
     */
    @Override
    public void queryExp() {

        String query = view.getTextArea().getSelectedText();
        if (!checkEditorContents(query, "Please fully highlight an atom for query")) return;
        Runnable r = () -> {
            togglePluginMenus(false);
            Log.log(Log.MESSAGE, this, ":queryExp(): query with: " + query);
            boolean showEn = Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.showEnglish", "true"));
            boolean useLLM = Boolean.parseBoolean(jEdit.getProperty("sumojedit.atp.useLLM", "false"));
            HTMLformatter.proofParaphraseInEnglish = showEn;
            LanguageFormatter.paraphraseLLM = useLLM;
            String outputText = null;
            String tmp = "0";
            try {
                TheoremProverController theoremProverController = new TheoremProverController();
                ATPQuery q = createATPQueryFromJEdit(query);
                ATPResult atpResult = theoremProverController.ask(q);
                if (atpResult != null) {
                    TPTP3ProofProcessor tpp = atpResult.getParsedProofProcessor(kb, query);
                    outputText = queryResultString(tpp);
                    if (outputText == null || outputText.isBlank()) {
                        outputText =
                            atpResult.getSummary() + "\n\n" +
                            "No parsed bindings/proof were produced.\n\n" +
                            "Raw ATP output:\n" +
                            String.join("\n", atpResult.getStdout());
                    }
                }
            }
            catch (Throwable ex) {
                outputText = "Query Failure! Check config.xml: " + tmp;
                Log.log(Log.ERROR, this, ":queryExp(): exception while running ATP", ex);
            }
            final String finalOutputText = outputText;
            ThreadUtilities.runInDispatchThread(() -> {
                jEdit.newFile(view);
                view.getTextArea().setText(finalOutputText);
            });
            Log.log(Log.MESSAGE, this, ":queryExp(): complete");
        };
        Runnable rs = create(r, () -> "Querying expression");
        startBackgroundThread(rs);
    }

    /******************************************************************
     */
    @Override
    public void browseTerm() {

        clearWarnAndErr();
        String contents = view.getTextArea().getSelectedText();
        if (!checkEditorContents(contents, "Please fully highlight a term to browse")) return;
        if (!StringUtil.emptyString(contents) && Formula.atom(contents) && kb.terms.contains(contents)) {
            String urlString = "http://sigma.ontologyportal.org:8443/sigma/Browse.jsp?kb=SUMO&lang=EnglishLanguage&flang=SUO-KIF&term=" + contents;
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(java.net.URI.create(urlString));
                } 
                catch (IOException e) {
                    Log.log(Log.ERROR, this, ":browseTerm(): ", e);
                }
            }
        }
    }

    /******************************************************************
     * @return the line number of where the error/warning begins
     */
    private int getLineNum(String line) {

        int result = -1;
        Pattern p = Pattern.compile("(\\d+):");
        Matcher m = p.matcher(line);
        if (m.find()) {
            try {
                result = Integer.parseInt(m.group(1));
            } 
            catch (NumberFormatException nfe) {}
        }
        if (result < 0) {
            p = Pattern.compile("line(:?) (\\d+)");
            m = p.matcher(line);
            if (m.find()) {
                try {
                    result = Integer.parseInt(m.group(2));
                } 
                catch (NumberFormatException nfe) {}
            }
        }
        if (result < 0 ) {
            p = Pattern.compile("line&#58; (\\d+)");
            m = p.matcher(line);
            if (m.find()) {
                try {
                    result = Integer.parseInt(m.group(1));
                } 
                catch (NumberFormatException nfe) {}
            }
        }
        if (result < 0) result = 0;
        return result;
    }

    /******************************************************************
     * sigmaAntlr generates line offsets
     * @return the line offset of where the error/warning begins
     */
    private int getOffset(String line) {

        int result = -1;
        Pattern p = Pattern.compile("\\:(\\d+)\\:");
        Matcher m = p.matcher(line);
        if (m.find()) {
            try {
                result = Integer.parseInt(m.group(1));
            } 
            catch (NumberFormatException nfe) {}
        }
        if (result < 0) result = 0;
        return result;
    }

    /******************************************************************
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
                fs.line = f.startLine - 1;
                return fs;
            }
        }
        return fs;
    }

    /******************************************************************
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

    /******************************************************************
     * Warn the user about fully highlighting a term or formula
     * @param contents the editor contents to check
     * @param msg the warning message to convey
     * @return indication of check pass/fail
     */
    private boolean checkEditorContents(String contents, String msg) {

        boolean retVal = true;
        if (contents == null || contents.isBlank() || contents.length() < 2) {
            synchronized (_pendingErrs) {
                _pendingErrs.add(new ErrRec(ErrorSource.WARNING, kif.filename, 1, 0, 0, msg));
            }
            if (log) Log.log(Log.WARNING, this, "checkEditorContents(): " + msg);
            retVal = false;
        }
        return retVal;
    }

    /******************************************************************
     */
    @Override
    public void gotoDefn() {

        clearWarnAndErr();
        String contents = view.getTextArea().getSelectedText();
        if (!checkEditorContents(contents, "Please fully highlight a term for definition")) return;
        if (StringUtil.emptyString(kif.filename)) kif.filename = view.getBuffer().getPath();
        String currentFName = FileUtil.noPath(kif.filename);
        if (!StringUtil.emptyString(contents) && Formula.atom(contents) && kb.terms.contains(contents)) {
            FileSpec result = findDefn(contents);
            Log.log(Log.MESSAGE, this, ":gotoDefn(): file:" + result.filepath + "\nline: " + (result.line+1));
            try {
                if (!FileUtil.noPath(result.filepath).equals(currentFName)) {
                    jEdit.openFile(view, result.filepath);
                    VFSManager.waitForRequests(); // <- Critical call to allow for complete Buffer loading!
                    int offset = view.getBuffer().getLineStartOffset(result.line);
                    view.getTextArea().moveCaretPosition(offset);
                }
            } 
            catch (Exception e) {
                Log.log(Log.ERROR, this, "gotoDefn()", e);
            }
        }
        else Log.log(Log.WARNING, this, "gotoDefn() term: '" + contents + "' not in the KB");
    }

    /******************************************************************
     * Performs the actual formula formatting
     * @param contents the content (formula) to format
     */
    private String formatSelectBody(String contents) {

        if (!checkEditorContents(contents, "Please highlight a formula, or CNTL+A")) return null;
        if (!parseKif(contents)) return null;
        if (StringUtil.emptyString(kif.filename)) kif.filename = view.getBuffer().getPath();
        StringBuilder result = new StringBuilder();
        for (Formula f : kif.formulaMap.values()) result.append(f);
        return result.toString();
    }

    /******************************************************************
     */
    @Override
    public void formatSelect() {

        clearWarnAndErr();
        final org.gjt.sp.jedit.Buffer buf = view.getBuffer();
        final String filePath = (buf != null ? buf.getPath() : null);
        if (isTptpFile(filePath)) {
            tptpFormatBuffer();
            return;
        }
        String contents = view.getTextArea().getSelectedText();
        String result = formatSelectBody(contents);
        if (!StringUtil.emptyString(result)) view.getTextArea().setSelectedText(result);
    }

    /******************************************************************
     * Route whole-buffer formatting to tptp4X for TPTP-like files
     */
    public void formatBuffer() {

        clearWarnAndErr();
        final org.gjt.sp.jedit.Buffer buf = view.getBuffer();
        final String filePath = (buf != null ? buf.getPath() : null);
        if (isTptpFile(filePath)) {
            tptpFormatBuffer();
            return;
        }
        String contents = view.getTextArea().getText();
        String result = formatSelectBody(contents);
        if (!StringUtil.emptyString(result)) view.getTextArea().setText(result);
    }

    /******************************************************************
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
            DefaultErrorSource.DefaultError warning = new DefaultErrorSource.DefaultError(errsrc, ErrorSource.WARNING, kif.filename, adjLine, offset, offset+1, msgWithSnippet);
            warnings.add(warning);
        }
        for (String err : kif.errorSet) {
            line = getLineNum(err);
            offset = getOffset(err);
            if (offset == 0) offset = 1;
            int adjLine = (line == 0 ? line : line - 1);
            String snip = safeSnippetFromFile(kif.filename, adjLine);
            String msgWithSnippet = snip.isEmpty() ? err : (err + " — " + snip);
            DefaultErrorSource.DefaultError error = new DefaultErrorSource.DefaultError(errsrc, ErrorSource.ERROR, kif.filename, adjLine, offset, offset+1, msgWithSnippet);
            errors.add(error);
        }
        ThreadUtilities.runInDispatchThread(() -> {
            errorlist.ErrorSource.unregisterErrorSource(errsrc);
            try {
                for (DefaultErrorSource.DefaultError warning : warnings) errsrc.addError(warning);
                for (DefaultErrorSource.DefaultError error : errors) errsrc.addError(error); 
            } 
            finally {
                errorlist.ErrorSource.registerErrorSource(errsrc);
            }
        });
    }

    /******************************************************************
     * Clears the KIF instance collections to include warnings and errors
     */
    private void clearKif() {

        kif.warningSet.clear();
        kif.errorSet.clear();
        kif.filename = "";
        kif.formulaMap.clear();
        kif.formulas.clear();
        kif.termFrequency.clear();
        kif.terms.clear();
    }

    /******************************************************************
     * Clears out all warnings and errors in both the ErrorList and
     * SigmaKEE trees.
     */
    private void clearWarnAndErr() {

        ThreadUtilities.runInBackground(() -> {
            if (!StringUtil.emptyString(kif.filename)) {
                errsrc.removeFileErrors(kif.filename);
            }
            jEdit.getAction("error-list-clear").invoke(view);
            errsrc.clear();
            clearKif();
            KButilities.clearErrors();
            if (kb != null) {
                kb.errors.clear();
                kb.warnings.clear();
            }
            FormulaPreprocessor.errors.clear();
            SUMOtoTFAform.errors.clear();
        });
    }

    /******************************************************************
     */
    @Override
    public void showStats() {

        clearWarnAndErr();
        Log.log(Log.MESSAGE, this, ":showStats(): starting");
        if (StringUtil.emptyString(kif.filename))
            kif.filename = view.getBuffer().getPath();
        String contents = view.getTextArea().getText();
        if (!parseKif(contents)) return;
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
                    } 
                    else thisNoPath = "";
                }
                else thisNoPath = FileUtil.noPath(defn.filepath);
                if (thisNoPath.equals(filename) || StringUtil.emptyString(thisNoPath)) {
                    Log.log(Log.MESSAGE, this, ":showStats(): ******* in this file: " + t);
                    stats.append("******* in this file: ").append(t).append('\n');
                    termCount++;
                } 
                else if (!Formula.isLogicalOperator(t)) otherTermCount++;
            }
            Log.log(Log.MESSAGE, this, ":showStats(): # terms: " + termCount);
            stats.append("# terms: ").append(termCount).append('\n');
            Log.log(Log.MESSAGE, this, ":showStats(): # terms used from other files: " + otherTermCount);
            stats.append("# terms used from other files: ").append(otherTermCount).append('\n');
            Log.log(Log.MESSAGE, this, ":showStats(): # axioms: " + kif.formulaMap.keySet().size());
            stats.append("# axioms: ").append(kif.formulaMap.keySet().size()).append('\n');
            int ruleCount = 0;
            for (Formula f : kif.formulaMap.values()) if (f.isRule()) ruleCount++;
            Log.log(Log.MESSAGE, this, ":showStats(): # rules: " + ruleCount);
            stats.append("# rules: ").append(ruleCount).append('\n');
            Log.log(Log.MESSAGE, this, ":showStats(): done reading kif file");
        } 
        catch (Exception e) {
            if (log) Log.log(Log.ERROR, this, ":showStats()",e);
            String msg = "Error in SUMOjEdit.showStats() with: " + kif.filename + ": " + e;
            System.err.println(msg);
        }
        jEdit.newFile(view);
        view.getTextArea().setSelectedText(stats.toString());
        Log.log(Log.MESSAGE, this, ":showStats(): complete");
    }

    /******************************************************************
     */
    private void clearErrorsForFile(DefaultErrorSource source, String filePath) {

        ThreadUtilities.runInDispatchThreadAndWait(() -> {
            if (source != null && filePath != null) source.removeFileErrors(filePath);
        });
    }

    /******************************************************************
     */
    @Override
    public void checkErrors() {

        Log.log(Log.MESSAGE, this, ":checkErrors(): starting");
        final View targetView = jEdit.getActiveView();
        if (targetView == null || targetView.getBuffer() == null) return;
        final DefaultErrorSource targetSource = ensureErrorSource(targetView);
        final String filePath = targetView.getBuffer().getPath();
        final String contents = targetView.getTextArea().getText();
        clearErrorsForFile(targetSource, filePath);
        startBackgroundThread(create(() -> {
            List<ErrRec> errors;
            if (isTptpFile(filePath)) errors = normalizeTptpErrorsForJEdit(TPTPFileChecker.check(contents, filePath));
            else errors = KifFileChecker.check(contents, filePath);
            addErrorsDirect(errors);
            Log.log(
                    Log.MESSAGE,
                    this,
                    ":checkErrors(): found "
                            + errors.size()
                            + " diagnostics");
        }, () -> "Checking errors"));
    }

    /******************************************************************
     * Utility method to parse KIF
     * @param contents the contents of a KIF file to parse
     * @return true if successful parse, no error or warnings
     */
    private boolean parseKif(String contents) {

        boolean retVal = false;
        try (Reader r = new StringReader(contents)) {
            kif.parse(r);
            Log.log(Log.MESSAGE, this, ":parseKif(): done reading kif file");
            retVal = kif.warningSet.isEmpty() && kif.errorSet.isEmpty();
        } 
        catch (Exception e) {
            if (log) Log.log(Log.ERROR, this, ":checkErrorsBody()", e);
            String msg = "Error in SUMOjEdit.parseKif() with: " + kif.filename + ": " + e;
            System.err.print(msg);
            retVal = false;
        } 
        finally {
            logKifWarnAndErr();
        }
        return retVal;
    }

    /******************************************************************
     * Check for a variety of syntactic and semantic errors and warnings in a
     * given buffer
     * @param contents the SUO-KIF to check
     */
    protected void checkErrorsBody(String contents, final String filePath) {

        List<ErrRec> msgs = KifFileChecker.check(contents, filePath);
        if (filePath != null) {
            boolean hasNotInKBWarning = msgs.stream().anyMatch(
                e -> e.type == ErrRec.WARNING && e.msg != null
                    && e.msg.startsWith("This file is not loaded into the KB"));
            if (hasNotInKBWarning) {
                if (notifiedNotInKB.contains(filePath)) {
                    msgs.removeIf(e -> e.type == ErrRec.WARNING && e.msg != null
                        && e.msg.startsWith("This file is not loaded into the KB"));
                } 
                else {
                    notifiedNotInKB.add(filePath);
                }
            }
        }
        addErrorsDirect(msgs);
    }

    /******************************************************************
     * Backward-compatible shim: delegate to the 2-arg version.
     */
    protected void checkErrorsBody(String contents) {

        String fn = (this.kif != null && !StringUtil.emptyString(this.kif.filename)) ? this.kif.filename : "untitled.kif";
        checkErrorsBody(contents, fn);
    }

    /******************************************************************
     */ 
    private void addErrorsDirect(List<ErrRec> errors) {

        if (errors == null || errors.isEmpty()) return;
        errors.sort(Comparator.comparingInt((ErrRec e) -> e.line).thenComparingInt(e -> e.start));
        final DefaultErrorSource targetSource = errsrc;
        final View targetView = view;
        final Buffer targetBuffer = targetView == null ? null : targetView.getBuffer();
        ThreadUtilities.runInDispatchThread(() -> {
            for (ErrRec e : errors) {
                int line = Math.max(0, e.line);
                int start = Math.max(0, e.start);
                int end = Math.max(start + 1, e.end);
                if (targetBuffer != null) {
                    int maxLine = Math.max(0, targetBuffer.getLineCount() - 1);
                    line = Math.min(line, maxLine);
                    int lineLength = targetBuffer.getLineLength(line);
                    start = Math.min(start, lineLength);
                    end = Math.min(Math.max(start + 1, end), lineLength);
                }
                targetSource.addError(e.type, e.file, line, start, end, appendSnippet(e.msg, e.file, line));
            }
            if (targetView != null) targetView.getDockableWindowManager().showDockableWindow("error-list");
        });
    }

    /******************************************************************
     */
    private void addErrors(List<ErrRec> errors, DefaultErrorSource targetSource, View targetView) {

        if (errors == null || errors.isEmpty()) return;
        List<ErrRec> sortedErrors = new ArrayList<>(errors);
        sortedErrors.sort(Comparator
            .comparingInt((ErrRec error) -> error.line)
            .thenComparingInt(error -> error.start));
        final Buffer targetBuffer = targetView == null ? null : targetView.getBuffer();
        ThreadUtilities.runInDispatchThread(() -> {
            for (ErrRec error : sortedErrors) {
                int line = Math.max(0, error.line);
                int start = Math.max(0, error.start);
                int end = Math.max(start + 1, error.end);
                if (targetBuffer != null && targetBuffer.getLineCount() > 0) {
                    int lastLine = targetBuffer.getLineCount() - 1;
                    line = Math.min(line, lastLine);
                    int lineLength = targetBuffer.getLineLength(line);
                    start = Math.min(start, lineLength);
                    if (end <= start + 1 && lineLength > 0) end = tokenEnd(targetBuffer, line, start);
                    else end = Math.min(end, lineLength);
                    end = lineLength == 0 ? 0 : Math.min(Math.max(start + 1, end), lineLength);
                }
                targetSource.addError(error.type, error.file, line, start, end, appendSnippet(error.msg, error.file, line));
            }
            if (targetView != null) targetView.getDockableWindowManager().showDockableWindow("error-list");
        });
    }

    /******************************************************************
     */
    private static int tokenEnd(Buffer buffer, int line, int start) {
        
        int length = buffer.getLineLength(line);
        String text = buffer.getLineText(line);
        int end = Math.min(start + 1, length);
        while (end < length) {
            char c = text.charAt(end);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) break;
            end++;
        }
        return end;
    }

    /******************************************************************
     * Find all occurrences of a term in the buffer and report errors for each
     */
        private void reportAllOccurrencesInBuffer(final String filePath, String term, String errorMessage, String[] bufferLines, int errorType) {
            
            final int n = bufferLines.length;
            final int chunk = Math.max(50, n / Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
            final java.util.List<java.util.concurrent.Callable<java.util.List<ErrRec>>> tasks = new java.util.ArrayList<>();
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
                java.util.List<java.util.concurrent.Future<java.util.List<ErrRec>>> futures = CHECKER_POOL.invokeAll(tasks);
                java.util.List<ErrRec> merged = new java.util.ArrayList<>();
                for (java.util.concurrent.Future<java.util.List<ErrRec>> f : futures) merged.addAll(f.get());
                addErrorsBatch(merged);
            } 
            catch (InterruptedException | ExecutionException ex) {
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

    /******************************************************************
     * Find a term in a line with word boundary checking
     */
    private int findTermInLine(String line, String term, int startPos) {
        
        int pos = line.indexOf(term, startPos);
        while (pos != -1) {
            boolean validStart = (pos == 0 || !isTermChar(line.charAt(pos - 1)));
            boolean validEnd = (pos + term.length() >= line.length() || !isTermChar(line.charAt(pos + term.length())));
            if (validStart && validEnd) return pos;
            pos = line.indexOf(term, pos + 1);
        }
        return -1;
    }

    /******************************************************************
     * Check if a character can be part of a term
     */
    private boolean isTermChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_';
    }

    /******************************************************************
     * Find where a formula appears in the buffer
     */
    private int findFormulaInBuffer(String formulaStr, String[] bufferLines) {
        
        String[] formulaLines = formulaStr.split("\n");
        String firstLine = "";
        for (String line : formulaLines) {
            if (!line.trim().isEmpty()) {
                firstLine = line.trim();
                break;
            }
        }
        if (firstLine.isEmpty()) return -1;
        for (int i = 0; i < bufferLines.length; i++) {
            if (bufferLines[i].contains(firstLine)) {
                return i;
            }
        }
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

    /******************************************************************
     * If an error message contains a formula "(...)", try to locate that formula in the buffer
     * and return the correct 0-based line number. Returns -1 if not resolvable.
     */
    private int resolveLineFromMessage(org.gjt.sp.jedit.Buffer buf, String msg) {
        
        if (buf == null || msg == null) return -1;
        int lc = buf.getLineCount();
        String[] bufferLines = new String[lc];
        for (int i = 0; i < lc; i++) bufferLines[i] = buf.getLineText(i);
        String candidate = extractInFormulaAtom(msg);
        if (candidate != null && !candidate.isEmpty()) {
            int hit = findFormulaInBuffer(candidate, bufferLines);
            if (hit >= 0) return hit;
        }
        int dash = msg.indexOf(" — ");
        if (dash >= 0 && dash + 3 < msg.length()) {
            String rhs = msg.substring(dash + 3).trim();
            if (rhs.startsWith("(")) candidate = rhs;
        } 
        else {
            candidate = null;
        }
        if (candidate == null) {
            int paren = msg.indexOf('(');
            if (paren >= 0) candidate = msg.substring(paren).trim();
        }
        if (candidate == null || candidate.isEmpty()) return -1;
        return findFormulaInBuffer(candidate, bufferLines);
    }

    /******************************************************************
     */
    private static String extractInFormulaAtom(String msg) {
        if (msg == null) return "";
        int k = msg.indexOf("in formula:");
        if (k < 0) return "";
        String tail = msg.substring(k + "in formula:".length()).trim();
        int p = tail.indexOf('(');
        if (p < 0) return "";
        tail = tail.substring(p).trim();
        int close = tail.indexOf(')');
        if (close > 0) return tail.substring(0, close + 1).trim();
        return tail; 
    }

    /******************************************************************
     */
    private static String extractRelationNameFP(String msg) {

        if (msg == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("for\\s+arg\\s+\\d+\\s+of\\s+relation\\s+([^\\s]+)\\s+in\\s+formula\\s*:", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(msg);
        return m.find() ? m.group(1).trim() : "";
    }

    /******************************************************************
     */
    @Override
    public void toTPTP() {

        clearWarnAndErr();
        Log.log(Log.MESSAGE, this, ":toTPTP(): starting");
        if (StringUtil.emptyString(kif.filename)) kif.filename = view.getBuffer().getPath();
        String contents = view.getTextArea().getText();
        String selected = view.getTextArea().getSelectedText();
        if (!StringUtil.emptyString(selected)) contents = selected;
        if (!parseKif(contents)) return;
        StringBuilder sb = new StringBuilder();
        try {
            java.util.List<Formula> ordered = kif.lexicalOrder();
            String /*pred,*/ tptpStr;
            TPTPVisitor sv;
            Map<String, TPTPFormula> hm;
            for (Formula f : ordered) {
                if (f.isHigherOrder(kb) || (f.predVarCache != null && !f.predVarCache.isEmpty())) continue;
                tptpStr = "fof(f4434,axiom," + SUMOformulaToTPTPformula.process(f, false) + ",[file('kb_" + f.getSourceFile() + "_" + f.startLine + "',unknown)]).";
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

    /******************************************************************
     */
    @Override
    public void fromTPTP() {

        clearWarnAndErr();
        Log.log(Log.MESSAGE, this, ":fromTPTP(): starting");
        String contents = view.getTextArea().getText();
        String selected = view.getTextArea().getSelectedText();
        if (!StringUtil.emptyString(selected)) contents = selected;
        if (StringUtil.emptyString(kif.filename)) kif.filename = view.getBuffer().getPath();
        try {
            TPTPVisitor sv = new TPTPVisitor();
            if (new File(kif.filename).exists()) sv.parseFile(kif.filename);
            else sv.parseString(contents);
            Map<String, TPTPFormula> hm = sv.result;
            jEdit.newFile(view);
            StringBuilder result = new StringBuilder();
            for (String s : hm.keySet()) result.append(hm.get(s).formula).append("\n\n");
            view.getTextArea().setText(result.toString());
            if (StringUtil.emptyString(result)) Log.log(Log.WARNING, this, ":fromTPTP(): empty result");
            else Log.log(Log.MESSAGE, this, ":fromTPTP(): result.length: " + result.length());
        } 
        catch (Exception e) {
            if (log) Log.log(Log.ERROR, this, ":fromTPTP()", e);
            String msg = "Error in SUMOjEdit.fromTPTP() with: " + kif.filename + ": " + e;
            System.err.println(msg);
        }
        Log.log(Log.MESSAGE, this, ":fromTPTP(): complete");
    }

    /** Menu action entrypoint. Called by actions.xml via SUMOjEditPlugin.sje.autoComplete() */
    @Override
    public void autoComplete() {
        
        String mode = jEdit.getProperty("sumojedit.ac.mode", "BOTH");
        boolean dropdownEnabled = "DROPDOWN_ONLY".equals(mode) || "BOTH".equals(mode);
        if (!dropdownEnabled) return;
        final org.gjt.sp.jedit.View v = jEdit.getActiveView();
        if (v == null) return;
        final org.gjt.sp.jedit.textarea.JEditTextArea ta = v.getTextArea();
        if (ta == null) return;
        SimpleCompletionPopup.show(ta);
    }

    /******************************************************************
     */
    private static String stripTailAfterPercentDash(String s) {

        if (s == null) return "";
        return s.replaceFirst("\\s+—\\s+%.*$", "").replaceFirst("\\s+%.*$", "").trim();
    }

    /******************************************************************
     */
    private static boolean isTptpFile(String path) {

        if (path == null) return false;
        int i = path.lastIndexOf('.');
        if (i < 0) return false;
        String ext = path.substring(i+1).toLowerCase(java.util.Locale.ROOT);
        return TPTP_EXTS.contains(ext);
    }

    /******************************************************************
     */
    @Override
    public void tptpFormatBuffer() {

        final View targetView = jEdit.getActiveView();
        if (targetView == null || targetView.getBuffer() == null) return;
        final Buffer targetBuffer = targetView.getBuffer();
        final DefaultErrorSource targetSource = ensureErrorSource(targetView);
        final String bufferPath = targetBuffer.getPath();
        final String filePath = bufferPath != null && !bufferPath.isBlank() ? bufferPath : targetBuffer.getName();
        if (!isTptpFile(filePath)) {
            addErrors(
                List.of(new ErrRec(ErrorSource.WARNING, filePath, 0, 0, 1, "TPTP formatting is only available for " + ".tptp, .p, .fof, .cnf, .tff, " + "and .thf files.")),
                targetSource,
                targetView);
            return;
        }
        final JEditTextArea textArea = targetView.getTextArea();
        final Selection selection = textArea.getSelectionCount() > 0 ? textArea.getSelection(0) : null;
        final int selectionStart = selection == null ? textArea.getCaretPosition() : selection.getStart();
        final int selectionEnd = selection == null ? selectionStart : selection.getEnd();
        final boolean hasSelection = selection != null;
        final String textToFormat = hasSelection ? targetView.getTextArea().getSelectedText() : targetView.getTextArea().getText();
        if (textToFormat == null || textToFormat.isBlank()) {
            addErrors(List.of(new ErrRec(ErrorSource.WARNING, filePath, 0, 0, 1, "There is no TPTP text to format.")), targetSource, targetView);
            return;
        }
        clearErrorsForFile(targetSource, filePath);
        startBackgroundThread(create(() -> {
            try {
                String formatted =
                    TPTPFileChecker.formatTptpText(textToFormat, filePath);
                if (formatted == null) {
                    addErrors(List.of(new ErrRec(ErrorSource.ERROR, filePath, 0, 0, 1, "TPTP formatting returned no output.")), targetSource, targetView);
                    return;
                }
                ThreadUtilities.runInDispatchThread(() -> {
                    if (hasSelection) {
                        targetBuffer.remove(selectionStart, selectionEnd - selectionStart);
                        targetBuffer.insert(selectionStart, formatted);
                    }
                    else targetView.getTextArea().setText(formatted);
                });
                final String textToCheck;
                if (hasSelection) {
                    String original = targetView.getTextArea().getText();
                    textToCheck = original.substring(0, selectionStart) + formatted + original.substring(selectionEnd);
                }
                else textToCheck = formatted;
                List<ErrRec> diagnostics = normalizeTptpErrorsForJEdit(TPTPFileChecker.check(textToCheck, filePath));
                addErrors(diagnostics, targetSource, targetView);
                Log.log(Log.MESSAGE, this, ":tptpFormatBuffer(): found " + diagnostics.size() + " diagnostics");
            }
            catch (Throwable throwable) {
                Log.log(Log.ERROR, this, ":tptpFormatBuffer()", throwable);
                addErrors(List.of(new ErrRec(ErrorSource.ERROR, filePath, 0, 0, 1, "TPTP formatting failed: " + errorMessage(throwable))), targetSource, targetView);
            }
        }, () -> "Formatting TPTP"));
    }

    /******************************************************************
     */
    private static String errorMessage(Throwable throwable) {

        if (throwable == null) return "Unknown error";
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    /******************************************************************
     */
    public void tptpCheckBuffer() {

        final View targetView = jEdit.getActiveView();
        if (targetView == null || targetView.getBuffer() == null) return;
        final Buffer targetBuffer = targetView.getBuffer();
        final DefaultErrorSource targetSource = ensureErrorSource(targetView);
        final String bufferPath = targetBuffer.getPath();
        final String filePath = bufferPath != null && !bufferPath.isBlank() ? bufferPath : targetBuffer.getName();
        if (!isTptpFile(filePath)) {
            addErrors(
                List.of(new ErrRec(
                    ErrorSource.WARNING,
                    filePath,
                    0,
                    0,
                    1,
                    "TPTP checking is only available for "
                        + ".tptp, .p, .fof, .cnf, .tff, "
                        + "and .thf files.")),
                    targetSource,
                    targetView);
            return;
        }

        final String contents = targetView.getTextArea().getText();
        clearErrorsForFile(targetSource, filePath);
        startBackgroundThread(create(() -> {
            try {
                List<ErrRec> diagnostics = normalizeTptpErrorsForJEdit(TPTPFileChecker.check(contents, filePath));
                addErrors(diagnostics, targetSource, targetView);
                Log.log(Log.MESSAGE, this, ":tptpCheckBuffer(): found " + diagnostics.size() + " diagnostics");
            }
            catch (Throwable throwable) {
                Log.log(Log.ERROR, this, ":tptpCheckBuffer()", throwable);
                addErrors(
                    List.of(new ErrRec(ErrorSource.ERROR, filePath, 0, 0, 1, "TPTP checking failed: " + errorMessage(throwable))),
                    targetSource,
                    targetView);
            }
        }, () -> "Checking TPTP"));
    }

    /******************************************************************
     */
    private static List<ErrRec> normalizeTptpErrorsForJEdit(List<ErrRec> errors) {

        if (errors == null || errors.isEmpty()) return Collections.emptyList();
        List<ErrRec> normalized = new ArrayList<>(errors.size());
        for (ErrRec error : errors) {
            int line = error.line > 0 ? error.line - 1 : 0;
            normalized.add(new ErrRec(error.type, error.file, line, error.start, error.end, error.msg));
        }
        return normalized;
    }

    /******************************************************************
     */
    public static void showHelp() {

        System.out.println("Diagnostics");
        System.out.println("  options:");
        System.out.println("  -h - show this help screen");
        System.out.println("  -d - <fname> - test diagnostics");
        System.out.println("  -q - run a default query");
    }

    /******************************************************************
     * Test method for this class.
     * @param args command line arguments
     */
    public static void main(String args[]) {

        System.out.println("INFO: In SUMOjEdit.main()");
        SUMOjEdit sje = null;
        KB kb = null;
        if (args != null && args.length > 0 && args[0].equals("-h")) showHelp();
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
        } else showHelp();
    }
}