package com.articulate.sigma.jedit;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.articulate.sigma.jedit.SUMOjEdit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Standalone unit tests for remaining private helper methods on
 * {@link SUMOjEdit} that do not depend on the GUI or external
 * ATP processes.  This class covers:
 *
 *  - parseIntSafe(String, int)
 *  - findTermInLine(String, String, int) and isTermChar(char)
 *  - findFormulaInBuffer(String, String[])
 *  - filespecFromForms(List<Formula>, String)
 *
 * These helpers are exercised via reflection so that the
 * public API of {@link SUMOjEdit} remains unchanged.
 *
 * 
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class SUMOjEditResidualHelpersTest {

    private SUMOjEdit sje;

    @Before
    public void setUp() {
        // Initialise a fresh SUMOjEdit instance and ErrorSource,
        // mirroring the setup used in other SUMOjEdit tests.
        sje = new SUMOjEdit();
        sje.errsrc = new DefaultErrorSource(sje.getClass().getName(), null);
        ErrorSource.registerErrorSource(sje.errsrc);
    }

    @After
    public void tearDown() {
        // Clean up any registered ErrorSource state between runs.
        ErrorSource.unregisterErrorSource(sje.errsrc);
        sje.errsrc.clear();
        sje = null;
    }

    // ---------------------------------------------------------------------
    // parseIntSafe(String, int)
    // ---------------------------------------------------------------------

    @Test
    public void testParseIntSafeParsesValidIntegerWithWhitespace() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("parseIntSafe", String.class, int.class);
        m.setAccessible(true);

        assertEquals(42, ((Integer) m.invoke(null, "42", 1)).intValue());
        assertEquals(7, ((Integer) m.invoke(null, "  7  ", 3)).intValue());
    }

    @Test
    public void testParseIntSafeFallsBackOnInvalidOrNull() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("parseIntSafe", String.class, int.class);
        m.setAccessible(true);

        // Non-numeric input should return the default
        assertEquals(5, ((Integer) m.invoke(null, "abc", 5)).intValue());
        // Empty string should also return the default
        assertEquals(9, ((Integer) m.invoke(null, "", 9)).intValue());
        // Null input should be caught and return the default
        assertEquals(4, ((Integer) m.invoke(null, (String) null, 4)).intValue());
    }

    // ---------------------------------------------------------------------
    // findTermInLine(String, String, int) and isTermChar(char)
    // ---------------------------------------------------------------------

    @Test
    public void testFindTermInLineMatchesWholeTermsWithBoundaries() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("findTermInLine",
                                                     String.class, String.class, int.class);
        m.setAccessible(true);

        String line = "The Human is an Animal.";
        int pos = ((Integer) m.invoke(sje, line, "Human", 0)).intValue();
        // "The " is 4 characters, so "Human" should start at index 4
        assertEquals(4, pos);

        // Term at the end of the line with punctuation
        line = "Animal Human.";
        pos = ((Integer) m.invoke(sje, line, "Human", 0)).intValue();
        // "Animal " is 7 characters, so "Human" should start at index 7
        assertEquals(7, pos);
    }

    @Test
    public void testFindTermInLineRejectsPartialTermsInsideLargerTokens() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("findTermInLine",
                                                     String.class, String.class, int.class);
        m.setAccessible(true);

        // "Human" appears only as part of a larger token and should not match
        assertEquals(-1, ((Integer) m.invoke(sje, "SuperHuman", "Human", 0)).intValue());
        assertEquals(-1, ((Integer) m.invoke(sje, "Human-Animal", "Human", 0)).intValue());
        assertEquals(-1, ((Integer) m.invoke(sje, "XHuman_", "Human", 0)).intValue());
    }

    @Test
    public void testIsTermCharClassification() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("isTermChar", char.class);
        m.setAccessible(true);

        // Letters, digits, '-' and '_' are term characters
        assertTrue((Boolean) m.invoke(sje, 'A'));
        assertTrue((Boolean) m.invoke(sje, 'z'));
        assertTrue((Boolean) m.invoke(sje, '0'));
        assertTrue((Boolean) m.invoke(sje, '-'));
        assertTrue((Boolean) m.invoke(sje, '_'));

        // Whitespace and punctuation are not term characters
        assertFalse((Boolean) m.invoke(sje, ' '));
        assertFalse((Boolean) m.invoke(sje, '.'));
        assertFalse((Boolean) m.invoke(sje, '('));
        assertFalse((Boolean) m.invoke(sje, ')'));
    }

    // ---------------------------------------------------------------------
    // findFormulaInBuffer(String, String[])
    // ---------------------------------------------------------------------

    @Test
    public void testFindFormulaInBufferMatchesFirstMeaningfulLine() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("findFormulaInBuffer",
                                                     String.class, String[].class);
        m.setAccessible(true);

        String formula =
                "\n\n" +
                "fof(ax1, axiom, (p & q)).\n" +
                "   % more stuff\n";

        String[] bufferLines = new String[] {
                "random header",
                "    fof(ax1, axiom, (p & q)).   ",
                "trailing text"
        };

        int idx = ((Integer) m.invoke(sje, formula, bufferLines)).intValue();
        assertEquals(1, idx);
    }

    @Test
    public void testFindFormulaInBufferUsesShortPrefixFallback() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("findFormulaInBuffer",
                                                     String.class, String[].class);
        m.setAccessible(true);

        // Construct a long first line so that the method will consider a
        // shortened prefix when the full line is not found in the buffer.
        String longFirstLine =
                "fof(ax2, axiom, (p & q & r & s & t & u & v)).";
        String formula = longFirstLine + "\n% comment line\n";

        // Buffer contains only the first ~20 characters of the first line,
        // not the full line, to force the short-prefix path.
        // We don't care about the exact 20-char boundary here; we just need
        // to ensure the full line is not present, but its prefix is.
        String shortPrefix = longFirstLine.substring(0, 20);
        String[] bufferLines = new String[] {
                "noise",
                shortPrefix + " truncated content",
                "more noise"
        };

        int idx = ((Integer) m.invoke(sje, formula, bufferLines)).intValue();
        assertEquals(1, idx);
    }

    @Test
    public void testFindFormulaInBufferReturnsMinusOneForEmptyOrMissing() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("findFormulaInBuffer",
                                                     String.class, String[].class);
        m.setAccessible(true);

        // Empty formula: no meaningful line
        String[] bufferLines = new String[] { "line1", "line2" };
        assertEquals(-1, ((Integer) m.invoke(sje, "", bufferLines)).intValue());

        // Formula has a first line, but it does not appear anywhere in the buffer
        String formula = "fof(ax3, axiom, (p)).\n";
        assertEquals(-1, ((Integer) m.invoke(sje, formula, bufferLines)).intValue());
    }

    // ---------------------------------------------------------------------
    // filespecFromForms(List<Formula>, String)
    // ---------------------------------------------------------------------

    /**
     * Helper to create a com.articulate.sigma.Formula instance and set
     * its sourceFile and startLine fields via reflection, without
     * depending on the public constructors or field visibility.
     */
    private Object makeFormula(String sourceFile, int startLine) throws Exception {
        Class<?> fClass = Class.forName("com.articulate.sigma.Formula");
        Object f = fClass.getConstructor().newInstance();

        Field sourceField = fClass.getDeclaredField("sourceFile");
        sourceField.setAccessible(true);
        sourceField.set(f, sourceFile);

        Field lineField = fClass.getDeclaredField("startLine");
        lineField.setAccessible(true);
        lineField.setInt(f, startLine);

        return f;
    }

    /**
     * Helper to read fields from the SUMOjEdit.FileSpec inner class.
     */
    private Object[] readFileSpec(Object fs) throws Exception {
        Class<?> fsClass = Class.forName("com.articulate.sigma.jedit.SUMOjEdit$FileSpec");

        Field filepathField = fsClass.getDeclaredField("filepath");
        filepathField.setAccessible(true);
        Field lineField = fsClass.getDeclaredField("line");
        lineField.setAccessible(true);

        String path = (String) filepathField.get(fs);
        int line = ((Integer) lineField.get(fs)).intValue();

        return new Object[] { path, line };
    }

    @Test
    public void testFilespecFromFormsPrefersCurrentFileNonCache() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("filespecFromForms",
                                                     List.class, String.class);
        m.setAccessible(true);

        Object f1 = makeFormula("/path/other.kif", 5);
        Object f2 = makeFormula("/dir/MyFile.kif", 10);
        Object f3 = makeFormula("/dir/MyFile_Cache.kif", 20); // should be ignored

        List forms = new ArrayList();
        forms.add(f1);
        forms.add(f2);
        forms.add(f3);

        Object fs = m.invoke(sje, forms, "MyFile.kif");
        Object[] result = readFileSpec(fs);

        assertEquals("/dir/MyFile.kif", result[0]);
        assertEquals(9, ((Integer) result[1]).intValue()); // startLine - 1
    }

    @Test
    public void testFilespecFromFormsFallsBackToFirstNonCacheWhenNoExactMatch() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("filespecFromForms",
                                                     List.class, String.class);
        m.setAccessible(true);

        Object f1 = makeFormula("/dir/Some_Cache.kif", 3); // cache file
        Object f2 = makeFormula("/dir/OtherFile.kif", 7);  // first non-cache
        Object f3 = makeFormula("/dir/Another.kif", 9);

        List forms = new ArrayList();
        forms.add(f1);
        forms.add(f2);
        forms.add(f3);

        Object fs = m.invoke(sje, forms, "Missing.kif");
        Object[] result = readFileSpec(fs);

        assertEquals("/dir/OtherFile.kif", result[0]);
        assertEquals(6, ((Integer) result[1]).intValue()); // startLine - 1
    }

    @Test
    public void testFilespecFromFormsReturnsEmptyWhenAllFormsAreCache() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("filespecFromForms",
                                                     List.class, String.class);
        m.setAccessible(true);

        Object f1 = makeFormula("/dir/Term1_Cache.kif", 2);
        Object f2 = makeFormula("/dir/Term2_Cache.kif", 4);

        List forms = new ArrayList();
        forms.add(f1);
        forms.add(f2);

        Object fs = m.invoke(sje, forms, "MyFile.kif");
        Object[] result = readFileSpec(fs);

        // With only _Cache.kif entries, the method should return the
        // default FileSpec, whose filepath is empty and line is -1.
        assertEquals("", result[0]);
        assertEquals(-1, ((Integer) result[1]).intValue());
    }
}