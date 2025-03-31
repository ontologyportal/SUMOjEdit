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

import errorlist.DefaultErrorSource;
import errorlist.ErrorListPanel;
import errorlist.ErrorSource;

import java.awt.*;
import java.io.*;
import javax.swing.Box;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.gui.RolloverButton;
import org.gjt.sp.jedit.io.VFSManager;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.msg.EditorExiting;
import org.gjt.sp.jedit.msg.VFSUpdate;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.util.Log;

import tptp_parser.*;

/**
 * ***************************************************************
 *
 */
public class SUMOjEdit implements EBComponent, SUMOjEditActions, Runnable {

    public static boolean log = true;

    protected FormulaPreprocessor fp;
    protected KB kb;

    private final KIF kif;

    private DefaultErrorSource errsrc;
    private DefaultErrorSource.DefaultError dw;
    private DefaultErrorSource.DefaultError de;
    private View view;
    private boolean kbsInitialized;

    /**
     * ***************************************************************
     * Initializes this plugin and loads the KBs
     */
    public SUMOjEdit() {

        kbsInitialized = false;

        Log.log(Log.MESSAGE, SUMOjEdit.this, ": SUMOKBtoTPTPKB.rapidParsing==" + SUMOKBtoTPTPKB.rapidParsing);
        Log.log(Log.MESSAGE, SUMOjEdit.this, ": initializing");

        kif = new KIF();
        kif.filename = "";
    }

    /**
     * ***************************************************************
     * Starts the given named Runnable
     * @param r the Runnable to start
     * @param desc a short description to give the Thread instance
     */
    public void startThread(Runnable r, String desc) {

        Thread t = new Thread(r);
        t.setName(SUMOjEdit.class.getSimpleName() + ": " + desc);
        t.setDaemon(true);
        t.start();
    }

    /* Starts the KB initialization process for UI use only. Must only be
     * called when jEdit will be an active UI. Not meant for use by the main()
     */
    @Override
    @SuppressWarnings("SleepWhileInLoop")
    public void run() {

        // wait for the view to become active
        do
            try {
                view = jEdit.getActiveView();
                Thread.sleep(50L);
            } catch (InterruptedException ex) {System.err.println(ex);}
        while (view == null);
        view.getJMenuBar().getSubElements()[8].menuSelectionChanged(true); // toggle the Plugins menu to populate all Plugin items

        // Disable the plugin's menus
        // Top view menu bar / Enhanced menu item / Plugins menu / SUMOjEdit plugin menu
        view.getJMenuBar().getSubElements()[8].getSubElements()[0].getSubElements()[3].getComponent().setEnabled(kbsInitialized);

        // Now, the right click context menu of the editor's text area in the case of customized SUMOjEdit actions
        view.getEditPane().getTextArea().setRightClickPopupEnabled(kbsInitialized);

        // Will first initialize the KB
        SUMOtoTFAform.initOnce();
        kb = SUMOtoTFAform.kb;
        kbsInitialized = (kb != null);
        view.getJMenuBar().getSubElements()[8].menuSelectionChanged(false);

        // Now, re-enable the plugin's menus
        view.getJMenuBar().getSubElements()[8].getSubElements()[0].getSubElements()[3].getComponent().setEnabled(kbsInitialized);
        view.getEditPane().getTextArea().setRightClickPopupEnabled(kbsInitialized);

        fp = SUMOtoTFAform.fp;
        Log.log(Log.MESSAGE, SUMOjEdit.this, ": kb: " + kb);

        // Initialize error plugin sources
        errsrc = new DefaultErrorSource(getClass().getName(), this.view);
        errorlist.ErrorSource.registerErrorSource(errsrc);
    }

    /**
     * ***************************************************************
     * Props at: https://www.jedit.org/api/org/gjt/sp/jedit/msg/package-summary.html
     * @param msg the Edit Bus message to handle
     */
    @Override
    public void handleMessage(EBMessage msg) {

        if (msg instanceof BufferUpdate)
            bufferUpdate((BufferUpdate)msg);
        else if (msg instanceof VFSUpdate)
            vfsUpdate((VFSUpdate)msg);
        else if (msg instanceof ViewUpdate)
            viewUpdate((ViewUpdate)msg);
        else if (msg instanceof EditorExiting)
            editorExiting((EditorExiting)msg);
        else if (msg instanceof EditPaneUpdate)
            editPaneUpdate((EditPaneUpdate)msg);
    }

