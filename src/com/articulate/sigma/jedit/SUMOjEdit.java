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

import java.awt.*;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.articulate.sigma.*;
import com.articulate.sigma.tp.*;
import com.articulate.sigma.tp.Vampire;
import com.articulate.sigma.trans.SUMOtoTFAform;
import com.articulate.sigma.utils.FileUtil;
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.msg.PropertiesChanged;
import org.gjt.sp.jedit.textarea.TextArea;
import org.gjt.sp.util.Log;

import errorlist.*;

/** ***************************************************************
 *
 */
public class SUMOjEdit
    implements
		EBComponent,
		SUMOjEditActions
 	{

	public View view = null;
	public KB kb = null;
	public FormulaPreprocessor fp = null;
	public static boolean log = true;
	public static boolean darkMode = true;

	/** ***************************************************************
	 * @param view the current jedit window
	 */
	public SUMOjEdit(View view) {

		//super(new BorderLayout());
		Log.log(Log.WARNING,this,"SUMOjEdit(): initializing");
		KBmanager.getMgr().initializeOnce();
		kb = KBmanager.getMgr().getKB("SUMO");
		fp = new FormulaPreprocessor();
		SUMOtoTFAform.initOnce();
		this.view = view;
	}

	/** ***************************************************************
	 */
	public void handleMessage(EBMessage message) {

		if (message instanceof PropertiesChanged) {
			propertiesChanged();
		}
	}

	/** ***************************************************************
	 */
	private void propertiesChanged() {

	}

	/** ***************************************************************
	 * Send a highlighted expression as a query to a theorem prover.
	 * return results in a new tab
	 */
	public void queryExp() {

		if (view == null)
			view = jEdit.getActiveView();
		String contents = view.getEditPane().getTextArea().getSelectedText();
		Log.log(Log.WARNING,this,"queryExp(): query Vampire with: " + contents);
		System.out.println("queryExp(): query Vampire with: " + contents);
		String dir = KBmanager.getMgr().getPref("kbDir") + File.separator;
		String type = "tptp";
		String outfile = dir + "temp-comb." + type;
		System.out.println("queryExp(): query Vampire on file: " + outfile);
		Log.log(Log.WARNING,this,"queryExp(): query Vampire on file: " + outfile);
		Vampire vamp = kb.askVampire(contents,30,1);
		jEdit.newFile(view);
		view.getTextArea().setText(StringUtil.arrayListToCRLFString(vamp.output));
	}

	/** ***************************************************************
	 * Open up a browser on the public Sigma for the highlighted term.
	 * If it's not a term, or not in the KB, don't open
	 */
	public void browseTerm() {

		if (view == null)
			view = jEdit.getActiveView();
		String contents = view.getEditPane().getTextArea().getSelectedText();
		if (!StringUtil.emptyString(contents) && Formula.atom(contents) &&
				kb.terms.contains(contents)) {
			String urlString = "http://sigma.ontologyportal.org:8080/sigma/Browse.jsp?kb=SUMO&lang=EnglishLanguage&flang=SUO-KIF&term=" +
					contents;
			if (Desktop.isDesktopSupported()) {
				try {
					Desktop.getDesktop().browse(java.net.URI.create(urlString));
				} catch (Exception e) {
					Log.log(Log.WARNING,this,"browseTerm(): error " + e.getMessage() + "\n" + e.getStackTrace());
				}
			}
		}
	}

	/** ***************************************************************
	 */
	public int getLineNum(String line) {

		int result = 0;
		Pattern p = Pattern.compile("line: (\\d+)");
		Matcher m = p.matcher(line);
		if (m.find()) {
			Log.log(Log.WARNING,this,"getLineNum(): found line number: " + m.group(1));
			try { result = Integer.parseInt(m.group(1)); }
			catch (NumberFormatException nfe) {}
		}
		if (result == 0) {
			p = Pattern.compile("line&58; (\\d+)");
			m = p.matcher(line);
			if (m.find()) {
				Log.log(Log.WARNING,this,"getLineNum(): found line number: " + m.group(1));
				try {
					result = Integer.parseInt(m.group(1));
				}
				catch (NumberFormatException nfe) {
				}
			}
		}
		return result;
	}

    /** ***************************************************************
     */
	public class FileSpec {
	    public String filepath = "";
	    public int line = -1;
    }

    /** ***************************************************************
     */
    private FileSpec filespecFromForms(ArrayList<Formula> forms, String currentFName) {

        FileSpec fs = new FileSpec();
        for (Formula f : forms) {
            if (FileUtil.noPath(f.getSourceFile()).equals(currentFName) && !f.getSourceFile().endsWith("_Cache.kif")) {
                fs.filepath = f.sourceFile;
                fs.line = f.startLine-1; // jedit starts from 0, SUMO starts from 1
                return fs;
            }
        }
		for (Formula f : forms) {
			if (!f.getSourceFile().endsWith("_Cache.kif")) {
				fs.filepath = f.sourceFile;
				fs.line = f.startLine-1; // jedit starts from 0, SUMO starts from 1
				return fs;
			}
		}
		return null;
    }

    /** ***************************************************************
     * Note that the "definition" of a term is collection of axioms
     * so look for, in order, instance, subclass, subAttribute, domain, documentation
     */
    private FileSpec findDefn(String term) {

        String currentPath = view.getBuffer().getPath();
        String currentFName = FileUtil.noPath(currentPath);
        FileSpec fs = new FileSpec();
        ArrayList<Formula> forms = kb.askWithRestriction(0,"instance",1,term);
        if (forms != null && forms.size() > 0)
            return(filespecFromForms(forms,currentFName));
        forms = kb.askWithRestriction(0,"subclass",1,term);
        if (forms != null && forms.size() > 0)
            return(filespecFromForms(forms,currentFName));
        forms = kb.askWithRestriction(0,"subAttribute",1,term);
        if (forms != null && forms.size() > 0)
            return(filespecFromForms(forms,currentFName));
        forms = kb.askWithRestriction(0,"subrelation",1,term);
        if (forms != null && forms.size() > 0)
            return(filespecFromForms(forms,currentFName));
        forms = kb.askWithRestriction(0,"domain",1,term);
        if (forms != null && forms.size() > 0)
            return(filespecFromForms(forms,currentFName));
        forms = kb.askWithRestriction(0,"documentation",1,term);
        if (forms != null && forms.size() > 0)
            return(filespecFromForms(forms,currentFName));
        return fs;
    }

    /** ***************************************************************
     * Go to the "definition" of a selected term.  If no term is selected
     * do nothing other than print an error to the console.  If definition
     * is in another file, load that file.
     */
    public void gotoDefn() {

        if (view == null)
            view = jEdit.getActiveView();
        String currentPath = view.getBuffer().getPath();
        String currentFName = FileUtil.noPath(currentPath);
        String contents = view.getEditPane().getTextArea().getSelectedText();
        if (!StringUtil.emptyString(contents) && Formula.atom(contents) &&
                kb.terms.contains(contents)) {
            FileSpec result = findDefn(contents);
			Log.log(Log.WARNING,this,"gotoDefn(): file:" +
					result.filepath + "\n" + result.line);
            if (result != null) {
            	int offset = -1;
                if (!FileUtil.noPath(result.filepath).equals(currentFName)) {
                    jEdit.openFile(view,result.filepath);
                    try { wait(1000); } catch(Exception e) {}
                }
                offset = view.getBuffer().getLineStartOffset(result.line);
				Log.log(Log.WARNING,this,"gotoDefn(): offset:" + offset);
                view.getEditPane().getTextArea().moveCaretPosition(offset);
            }
        }
    }

	/** ***************************************************************
	 */
	private String formatSelectBody(String contents) {

		KIF kif = new KIF();
		//kif.filename = "/home/apease/workspace/sumo/Merge.kif";
		try {
			kif.parse(new StringReader(contents));
			Log.log(Log.WARNING,this,"checkErrors(): done reading kif file");
		}
		catch (Exception e) {
			Log.log(Log.WARNING,this,"checkErrors(): error loading kif file" +
					e.getMessage() + "\n" + e.getStackTrace());
			return null;
		}
		StringBuffer result = new StringBuffer();
		for (Formula f : kif.formulaMap.values()) {
			result.append(f.textFormat(f.getFormula()));
		}
		return result.toString();
	}

	/** ***************************************************************
	 * Reformat a selection of SUO-KIF axioms in a buffer.  In case
	 * of error, such as a selection that only spans part of an axiom,
	 * do nothing.
	 */
	public void formatSelect() {

		if (view == null)
			view = jEdit.getActiveView();
		String contents = view.getEditPane().getTextArea().getSelectedText();
		String result = formatSelectBody(contents);
		if (!StringUtil.emptyString(result))
			view.getEditPane().getTextArea().setSelectedText(result);
	}

	/** ***************************************************************
	 * Check for a variety of syntactic and semantic errors and warnings
	 * in a given buffer
	 */
	public void checkErrors() {

		if (view == null)
			view = jEdit.getActiveView();
		Log.log(Log.WARNING, this, "checkErrors(): starting");
		errorlist.DefaultErrorSource errsrc;
		errsrc = new errorlist.DefaultErrorSource("sigmakee", view);
		errorlist.ErrorSource.registerErrorSource(errsrc);
		jEdit.getAction("error-list-clear").invoke(null);
		//errsrc.addError(ErrorSource.ERROR, "C:\\my_projects\\hw_if\\control\\ctrlapi.c",944,0,0,"LNT787: (Info -- enum constant 'DTV_PL_ASIG_AV_IP1_AUDIO' not used within switch)");

		String contents = view.getEditPane().getTextArea().getText();
		String path = view.getBuffer().getPath();
		checkErrorsBody(contents, path, errsrc);
		Log.log(Log.WARNING, this, "checkErrors(): complete");
	}

	/** ***************************************************************
	 */
	public void checkErrorsBody(String contents, String path, errorlist.DefaultErrorSource errsrc) {

		KIF kif = new KIF();
		//kif.filename = "/home/apease/workspace/sumo/Merge.kif";
		try {
			kif.parse(new StringReader(contents));
			Log.log(Log.WARNING,this,"checkErrors(): done reading kif file");
		}
		catch (Exception e) {
			Log.log(Log.WARNING,this,"checkErrors(): error loading kif file");
			if (log) errsrc.addError(ErrorSource.WARNING,e.getMessage(),1,0,0,
				"error loading kif file with " + contents.length() + " characters "); }

		Log.log(Log.WARNING,this,"checkErrors(): success loading kif file with " + contents.length() + " characters ");
		Log.log(Log.WARNING,this,"checkErrors(): filename: " + path);

		for (String warn : kif.warningSet) {
			int line = getLineNum(warn) - 1 ;
			if (log) errsrc.addError(ErrorSource.WARNING,path,line,0,0,warn);
			Log.log(Log.WARNING,this,line);
		}
		for (String err : kif.errorSet) {
			int line = getLineNum(err) - 1;
			if (log) errsrc.addError(ErrorSource.ERROR,path,line,0,0,err);
			Log.log(Log.WARNING,this,line);
		}
		int counter = 0;
		for (Formula f : kif.formulaMap.values()) {
			counter++;
			if (counter > 1000) {
				Log.log(Log.WARNING,this,".");
				counter = 0;
			}
			//Log.log(Log.WARNING,this,"checking formula " + f.toString());
			if (Diagnostics.quantifierNotInStatement(f))
				if (log) errsrc.addError(ErrorSource.ERROR,path,f.startLine-1,f.endLine-1,0,
						"Quantifier not in statement");
			HashSet<String> result = Diagnostics.singleUseVariables(f);
			if (result != null && result.size() > 0)
				if (log) errsrc.addError(ErrorSource.WARNING,path,f.startLine-1,f.endLine-1,0,
						"Variable(s) only used once: " + result.toString());
			Set<Formula> processed = fp.preProcess(f,false,kb);
			if (f.errors != null && f.errors.size() > 0) {
				for (String err : f.errors)
					if (log) errsrc.addError(ErrorSource.ERROR,path,f.startLine-1,f.endLine-1,0,err);
				for (String w : f.warnings)
					if (log) errsrc.addError(ErrorSource.WARNING,path,f.startLine-1,f.endLine-1,0,w);
			}
			//Log.log(Log.WARNING,this,"checking variables in formula ");
			HashMap<String,HashSet<String>> varmap = fp.findAllTypeRestrictions(f,kb);
			//Log.log(Log.WARNING,this,"varmap " + varmap);
			SUMOtoTFAform.varmap = varmap;
			SUMOtoTFAform.inconsistentVarTypes();
			//Log.log(Log.WARNING,this,"done checking var types ");

            // note that predicate variables can result in many relations being tried that don't
            // fit because of type inconsistencies, which then are rejected and not a formalization error
            // so ignore those cases (of size()>1)
			if (SUMOtoTFAform.errors != null && f.errors.size() > 0 && processed.size() == 1) {
				for (String err : SUMOtoTFAform.errors) {
					if (log) errsrc.addError(ErrorSource.ERROR, path, f.startLine-1, f.endLine-1, 0, err);
					Log.log(Log.WARNING, this, err);
				}
			}
			String term = PredVarInst.hasCorrectArity(f, kb);
			if (!StringUtil.emptyString(term)) {
				String msg = ("Arity error of predicate " + term);
				if (log) errsrc.addError(ErrorSource.ERROR, path, f.startLine-1, f.endLine-1, 0, msg);
				Log.log(Log.WARNING, this, msg);
			}
			Set<String> terms = f.termCache;
			for (String t : terms) {
				if (Diagnostics.LOG_OPS.contains(t) || t.equals("Entity") ||
						Formula.isVariable(t) || StringUtil.isNumeric(t) || StringUtil.isQuotedString(t))
					continue;
				else {
					ArrayList<Formula> forms = kb.askWithRestriction(0,"instance",1,t);
					if (forms == null || forms.size() == 0) {
						forms = kb.askWithRestriction(0,"subclass",1,t);
						if (forms == null || forms.size() == 0) {
							forms = kb.askWithRestriction(0, "subAttribute", 1, t);
							if (forms == null || forms.size() == 0) {
								if (log) errsrc.addError(ErrorSource.ERROR, path, f.startLine - 1, f.endLine - 1, 0,
										"unknown term: " + t);
								Log.log(Log.WARNING, this, "unknown term: " + t);
							}
						}
					}
					/*
					if (!kb.kbCache.subclassOf(t,"Entity") && !kb.kbCache.transInstOf(t,"Entity")) {
						if (log) errsrc.addError(ErrorSource.ERROR, path, f.startLine-1, f.endLine-1, 0,
                                "unknown term: " + t);
						Log.log(Log.WARNING, this, "unknown term: " + t);
					} */
				}
			}
		}
		Log.log(Log.WARNING,this,"checkErrors(): check completed: ");
	}

	/** ***************************************************************
	 */
	public static void showHelp() {

		System.out.println("Diagnostics");
		System.out.println("  options:");
		System.out.println("  -h - show this help screen");
		System.out.println("  -d - <fname> - test diagnostics");
	}

	/** ***************************************************************
	 * Test method for this class.
	 */
	public static void main(String args[]) {

		log = false;
		KBmanager.getMgr().initializeOnce();
		//resultLimit = 0; // don't limit number of results on command line
		KB kb = KBmanager.getMgr().getKB(KBmanager.getMgr().getPref("sumokbname"));
		if (args != null && args.length > 1 && args[0].equals("-d")) {
			errorlist.DefaultErrorSource errsrc;
			errsrc = new errorlist.DefaultErrorSource("sigmakee");
			errorlist.ErrorSource.registerErrorSource(errsrc);
			SUMOjEdit sje = new SUMOjEdit(null);
			String contents = String.join("\n",FileUtil.readLines(args[1],false));
			sje.checkErrorsBody(contents,args[1],errsrc);
		}
		else if (args != null && args.length > 0 && args[0].equals("-h")) {
			showHelp();
		}
		else
			showHelp();
	}
}
