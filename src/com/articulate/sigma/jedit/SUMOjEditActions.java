package com.articulate.sigma.jedit;

/*
 * SUMOjEditActions.java
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

/** Defines the actions to take in this plugin */
interface SUMOjEditActions {

    /**
     * ***************************************************************
     * Show statistics for a given buffer
     */
    void showStats();

    /**
     * ***************************************************************
     * Check for a variety of syntactic and semantic errors and warnings in a
     * given buffer
     */
    void checkErrors();

    /**
     * ***************************************************************
     * Reformat a selection of SUO-KIF axioms in a buffer. In case of error,
     * such as a selection that only spans part of an axiom, do nothing.
     */
    void formatSelect();

    /**
     * ***************************************************************
     * Go to the "definition" of a selected term. If no term is selected do
     * nothing other than print an WARN to the console. If definition is in
     * another file, load that file.
     */
    void gotoDefn();

    /**
     * ***************************************************************
     * Open up a browser on the public Sigma for the highlighted term. If it's
     * not a term, or not in the KB, don't open
     */
    void browseTerm();

    /**
     * ***************************************************************
     * Send a highlighted expression as a query to a theorem prover. return
     * results in a new tab
     */
    void queryExp();

    /**
     * ***************************************************************
     * Set theorem proving to use Vampire
     */
    void chooseVamp();

    /**
     * ***************************************************************
     * Set theorem proving to use E
     */
    void chooseE();

    /**
     * ***************************************************************
     * Set theorem proving to use FOF translation of SUMO
     */
    void setFOF();

    /**
     * ***************************************************************
     * Set theorem proving to use TFF translation of SUMO
     */
    void setTFF();

    /**
     * ***************************************************************
     * Convert a buffer or selection from SUO-KIF to TPTP. Note that this does
     * not do full pre-processing, just a syntax translation
     */
    void toTPTP();

    /**
     * ***************************************************************
     * Convert a buffer or selection from TPTP to SUO-KIF. Note that this does
     * not do full pre-processing, just a syntax translation
     */
    void fromTPTP();
}