    /**
     * ***************************************************************
     */
    private void bufferUpdate(BufferUpdate bu) {

        if (bu.getView() == view)
            if (bu.getWhat() == BufferUpdate.DIRTY_CHANGED)
                System.out.println("DIRTY_CHANGED"); // file saved, or changed
    }

    /**
     * ***************************************************************
     */
    private void vfsUpdate(VFSUpdate vu) {

        System.out.println("VFS update"); // file saved
    }

    private void viewUpdate(ViewUpdate vu) {
        if (vu.getView() == view)
            if (vu.getWhat() == ViewUpdate.CLOSED)
                System.out.println("ViewUpdate.CLOSED, unloading"); // file saved, or changed

    }

    /**
     * ***************************************************************
     */
    private void editorExiting(EditorExiting ee) {

        System.out.println("Editor exiting, unloading");
        unload();
    }

    /**
     * ***************************************************************
     */
    private void editPaneUpdate(EditPaneUpdate eu) {

        if (eu.getWhat() == EditPaneUpdate.BUFFERSET_CHANGED)
            System.out.println("BUFFERSET_CHANGED");
        else if (eu.getWhat() == EditPaneUpdate.BUFFER_CHANGED)
            System.out.println("BUFFER_CHANGED"); // switching between files or panes and closing panes
    }

