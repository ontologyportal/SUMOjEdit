package com.articulate.sigma.jedit;

/*
 * QuickNotepad.java
 * part of the QuickNotepad plugin for the jEdit text editor
 * Copyright (C) 2001 John Gellene
 * jgellene@nyc.rr.com
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
 *
 * $Id$
 */

// {{{ imports
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.*;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.articulate.sigma.KBmanager;
import com.articulate.sigma.KIF;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.gui.DockableWindowManager;
import org.gjt.sp.jedit.msg.PropertiesChanged;
import org.gjt.sp.util.Log;
import org.gjt.sp.util.StandardUtilities;

import errorlist.*;

/** ***************************************************************
 *
 */
public class QuickNotepad extends JPanel
    implements EBComponent, QuickNotepadActions, DefaultFocusComponent {

    // {{{ Instance Variables
	private static final long serialVersionUID = 6412255692894321789L;

	private String filename;

	private String defaultFilename;

	private View view;

	private boolean floating;

	private QuickNotepadTextArea textArea;

	private QuickNotepadToolPanel toolPanel;

	/** ***************************************************************
	 * @param view the current jedit window
	 * @param position a variable passed in from the script in actions.xml,
	 * 	which can be DockableWindowManager.FLOATING, TOP, BOTTOM, LEFT, RIGHT, etc.
	 * 	see @ref DockableWindowManager for possible values.
	 */
	public QuickNotepad(View view, String position) {

		super(new BorderLayout());
		KBmanager.getMgr().initializeOnce();
		this.view = view;
		this.floating = position.equals(DockableWindowManager.FLOATING);

		if (jEdit.getSettingsDirectory() != null) {
			this.filename = jEdit.getProperty(QuickNotepadPlugin.OPTION_PREFIX
					+ "filepath");
			if (this.filename == null || this.filename.length() == 0) {
				this.filename = new String(jEdit.getSettingsDirectory()
						+ File.separator + "qn.txt");
				jEdit.setProperty(
						QuickNotepadPlugin.OPTION_PREFIX + "filepath",
						this.filename);
			}
			this.defaultFilename = this.filename;
		}

		this.toolPanel = new QuickNotepadToolPanel(this);
		add(BorderLayout.NORTH, this.toolPanel);

		if (floating)
			this.setPreferredSize(new Dimension(500, 250));

		textArea = new QuickNotepadTextArea();
		textArea.setFont(QuickNotepadOptionPane.makeFont());

		JScrollPane pane = new JScrollPane(textArea);
		add(BorderLayout.CENTER, pane);

		readFile();
	}

	/** ***************************************************************
	 */
	public void focusOnDefaultComponent() {
		textArea.requestFocus();
	}

	/** ***************************************************************
	 */
	public String getFilename() {
		return filename;
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

		String propertyFilename = jEdit
				.getProperty(QuickNotepadPlugin.OPTION_PREFIX + "filepath");
		if (!StandardUtilities.objectsEqual(defaultFilename, propertyFilename)) {
			saveFile();
			toolPanel.propertiesChanged();
			defaultFilename = propertyFilename;
			filename = defaultFilename;
			readFile();
		}
		Font newFont = QuickNotepadOptionPane.makeFont();
		if (!newFont.equals(textArea.getFont())) {
			textArea.setFont(newFont);
		}
	}

	/** ***************************************************************
	 */
	public void addNotify() {
		super.addNotify();
		EditBus.addToBus(this);
	}

	/** ***************************************************************
	 */
	public void removeNotify() {

		saveFile();
		super.removeNotify();
		EditBus.removeFromBus(this);
	}

	/** ***************************************************************
	 */
	public void saveFile() {

		if (filename == null || filename.length() == 0)
			return;
		try {
			FileWriter out = new FileWriter(filename);
			out.write(textArea.getText());
			out.close();
		}
		catch (IOException ioe) {
			Log.log(Log.ERROR, QuickNotepad.class,
					"Could not write notepad text to " + filename);
		}
	}

	/** ***************************************************************
	 */
	public void chooseFile() {

		String[] paths = GUIUtilities.showVFSFileDialog(view, null,
				JFileChooser.OPEN_DIALOG, false);
		if (paths != null && !paths[0].equals(filename)) {
			saveFile();
			filename = paths[0];
			toolPanel.propertiesChanged();
			readFile();
		}
	}

	/** ***************************************************************
	 */
	public void addHeader() {

		view.getEditPane().getTextArea().setText(";; Header for file\n" +
				view.getEditPane().getTextArea().getText());
	}

	/** ***************************************************************
	 */
	public int getLineNum(String line) {

		int result = 0;
		Pattern p = Pattern.compile("line: (\\d+)");
		Matcher m = p.matcher(line);
		if (m.find()) {
			try { result = Integer.parseInt(m.group(1)); }
			catch (NumberFormatException nfe) {}
		}
		return result;
	}

	/** ***************************************************************
	 */
	public void checkErrors() {

		errorlist.DefaultErrorSource errsrc;
		errsrc = new errorlist.DefaultErrorSource("sigmakee",view);
		errorlist.ErrorSource.registerErrorSource(errsrc);
		jEdit.getAction("error-list-clear").invoke(null);

		//errsrc.addError(ErrorSource.ERROR, "C:\\my_projects\\hw_if\\control\\ctrlapi.c",944,0,0,"LNT787: (Info -- enum constant 'DTV_PL_ASIG_AV_IP1_AUDIO' not used within switch)");

		String contents = view.getEditPane().getTextArea().getText();

		/*
		KIF kif = new KIF();
		kif.filename = filename;

		System.out.println("checkErrors(): about to read kif file");
		try {
			kif.readFile("/home/apease/workspace/sumo/Merge.kif");
			System.out.println("checkErrors(): done reading kif file");
		}
		catch (Exception e) {
			System.out.println("checkErrors(): error loading kif file");
			errsrc.addError(ErrorSource.WARNING,e.getMessage(),1,0,0,
				"start of file with " + contents.length() + " characters "); }
		*/

		System.out.println("checkErrors(): success loading kif file with " + contents.length() + " characters ");
		String path = view.getBuffer().getPath();
		errsrc.addError(ErrorSource.WARNING,path,1,0,0,
				"start of file with " + contents.length() + " characters ");
				//"start of file with " + warnings.size() + " warnings and " + kif.errorSet.size() + " errors");
		/*
		for (String warn : warnings) {
			int line = getLineNum(warn);
			errsrc.addError(ErrorSource.WARNING,path,line,0,0,
					warn);
			textArea.setText(textArea.getText() + "\n" + warn);
		}
		for (String err : kif.errorSet) {
			int line = getLineNum(err);
			errsrc.addError(ErrorSource.ERROR,path,line,0,0,
					err);
			textArea.setText(textArea.getText() + "\n" + err);
		}
		 */
	}

	/** ***************************************************************
	 */
	public void copyToBuffer() {

		jEdit.newFile(view);
		view.getEditPane().getTextArea().setText(textArea.getText());
	}

	/** ***************************************************************
	 */
	private void readFile() {

		if (filename == null || filename.length() == 0)
			return;

		BufferedReader bf = null;
		try {
			bf = new BufferedReader(new FileReader(filename));
			StringBuffer sb = new StringBuffer(2048);
			String str;
			while ((str = bf.readLine()) != null) {
				sb.append(str).append('\n');
			}
			bf.close();
			textArea.setText(sb.toString());
		}
		catch (FileNotFoundException fnf) {
			Log.log(Log.ERROR, QuickNotepad.class, "notepad file " + filename
					+ " does not exist");
		}
		catch (IOException ioe) {
			Log.log(Log.ERROR, QuickNotepad.class,
					"could not read notepad file " + filename);
		}
	}
}
