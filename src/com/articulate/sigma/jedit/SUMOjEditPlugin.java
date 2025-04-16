package com.articulate.sigma.jedit;

/*
 * SUMOjEditPlugin.java
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

import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.EditPlugin;

/**
 * The SUMOjEdit plugin launcher
 * @author Adam Pease
 */
public class SUMOjEditPlugin extends EditPlugin {

    public static final String NAME = "sumojedit";
    public static final String OPTION_PREFIX = "options." + NAME + ".";

    private EBComponent sje;

    @Override
    public void start() {

        sje = new SUMOjEdit();

        // Allow jEdit to start while the KBs are loading
        ((SUMOjEdit)sje).startThread(((SUMOjEdit)sje), "KB init");
        EditBus.addToBus(sje);
    }

    @Override
    public void stop() {

        EditBus.removeFromBus(sje);
    }

    /** JavaBean accessor for the plugin component
     *
     * @return an EBComponent (the plugin)
     */
    public EBComponent getSje() {return sje;}
}
