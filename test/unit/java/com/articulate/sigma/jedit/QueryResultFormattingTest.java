package com.articulate.sigma.jedit;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Standalone unit tests for SUMOjEdit#queryResultString(TPTP3ProofProcessor).
 *
 * Verifies output ordering:
 *   1) Bindings (bindingMap preferred over bindings)
 *   2) Status
 *   3) Proof steps (in-order, after a blank line)
 *
 * This test avoids running any prover; it stubs the proof processor fields directly via reflection.
 *
 * 
 * Author: Simon Deng, NPS ORISE Intern 2025, adam.pease@nps.edu
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.fastac.KifTermIndexTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class QueryResultFormattingTest {

    // ----------------------------
    // Reflection helpers
    // ----------------------------

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        fail("Field not found: " + fieldName + " on " + target.getClass());
    }

    private static Object newTptp3ProofProcessor() throws Exception {
        Class<?> clazz = Class.forName("com.articulate.sigma.trans.TPTP3ProofProcessor");
        return clazz.getConstructor().newInstance();
    }

    private static Object makeTptpFormula(String s) throws Exception {
        Class<?> tf = Class.forName("tptp_parser.TPTPFormula");
        Object inst = tf.getConstructor().newInstance();

        // Best effort: set a string field that represents the formula text.
        Field chosen = null;

        for (Field f : tf.getDeclaredFields()) {
            if (f.getType() == String.class && "formula".equalsIgnoreCase(f.getName())) {
                chosen = f;
                break;
            }
        }
        if (chosen == null) {
            for (Field f : tf.getDeclaredFields()) {
                if (f.getType() == String.class) {
                    chosen = f;
                    break;
                }
            }
        }

        if (chosen != null) {
            chosen.setAccessible(true);
            chosen.set(inst, s);
        }

        return inst;
    }

    private static String invokeQueryResultString(SUMOjEdit sje, Object tpp) throws Exception {
        Class<?> tppClass = Class.forName("com.articulate.sigma.trans.TPTP3ProofProcessor");
        Method m = SUMOjEdit.class.getDeclaredMethod("queryResultString", tppClass);
        m.setAccessible(true);
        return (String) m.invoke(sje, tpp);
    }

    private static String normalizeNewlines(String s) {
        if (s == null) return null;
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    // ----------------------------
    // Tests
    // ----------------------------

    @Test
    public void testBindingMapStatusProofOrder() throws Exception {
        SUMOjEdit sje = new SUMOjEdit();
        Object tpp = newTptp3ProofProcessor();

        Map<String, String> bindingMap = new LinkedHashMap<>();
        bindingMap.put("?X", "Human");

        List<Object> proof = new ArrayList<>();
        proof.add(makeTptpFormula("step1: A"));
        proof.add(makeTptpFormula("step2: B"));

        setField(tpp, "bindingMap", bindingMap);
        setField(tpp, "bindings", Arrays.asList("?X=SHOULD_NOT_APPEAR")); // ignored if bindingMap present
        setField(tpp, "status", "Theorem");
        setField(tpp, "proof", proof);

        String out = normalizeNewlines(invokeQueryResultString(sje, tpp));
        assertNotNull(out);

        int iBindings = out.indexOf("Bindings:");
        int iStatus = out.indexOf("Theorem");
        int iStep1 = out.indexOf("step1: A");
        int iStep2 = out.indexOf("step2: B");

        assertTrue("Expected Bindings block", iBindings >= 0);
        assertTrue("Expected status", iStatus >= 0);
        assertTrue("Expected proof step1", iStep1 >= 0);
        assertTrue("Expected proof step2", iStep2 >= 0);

        assertTrue("Bindings must come before status", iBindings < iStatus);
        assertTrue("Status must come before proof", iStatus < iStep1);
        assertTrue("Proof steps must preserve order", iStep1 < iStep2);

        assertFalse("bindings list must not be used when bindingMap is present",
                out.contains("SHOULD_NOT_APPEAR"));
    }

    @Test
    public void testBindingsListUsedWhenNoBindingMap() throws Exception {
        SUMOjEdit sje = new SUMOjEdit();
        Object tpp = newTptp3ProofProcessor();

        setField(tpp, "bindingMap", Collections.emptyMap());
        setField(tpp, "bindings", Arrays.asList("?X=Human"));
        setField(tpp, "status", "Satisfiable");
        setField(tpp, "proof", Collections.emptyList());

        String out = normalizeNewlines(invokeQueryResultString(sje, tpp));
        assertNotNull(out);

        assertTrue(out.contains("Bindings:"));
        assertTrue(out.contains("Satisfiable"));

        int iBindings = out.indexOf("Bindings:");
        int iStatus = out.indexOf("Satisfiable");
        assertTrue("Bindings must come before status", iBindings < iStatus);

        // No proof => should not include proof lines
        assertFalse("No proof steps should be present", out.contains("step"));
    }

    @Test
    public void testStatusShownEvenWithoutBindings() throws Exception {
        SUMOjEdit sje = new SUMOjEdit();
        Object tpp = newTptp3ProofProcessor();

        setField(tpp, "bindingMap", Collections.emptyMap());
        setField(tpp, "bindings", Collections.emptyList());
        setField(tpp, "status", "Unknown");
        setField(tpp, "proof", Collections.emptyList());

        String out = normalizeNewlines(invokeQueryResultString(sje, tpp));
        assertNotNull(out);
        assertEquals("Unknown", out.trim());
    }
}
