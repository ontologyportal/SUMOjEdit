package com.articulate.sigma.jedit;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for TPTP‑related helper methods on {@link SUMOjEdit} that live
 * in the SUMOjEdit repository.  These tests exercise private utility
 * functions such as file‑type detection, parsing of tptp4X output and
 * locating formula declarations without invoking the external tptp4X
 * binary.  Reflection is used to access private methods in order to
 * verify their behaviour.
 *
 *
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class TPTPErrorCheckTest {

    private SUMOjEdit sje;

    @Before
    public void setUp() {
        // Initialise a new SUMOjEdit instance and register a fresh
        // ErrorSource.  Without an ErrorSource the class will throw
        // when logging parse warnings/errors.  The SUOKIF tests use
        // the same setup.
        sje = new SUMOjEdit();
        sje.errsrc = new DefaultErrorSource(sje.getClass().getName(), null);
        ErrorSource.registerErrorSource(sje.errsrc);
    }

    @After
    public void tearDown() {
        // Clean up the ErrorSource to avoid polluting other tests.
        ErrorSource.unregisterErrorSource(sje.errsrc);
        sje.errsrc.clear();
        sje = null;
    }

    /**
     * Verify that the private helper {@code isTptpFile()} correctly
     * identifies TPTP file extensions.  Lower‑case and upper‑case
     * variants should be accepted.  Missing or unknown extensions
     * should return false.
     */
    @Test
    public void testIsTptpFile() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("isTptpFile", String.class);
        m.setAccessible(true);

        // Valid extensions (lowercase)
        assertTrue((Boolean) m.invoke(null, "theorem.p"));
        assertTrue((Boolean) m.invoke(null, "foo.fof"));
        assertTrue((Boolean) m.invoke(null, "bar.cnf"));
        assertTrue((Boolean) m.invoke(null, "baz.tff"));
        assertTrue((Boolean) m.invoke(null, "qux.thf"));
        assertTrue((Boolean) m.invoke(null, "logic.tptp"));

        // Valid extensions (upper/mixed case)
        assertTrue((Boolean) m.invoke(null, "AXIOM.P"));
        assertTrue((Boolean) m.invoke(null, "LEMMA.FOF"));
        assertTrue((Boolean) m.invoke(null, "CLAUSE.CNF"));
        assertTrue((Boolean) m.invoke(null, "FORMULA.TFF"));
        assertTrue((Boolean) m.invoke(null, "TERM.THF"));
        assertTrue((Boolean) m.invoke(null, "SET.TPTP"));

        // Missing or unknown extensions
        assertFalse((Boolean) m.invoke(null, (Object) null));
        assertFalse((Boolean) m.invoke(null, "noextension"));
        assertFalse((Boolean) m.invoke(null, "unknown.txt"));
    }

    /**
     * Verify that the private {@code parseTptpOutput()} method
     * correctly parses a variety of tptp4X output formats into
     * {@code ErrRec} instances.  The parser should ignore comment
     * lines, assign sensible default positions when no line or column
     * information is present and return diagnostics sorted by their
     * 0‑based line number.  Messages should be preserved verbatim.
     */
    @Test
    public void testParseTptpOutput() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod(
                "parseTptpOutput", String.class, String.class, int.class);
        m.setAccessible(true);

        // Build a synthetic tptp4X output containing several styles
        StringBuilder sb = new StringBuilder();
        // Style 1: colon‑separated line and column
        sb.append("12:34: something bad happened\n");
        // Style 2: "Line X Char Y" format
        sb.append("Line 15 Char 8 Unexpected token\n");
        // Style 3: "line X, column Y" format
        sb.append("line 20, column 5: parse error\n");
        // Comment line (should be skipped)
        sb.append("% a comment line\n");
        // Unrecognised line that still contains an error keyword
        sb.append("This is not recognised but contains an error message\n");

        String output = sb.toString();
        String filePath = "test.tptp";

        @SuppressWarnings("unchecked")
        List<Object> recs = (List<Object>) m.invoke(sje, filePath, output, ErrorSource.ERROR);
        // Expect 4 diagnostics: one for each non‑comment line
        assertEquals("Expected four diagnostics", 4, recs.size());

        // Helper to extract fields reflectively
        class Rec {
            int line;
            int start;
            int end;
            String msg;
            Rec(Object rec) throws Exception {
                Class<?> rc = rec.getClass();
                Field fLine = rc.getDeclaredField("line");
                fLine.setAccessible(true);
                this.line = fLine.getInt(rec);
                Field fStart = rc.getDeclaredField("start");
                fStart.setAccessible(true);
                this.start = fStart.getInt(rec);
                Field fEnd = rc.getDeclaredField("end");
                fEnd.setAccessible(true);
                this.end = fEnd.getInt(rec);
                Field fMsg = rc.getDeclaredField("msg");
                fMsg.setAccessible(true);
                this.msg = (String) fMsg.get(rec);
            }
        }

        Rec r0 = new Rec(recs.get(0));
        Rec r1 = new Rec(recs.get(1));
        Rec r2 = new Rec(recs.get(2));
        Rec r3 = new Rec(recs.get(3));

        // The results should be sorted by line number (fallback first)
        assertTrue("First diagnostic should have the lowest line number", r0.line <= r1.line);
        assertTrue("Diagnostics should be sorted", r1.line <= r2.line && r2.line <= r3.line);

        // Verify each diagnostic’s computed line and column values and messages
        // Fallback message (no line/col info): line = 0, start = 0, end = 1
        assertEquals(0, r0.line);
        assertEquals(0, r0.start);
        assertEquals(1, r0.end);
        assertTrue(r0.msg.contains("error message"));

        // Style 1: 12:34: ... -> line = 11, col = 33
        assertEquals(11, r1.line);
        assertEquals(33, r1.start);
        assertEquals(34, r1.end);
        assertTrue(r1.msg.contains("something bad happened"));

        // Style 2: Line 15 Char 8 ... -> line = 14, col = 7
        assertEquals(14, r2.line);
        assertEquals(7, r2.start);
        assertEquals(8, r2.end);
        assertTrue(r2.msg.contains("Unexpected token"));

        // Style 3: line 20, column 5: ... -> line = 19, col = 4
        assertEquals(19, r3.line);
        assertEquals(4, r3.start);
        assertEquals(5, r3.end);
        assertTrue(r3.msg.contains("parse error"));
    }

    /**
     * Verify that {@code findFormulaLine()} locates the 0‑based line
     * number of a TPTP formula declaration in a file.  When the
     * formula name does not exist the method should return -1.
     */
    @Test
    public void testFindFormulaLine() throws Exception {
        // Create a temporary file with a few TPTP declarations
        Path tmp = Files.createTempFile("formula", ".tptp");
        try {
            String content = String.join("\n",
                    "% a comment",
                    "tff(formula1, type, (a => b)).",
                    "fof(formula2, type, a).",
                    "cnf(formula3, type, a & b).",
                    "p(x).\n");
            Files.writeString(tmp, content);

            Method m = SUMOjEdit.class.getDeclaredMethod("findFormulaLine", String.class, String.class);
            m.setAccessible(true);

            // Expect 1 for formula1 (comments occupy line 0)
            assertEquals(1, ((Integer) m.invoke(sje, tmp.toString(), "formula1")).intValue());
            // Expect 2 for formula2
            assertEquals(2, ((Integer) m.invoke(sje, tmp.toString(), "formula2")).intValue());
            // Expect 3 for formula3
            assertEquals(3, ((Integer) m.invoke(sje, tmp.toString(), "formula3")).intValue());
            // Unknown formula should return -1
            assertEquals(-1, ((Integer) m.invoke(sje, tmp.toString(), "doesNotExist")).intValue());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Verify that {@code parseTptpFirstErrorLine()} extracts the first
     * numeric line reference from an error message and returns 0 when
     * none is present.  Different capitalisations and prefixes should
     * be handled uniformly.
     */
    @Test
    public void testParseTptpFirstErrorLine() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod(
                "parseTptpFirstErrorLine", String.class);
        m.setAccessible(true);

        assertEquals(7, ((Integer) m.invoke(sje, "Syntax error at line 7: unexpected token")).intValue());
        assertEquals(123, ((Integer) m.invoke(sje, "some error, line 123 something")).intValue());
        assertEquals(5, ((Integer) m.invoke(sje, "Line 5 Char 2 error")).intValue());
        assertEquals(0, ((Integer) m.invoke(sje, "no line information")).intValue());
    }

    /**
     * Verify that {@code deriveErrorLineFromStdout()} returns the
     * 1‑based line number that immediately follows the provided
     * output.  An empty or null string should return 0.
     */
    @Test
    public void testDeriveErrorLineFromStdout() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod(
                "deriveErrorLineFromStdout", String.class);
        m.setAccessible(true);

        // Two lines of output implies the error begins on line 3
        assertEquals(3, ((Integer) m.invoke(sje, "first\nsecond\n")).intValue());
        // Single line implies the next line is 2
        assertEquals(2, ((Integer) m.invoke(sje, "only one line")).intValue());
        // Empty or blank strings should return 0
        assertEquals(0, ((Integer) m.invoke(sje, "")).intValue());
        assertEquals(0, ((Integer) m.invoke(sje, (String) null)).intValue());
    }
} // end class file TPTPErrorCheckTest.java