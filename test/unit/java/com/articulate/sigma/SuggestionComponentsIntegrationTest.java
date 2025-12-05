package com.articulate.sigma;

import com.articulate.sigma.jedit.fastac.PrefixIndex;
import com.articulate.sigma.jedit.fastac.FastSuggestor;
import com.articulate.sigma.jedit.fastac.FastACBootstrap;
import com.articulate.sigma.jedit.fastac.FastACAutoAttach;
import org.gjt.sp.jedit.jEdit;
import org.junit.Test;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration tests for the suggestion components.
 * 
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class SuggestionComponentsIntegrationTest {

    @Test
    public void testPrefixIndexWithFastSuggestorIntegration() throws Exception {
        jEdit.setProperty("sumo.autocomplete.mode", "popup");
        List<String> words = Arrays.asList("Human", "Horse", "Happiness", "Happen");
        PrefixIndex index = new PrefixIndex();
        index.build(words);

        JTextArea area = new JTextArea();
        area.setText("Hap");
        area.setCaretPosition(area.getDocument().getLength());

        FastSuggestor fs = new FastSuggestor(area, index);
        Method currentWordPrefix = FastSuggestor.class.getDeclaredMethod("currentWordPrefix");
        currentWordPrefix.setAccessible(true);
        String prefix = (String) currentWordPrefix.invoke(fs);
        assertEquals("Hap", prefix);

        List<String> suggestions = index.suggest(prefix, 10);
        assertNotNull(suggestions);
        assertFalse(suggestions.isEmpty());
    }

    @Test
    public void testFastACBootstrapRunOnce() throws Exception {
        jEdit.setProperty("sumo.autocomplete.mode", "popup");
        FastACBootstrap.runOnce();
        FastACBootstrap.runOnce();

        Field didRunField = FastACBootstrap.class.getDeclaredField("didRun");
        didRunField.setAccessible(true);
        java.util.concurrent.atomic.AtomicBoolean didRun =
                (java.util.concurrent.atomic.AtomicBoolean) didRunField.get(null);
        assertTrue("FastACBootstrap should record that it has run", didRun.get());
    }

    @Test
    public void testFastACAutoAttachPopupEnabled() {
        jEdit.setProperty("sumo.autocomplete.mode", "popup");
        FastACAutoAttach.attachEverywhere(Collections.emptyList());
    }
}