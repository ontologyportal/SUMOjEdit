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

import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.articulate.sigma.*;
import com.articulate.sigma.trans.SUMOtoTFAform;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.PropertiesChanged;
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

	private static final long serialVersionUID = 6412255692894321789L;

	public View view = null;
	public KB kb = null;
	public FormulaPreprocessor fp = null;

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
	public void checkErrors() {

		if (view == null)
			view = jEdit.getActiveView();
		Log.log(Log.WARNING,this,"checkErrors(): starting");
		errorlist.DefaultErrorSource errsrc;
		errsrc = new errorlist.DefaultErrorSource("sigmakee",view);
		errorlist.ErrorSource.registerErrorSource(errsrc);
		jEdit.getAction("error-list-clear").invoke(null);
		//errsrc.addError(ErrorSource.ERROR, "C:\\my_projects\\hw_if\\control\\ctrlapi.c",944,0,0,"LNT787: (Info -- enum constant 'DTV_PL_ASIG_AV_IP1_AUDIO' not used within switch)");

		String contents = view.getEditPane().getTextArea().getText();
		String path = view.getBuffer().getPath();

		KIF kif = new KIF();
		//kif.filename = "/home/apease/workspace/sumo/Merge.kif";
		try {
			kif.parse(new StringReader(contents));
			Log.log(Log.WARNING,this,"checkErrors(): done reading kif file");
		}
		catch (Exception e) {
			Log.log(Log.WARNING,this,"checkErrors(): error loading kif file");
			errsrc.addError(ErrorSource.WARNING,e.getMessage(),1,0,0,
				"error loading kif file with " + contents.length() + " characters "); }

		Log.log(Log.WARNING,this,"checkErrors(): success loading kif file with " + contents.length() + " characters ");
		Log.log(Log.WARNING,this,"checkErrors(): filename: " + path);

		for (String warn : kif.warningSet) {
			int line = getLineNum(warn);
			errsrc.addError(ErrorSource.WARNING,path,line,0,0,warn);
			Log.log(Log.WARNING,this,line);
		}
		for (String err : kif.errorSet) {
			int line = getLineNum(err);
			errsrc.addError(ErrorSource.ERROR,path,line,0,0,err);
			Log.log(Log.WARNING,this,line);
		}
		for (Formula f : kif.formulaMap.values()) {
			//Log.log(Log.WARNING,this,"checking formula " + f.toString());
			if (Diagnostics.quantifierNotInStatement(f))
				errsrc.addError(ErrorSource.ERROR,path,f.startLine,0,0,
						"Quantifier not in statement");
			HashSet<String> result = Diagnostics.singleUseVariables(f);
			if (result != null && result.size() > 0)
				errsrc.addError(ErrorSource.WARNING,path,f.startLine,0,0,
						"Variable(s) only used once: " + result.toString());
			fp.preProcess(f,false,kb);
			if (f.errors != null && f.errors.size() > 0) {
				for (String err : f.errors)
					errsrc.addError(ErrorSource.ERROR,path,f.startLine,0,0,err);
				for (String w : f.warnings)
					errsrc.addError(ErrorSource.WARNING,path,f.startLine,0,0,w);
			}
			//Log.log(Log.WARNING,this,"checking variables in formula ");
			HashMap<String,HashSet<String>> varmap = fp.findAllTypeRestrictions(f,kb);
			//Log.log(Log.WARNING,this,"varmap " + varmap);
			SUMOtoTFAform.varmap = varmap;
			SUMOtoTFAform.inconsistentVarTypes();
			//Log.log(Log.WARNING,this,"done checking var types ");
			if (SUMOtoTFAform.errors != null && f.errors.size() > 0) {
				for (String err : SUMOtoTFAform.errors) {
					errsrc.addError(ErrorSource.ERROR, path, f.startLine, 0, 0, err);
					Log.log(Log.WARNING, this, err);
				}
			}
			String term = PredVarInst.hasCorrectArity(f, kb);
			if (!StringUtil.emptyString(term)) {
				String msg = ("Arity error of predicate " + term);
				errsrc.addError(ErrorSource.ERROR, path, f.startLine, 0, 0, msg);
				Log.log(Log.WARNING, this, msg);
			}
		}
		Log.log(Log.WARNING,this,"checkErrors(): check completed: ");
	}
}