    /**
     * ***************************************************************
     * Clean up resources upon shutdown
     */
    private void unload() {
        ErrorSource.unregisterErrorSource(errsrc);
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

    /**
     * ***************************************************************
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
        jEdit.newFile(view);
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

        String contents = view.getEditPane().getTextArea().getSelectedText();
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
            //System.out.println("queryExp(): completed query with result: " + StringUtil.arrayListToCRLFString(vamp.output));
            //Log.log(Log.WARNING,this,"queryExp(): completed query with result: " + StringUtil.arrayListToCRLFString(vamp.output));
        }
        if (KBmanager.getMgr().prover == KBmanager.Prover.EPROVER) {
            eprover = kb.askEProver(contents, 30, 1);
            try {
                //System.out.println("queryExp(): completed query with result: " + StringUtil.arrayListToCRLFString(eprover.output));
                //Log.log(Log.WARNING,this,"queryExp(): completed query with result: " + StringUtil.arrayListToCRLFString(eprover.output));
                tpp.parseProofOutput(eprover.output, contents, kb, eprover.qlist);
                qlist = eprover.qlist;
            } catch (Exception e) {
                Log.log(Log.ERROR, this, ":queryExp(): error " + Arrays.toString(e.getStackTrace()));
            }
        }
        tpp.processAnswersFromProof(qlist, contents);
        view.getTextArea().setText(queryResultString(tpp));
    }

    @Override
    public void browseTerm() {

        String contents = view.getEditPane().getTextArea().getSelectedText();
        if (!StringUtil.emptyString(contents) && Formula.atom(contents)
                && kb.terms.contains(contents)) {
            String urlString = "http://sigma.ontologyportal.org:8443/sigma/Browse.jsp?kb=SUMO&lang=EnglishLanguage&flang=SUO-KIF&term="
                    + contents;
            if (Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(java.net.URI.create(urlString));
                } catch (IOException e) {
                    Log.log(Log.ERROR, this, ":browseTerm(): error " + Arrays.toString(e.getStackTrace()));
                }
            }
        }
    }

    /**
     * ***************************************************************
     * @return the line number of where the error/warning begins
     */
    private int getLineNum(String line) {

        int result = 0;
        Pattern p = Pattern.compile("line (\\d+)");
        Matcher m = p.matcher(line);
        if (m.find()) {
//            Log.log(Log.MESSAGE, this, ":getLineNum(): found line number: " + m.group(1));
            try {
                result = Integer.parseInt(m.group(1));
            } catch (NumberFormatException nfe) {}
        }
        if (result == 0) {
            p = Pattern.compile("line&#58; (\\d+)");
            m = p.matcher(line);
            if (m.find()) {
//                Log.log(Log.MESSAGE, this, ":getLineNum(): found line number: " + m.group(1));
                try {
                    result = Integer.parseInt(m.group(1));
                } catch (NumberFormatException nfe) {}
            }
        }
        if (result < 0)
            result = 0;
        return result;
    }

    /**
     * ***************************************************************
     * @return the line offset of where the error/warning begins
     */
    private int getOffset(String line) {

        int result = 0;
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

    /**
     * ***************************************************************
     * Utility class that contains searched term line and filepath information
     */
    public class FileSpec {

        public String filepath = "";
        public int line = -1;
    }

    /**
     * ***************************************************************
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

    /**
     * ***************************************************************
     * Note that the "definition" of a term is collection of axioms so look for,
     * in order: instance, subclass, subAttribute, subrelation, domain, documentation
     * @param term the term to search for
     * @return a FileSpec with searched term info
     */
    private FileSpec findDefn(String term) {

        String currentPath = view.getBuffer().getPath();
        String currentFName = FileUtil.noPath(currentPath);
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
        return new FileSpec(); // nothing found
    }

    @Override
    public void gotoDefn() {

        String currentPath = view.getBuffer().getPath();
        String currentFName = FileUtil.noPath(currentPath);
        String contents = view.getEditPane().getTextArea().getSelectedText();
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
                    view.getEditPane().getTextArea().moveCaretPosition(offset);
                }
            } catch (Exception e) {
                Log.log(Log.ERROR, this, "gotoDefn()", e);
            }
        }
        else
            Log.log(Log.WARNING, this, "gotoDefn() term: '" + contents + "' not in the KB");
    }

    /**
     * ***************************************************************
     * Performs the actual formula formatting
     * @param contents the content (formula) to format
     * @param path the path of the file containing the formula
     */
    private String formatSelectBody(String contents, String path) {

        if (contents == null || contents.isBlank() || contents.length() < 2) {

            String msg = "Please highlight a formula, or CNTL+A";
            errsrc.addError(ErrorSource.WARNING, path, 1, 0, 0, msg);
            if (log)
                Log.log(Log.WARNING, this, "formatSelectBody(contents): " + msg);
            return null; // user fix before continuing
        }
        kif.filename = this.view.getBuffer().getPath();
        try (StringReader r = new StringReader(contents)) {
            kif.parse(r);
        } catch (Exception e) {
            Log.log(Log.ERROR, this, ":formatSelect()", e);
            return null;
        } finally {
            if (!kif.warningSet.isEmpty() || !kif.errorSet.isEmpty()) {
                logKifWarnAndErr();
                if (log)
                    Log.log(Log.ERROR, this, ":formatSelect(): error loading kif file: "
                        + kif.errorSet);
                return null; // user fix before continuing
            }
        }
        Log.log(Log.MESSAGE, this, ":formatSelect(): done reading kif file");
        StringBuilder result = new StringBuilder();
        for (Formula f : kif.formulasOrdered.values())
            result.append(f);
        return result.toString();
    }

    @Override
    public void formatSelect() {

        clearWarnAndErr();
        String contents = view.getEditPane().getTextArea().getSelectedText();
        String path = view.getBuffer().getPath();
        String result = formatSelectBody(contents, path);
        if (!StringUtil.emptyString(result))
            view.getEditPane().getTextArea().setSelectedText(result);
    }

    /**
     * ***************************************************************
     * Pass any KIF parse warnings and/or errors to the ErrorList Plugin. Also
     * any general warnings or errors (future capability)
     */
    private void logKifWarnAndErr() {

        int line, offset;
        for (String warn : kif.warningSet) {

            line = getLineNum(warn);
            offset = getOffset(warn);
            errsrc.addError(ErrorSource.WARNING, kif.filename, line == 0 ? line : line-1, offset, offset+1, warn);
        }
        for (String err : kif.errorSet) {

            line = getLineNum(err);
            offset = getOffset(err);
            errsrc.addError(ErrorSource.ERROR, kif.filename, line == 0 ? line : line-1, offset, offset+1, err);
        }

        // Not currently used, but good framework to have
        if (dw != null && (!dw.getErrorMessage().isBlank() || dw.getExtraMessages().length > 0))
            errsrc.addError(dw);
        if (de != null && (!de.getErrorMessage().isBlank() || de.getExtraMessages().length > 0))
            errsrc.addError(de);
    }

    /**
     * ***************************************************************
     * Clears the KIF instance collections to include warnings and errors
     */
    private void clearKif() {

        kif.errorSet.clear();
        kif.filename = "";
        kif.formulaMap.clear();
        kif.formulas.clear();
        kif.formulasOrdered.clear();
        kif.termFrequency.clear();
        kif.terms.clear();
        kif.warningSet.clear();
    }

    private void clearWarnAndErr() {

        if (!StringUtil.emptyString(kif.filename))
            errsrc.removeFileErrors(kif.filename);

        // Redundant calls if the clear button is also invoked
        jEdit.getAction("error-list-clear").invoke(view);
        errsrc.clear();
        // But good to invoke if the ErrorList is not yet visible

        clearKif();
        KButilities.clearErrors();
        kb.errors.clear();
        kb.warnings.clear();
        FormulaPreprocessor.errors.clear();
        SUMOtoTFAform.errors.clear();

        // Toggles the ErrorList sweep (clean) button to clear errors
        // Components of the ErrorListPanel:
        // a Box on top of a JScrollPane. We want the Box
        Container c = view.getDockableWindowManager().getDockable("error-list");

        if (c != null) { // can happen if ErrorList is not visible (selected)
            ErrorListPanel elp = (ErrorListPanel) c.getComponents()[0];

            // Get ErrorListPanel dimensions
            double elpWidth = elp.getWidth();
            double elpHeight = elp.getHeight();

            // Scale the coordinates. ErrorListPanel's (0, 0) starts at upper left corner
            // ELP dimensions are 1055x236 - Box coords are 1055x36
            double scaledX = ((elpWidth/2) * elpWidth) / elp.getBounds().width;
            double scaledY = ((36/2) * elpHeight) / elp.getBounds().height;

            // Get the Box at the scaled coordinates
            Box box = (Box) elp.getComponentAt((int) scaledX, (int) scaledY);
            RolloverButton btn = (RolloverButton) box.getComponents()[13];
            btn.doClick(); // click the clear btn
        }
    }

    @Override
    public void showStats() {

        clearWarnAndErr();
        Log.log(Log.MESSAGE, this, ":showStats(): starting");
        kif.filename = view.getBuffer().getPath();
        String contents = view.getEditPane().getTextArea().getText();
        String path = kif.filename;
        Log.log(Log.MESSAGE, this, ":showStats(): path: " + path);
        String filename = FileUtil.noPath(path);
        StringBuilder stats = new StringBuilder();
        try (Reader r = new StringReader(contents)) {
            kif.parse(r);
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
                    Log.log(Log.WARNING, this, ":showStats(): no definition found for: " + t);
                    continue;
                }
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
            try (Writer sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw)) {
                e.printStackTrace(pw);
                if (log)
                    Log.log(Log.ERROR, this, ":showStats()",e);
                String msg = "error loading kif file with " + contents.length() + " characters";
                errsrc.addError(ErrorSource.ERROR, kif.filename, 1, 0, 0, msg);
            } catch (IOException ex) {}
        } finally {
            logKifWarnAndErr();
        }
        jEdit.newFile(view);
        view.getEditPane().getTextArea().setSelectedText(stats.toString());
        Log.log(Log.MESSAGE, this, ":showStats(): complete");
    }

    @Override
    public void checkErrors() {

        clearWarnAndErr();
        Log.log(Log.MESSAGE, this, ":checkErrors(): starting");
        String path = view.getBuffer().getPath();
        kif.filename = path;
        String contents = view.getEditPane().getTextArea().getText();
        checkErrorsBody(contents, path);
        Log.log(Log.MESSAGE, this, ":checkErrors(): complete");
    }

    /**
     * ***************************************************************
     * Check for a variety of syntactic and semantic errors and warnings in a
     * given buffer
     * @param contents the content (SUO-KIF) to check
     * @param path the path of the file containing SUO-KIF to check
     */
    protected void checkErrorsBody(String contents, String path) {

        try (Reader r = new StringReader(contents)) {
            kif.parse(r);
            Log.log(Log.MESSAGE, this, ":checkErrorsBody(): done reading kif file");
        } catch (Exception e) {
            if (log)
                Log.log(Log.ERROR, this, ":checkErrorsBody()", e);
            String msg = "error loading kif file: " + path + " with " + contents.length() + " characters";
            errsrc.addError( ErrorSource.ERROR, kif.filename, 1, 0, 0, msg);
        } finally {
            if (!kif.warningSet.isEmpty() || !kif.errorSet.isEmpty()) {
                logKifWarnAndErr();
                return; // user fix before continuing
            }
        }

        Log.log(Log.MESSAGE, this, ":checkErrorsBody(): success loading kif file with " + contents.length() + " characters");
        Log.log(Log.MESSAGE, this, ":checkErrorsBody(): filename: " + path);

            int counter = 0;
            Set<String> nbeTerms = new HashSet<>();
            Set<String> unkTerms = new HashSet<>();
            Set<String> result, unquant, terms;
            Set<Formula> processed;
            String err, term, msg;
            java.util.List<Formula> forms;
            SuokifVisitor sv;
            for (Formula f : kif.formulaMap.values()) {
                Log.log(Log.MESSAGE, this, ":checkErrorsBody(): check formula:\n " + f);
                counter++;
                if (counter > 1000) {
                    Log.log(Log.NOTICE, this, ".");
                    counter = 0;
                }
                // Check for syntax errors that KIF parse didn't catch
                sv = SuokifApp.process(Formula.textFormat(f.getFormula()));
                if (!sv.errors.isEmpty()) {
                    int line, offset;
                    for (String er : sv.errors) {
                        line = getLineNum(er);
                        offset = getOffset(er);
                        errsrc.addError(ErrorSource.ERROR, kif.filename, line == 0 ? line : line-1, offset, offset+1, er);
                    }
                    return; // user fix before continuing
                }
                //Log.log(Log.WARNING,this,"checking formula " + f.toString());
                if (Diagnostics.quantifierNotInStatement(f))
                    errsrc.addError(ErrorSource.ERROR, path, f.startLine-1, f.endLine-1,0,"Quantifier not in statement");
                result = Diagnostics.singleUseVariables(f);
                if (result != null && !result.isEmpty())
                    errsrc.addError(ErrorSource.WARNING, path, f.startLine-1, f.endLine-1,0, "Variable(s) only used once: " + result.toString());
                processed = fp.preProcess(f, false, kb);
                if (f.errors != null && !f.errors.isEmpty()) {
                    for (String er : f.errors)
                        errsrc.addError(ErrorSource.ERROR, path, f.startLine-1, f.endLine-1, 0, er);
                    for (String w : f.warnings)
                        errsrc.addError(ErrorSource.WARNING, path, f.startLine-1, f.endLine-1,0,w);
                }
                //Log.log(Log.WARNING,this,"checking variables in formula ");
                if (!KButilities.isValidFormula(kb, f.toString())) {
                    for (String er : KButilities.errors) {
                        errsrc.addError(ErrorSource.ERROR, path, f.startLine-1, f.endLine-1, 0, er);
                        if (log)
                            Log.log(Log.ERROR, this, er);
                    }
                }
                //Log.log(Log.WARNING,this,"done checking var types ");

                unquant = Diagnostics.unquantInConsequent(f);
                if (!unquant.isEmpty()) {
                    err = "Unquantified var(s) " + unquant + " in consequent";
                    errsrc.addError(ErrorSource.ERROR, path, f.startLine-1, f.endLine-1, 0, err);
                    if (log)
                        Log.log(Log.WARNING, this, err);
                }

                // note that predicate variables can result in many relations being tried that don't
                // fit because of type inconsistencies, which then are rejected and not a formalization error
                // so ignore those cases (of size()>1)
                if (SUMOtoTFAform.errors != null && !f.errors.isEmpty() && processed.size() == 1) {
                    for (String er : SUMOtoTFAform.errors) { // <- We might already have these from KButilities (tdn)
                        errsrc.addError(ErrorSource.ERROR, path, f.startLine-1, f.endLine-1, 0, er);
                        if (log)
                            Log.log( Log.ERROR, this, er);
                    }
                }
                term = PredVarInst.hasCorrectArity(f, kb);
                if (!StringUtil.emptyString(term)) {
                    msg = ("Arity error of predicate " + term);
                    errsrc.addError(ErrorSource.ERROR, path, f.startLine-1, f.endLine-1, 0, msg);
                    if (log)
                        Log.log(Log.ERROR, this, msg);
                }
                terms = f.collectTerms();
                Log.log(Log.MESSAGE, this, ":checkErrorsBody(): # terms in formula: " + terms.size());
                for (String t : terms) {
                    if (Diagnostics.LOG_OPS.contains(t) || t.equals("Entity")
                            || Formula.isVariable(t) || StringUtil.isNumeric(t) || StringUtil.isQuotedString(t)) {
                        continue;
                    } else {
                        forms = kb.askWithRestriction(0, "instance", 1, t);
                        if (forms == null || forms.isEmpty()) {
                            forms = kb.askWithRestriction(0, "subclass", 1, t);
                            if (forms == null || forms.isEmpty()) {
                                forms = kb.askWithRestriction(0, "subAttribute", 1, t);
                                if (forms == null || forms.isEmpty()) {
                                    if (!unkTerms.contains(t))
                                        errsrc.addError(ErrorSource.WARNING, path, f.startLine-1, f.endLine-1, 0, "unknown term: " + t);
                                    unkTerms.add(t);
                                    if (log)
                                        Log.log(Log.WARNING, this, "unknown term: " + t);
                                }
                            }
                        }
                    }
                    if (Diagnostics.termNotBelowEntity(t, kb) && !nbeTerms.contains(t)) {
                        errsrc.addError(ErrorSource.ERROR, path, f.startLine-1, f.endLine-1, 0, "term not below entity: " + t);
                        nbeTerms.add(t);
                    }
                }
            }
    }

    @Override
    public void toTPTP() {

        clearWarnAndErr();
        Log.log(Log.MESSAGE, this, ":toTPTP(): starting");
        kif.filename = this.view.getBuffer().getPath();
        String contents = view.getEditPane().getTextArea().getText();
        String selected = view.getEditPane().getTextArea().getSelectedText();
        if (!StringUtil.emptyString(selected))
            contents = selected;
        StringBuilder sb = new StringBuilder();
        try (Reader r = new StringReader(contents)) {
            kif.parse(r);
            //Log.log(Log.WARNING,this,"toTPTP(): done reading kif file");
            java.util.List<Formula> ordered = kif.lexicalOrder();
            String pred, tptpStr;
            TPTPVisitor sv;
            Map<String, TPTPFormula> hm;
            for (Formula f : ordered) {
                //Log.log(Log.WARNING,this,"toTPTP(): SUO-KIF: " + f.getFormula());
                pred = f.car();
                //Log.log(Log.WARNING,this,"toTPTP pred: " + pred);
                //Log.log(Log.WARNING,this,"toTPTP kb: " + kb);
                //Log.log(Log.WARNING,this,"toTPTP kb cache: " + kb.kbCache);
                //Log.log(Log.WARNING,this,"toTPTP gather pred vars: " + PredVarInst.gatherPredVars(kb,f));
                //if (f.predVarCache != null && f.predVarCache.size() > 0)
                //	Log.log(Log.WARNING,this,"toTPTP Formula.isHigherOrder(): pred var cache: " + f.predVarCache);
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
            try (Writer sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw)) {
                e.printStackTrace(pw);
                String msg = "error loading kif file with " + contents.length() + " characters";
                errsrc.addError(ErrorSource.ERROR, kif.filename, 1, 0, 0, msg);
            } catch (IOException ex) {}
        } finally {
            logKifWarnAndErr();
        }
    }

    @Override
    public void fromTPTP() {

        clearWarnAndErr();
        Log.log(Log.MESSAGE, this, ":fromTPTP(): starting");
        String contents = view.getEditPane().getTextArea().getText();
        String selected = view.getEditPane().getTextArea().getSelectedText();
        if (!StringUtil.emptyString(selected))
            contents = selected;
        String path = view.getBuffer().getPath();
        try {
            TPTPVisitor sv = new TPTPVisitor();
            if (new File(path).exists())
                sv.parseFile(path);
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
            try (Writer sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw)) {
                e.printStackTrace(pw);
                String msg = "error loading kif file with " + contents.length() + " characters";
                errsrc.addError( ErrorSource.ERROR, kif.filename, 1, 0, 0, msg);
            } catch (IOException ex) {}
        }
        Log.log(Log.MESSAGE, this, ":fromTPTP(): complete");
    }

    /**
     * ***************************************************************
     */
    public static void showHelp() {

        System.out.println("Diagnostics");
        System.out.println("  options:");
        System.out.println("  -h - show this help screen");
        System.out.println("  -d - <fname> - test diagnostics");
        System.out.println("  -q - run a default query");
    }

    /**
     * ***************************************************************
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
        }

        if (args != null && args.length > 1 && args[0].equals("-d")) {
            String contents = String.join("\n", FileUtil.readLines(args[1], false));
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
}
