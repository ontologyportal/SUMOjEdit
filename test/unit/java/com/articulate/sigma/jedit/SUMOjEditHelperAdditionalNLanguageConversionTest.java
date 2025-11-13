package com.articulate.sigma.jedit;

import errorlist.DefaultErrorSource;
import errorlist.ErrorSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * Additional unit tests for helper methods on {@link SUMOjEdit} that
 * support language conversion between SUO‑KIF and TPTP.  These
 * helpers perform string manipulation, file IO and heuristic error
 * filtering that can be exercised in isolation.  The methods
 * tested here were not covered by the existing test suites provided
 * with the project.  Reflection is used to access private
 * implementations.
 *
 * Each test case avoids interacting with the external
 * tptp4X program or the jEdit GUI, ensuring they remain
 * deterministic and self‑contained.  Test inputs emphasise edge
 * cases such as nulls, extraneous whitespace and unexpected
 * formatting.
 * 
 * This test file targets the helper methods that surround SUMOjEdit’s 
 * Language Conversion feature, focusing on the TPTP side. It verifies 
 * how TPTP error messages are trimmed and cleaned (`stripTailAfterPercentDash`, `
 * cleanErrorMessage`), how formula names are extracted from messages 
 * to locate the correct TPTP formula line (`extractContinuingWithFormula`), 
 * how real errors are distinguished from noise (`looksLikeRealError`), 
 * how offending lines are retrieved and truncated from source files 
 * for display (`safeSnippetFromFile`), how temporary files are 
 * created for TPTP tools (`writeTemp`), and how the TPTP fragment 
 * used by the SUO-KIF→TPTP translator is configured (`setFOF`, `setTFF`).
 */
/**
 *
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class SUMOjEditHelperAdditionalNLanguageConversionTest {

    private SUMOjEdit sje;

    @Before
    public void setUp() {
        // Instantiate SUMOjEdit and register a dummy ErrorSource to avoid
        // NPEs when the class logs diagnostics.  Although most of the
        // helpers tested here are static, providing a consistent
        // environment mirrors the existing tests.
        sje = new SUMOjEdit();
        sje.errsrc = new DefaultErrorSource(sje.getClass().getName(), null);
        ErrorSource.registerErrorSource(sje.errsrc);
    }

    @After
    public void tearDown() {
        ErrorSource.unregisterErrorSource(sje.errsrc);
        sje.errsrc.clear();
        sje = null;
    }

    /**
     * Verify that {@code stripTailAfterPercentDash()} removes
     * trailing formula comments introduced by tptp4X.  It should
     * strip both the Unicode em dash (—) and a plain space from
     * comments starting with '%' and return the trimmed message.
     * Null inputs should yield an empty string and strings without
     * a trailing comment should be returned unchanged (after
     * trimming).  Multiple whitespace variations are exercised.
     */
    @Test
    public void testStripTailAfterPercentDash() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("stripTailAfterPercentDash", String.class);
        m.setAccessible(true);

        // Null input yields empty string
        assertEquals("", m.invoke(null, (String) null));

        // Message terminated by an em dash and comment
        assertEquals("Message", m.invoke(null, "Message — %formula info"));
        // Message terminated by a plain space and comment
        assertEquals("Message", m.invoke(null, "Message %comment"));
        // Message with extra whitespace around the em dash
        assertEquals("Message", m.invoke(null, "Message   —   %trailing stuff"));
        // Message without any trailing comment should remain (after trimming)
        assertEquals("Simple", m.invoke(null, "  Simple  "));
    }

    /**
     * Verify that {@code cleanErrorMessage()} removes concatenated
     * formula declarations and trailing TPTP declarations.  It
     * should handle both an em dash and a regular dash prefix and
     * remove any trailing tff/thf/fof/cnf formula declarations
     * regardless of whether they are followed by punctuation.  Null
     * inputs should yield an empty string and unrelated messages
     * should be returned trimmed but otherwise unchanged.
     */
    @Test
    public void testCleanErrorMessage() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("cleanErrorMessage", String.class);
        m.setAccessible(true);

        // Null input produces an empty string
        assertEquals("", m.invoke(null, (String) null));

        // Remove content after an em dash
        assertEquals("msg", m.invoke(null, "msg — tff(foo,bar)"));
        // Remove content after a regular dash with various prefixes
        assertEquals("msg", m.invoke(null, "msg - tff(foo, bar)"));
        assertEquals("msg", m.invoke(null, "msg - thf(bar)"));
        assertEquals("msg", m.invoke(null, "msg - fof(baz)"));
        assertEquals("msg", m.invoke(null, "msg - cnf(qux)"));

        // Remove trailing formula declarations without a preceding dash
        assertEquals("msg", m.invoke(null, "msg tff(foo)."));
        assertEquals("msg", m.invoke(null, "msg thf(bar)"));
        assertEquals("msg", m.invoke(null, "msg fof(baz)."));
        assertEquals("msg", m.invoke(null, "msg cnf(qux)"));

        // Messages without any recognised patterns should remain unchanged (trimmed)
        assertEquals("plain message", m.invoke(null, "plain message"));
    }

    /**
     * Verify that {@code extractContinuingWithFormula()} extracts the
     * formula name from the "continuing with 'name'" pattern.  It
     * should ignore any trailing content after a comma and support
     * both single and double quotation marks.  Null and non‑matching
     * strings should return null.
     */
    @Test
    public void testExtractContinuingWithFormula() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("extractContinuingWithFormula", String.class);
        m.setAccessible(true);

        // Null input yields null
        assertNull(m.invoke(null, (String) null));

        // Single quoted formula name
        assertEquals("foo", m.invoke(null, "continuing with 'foo'"));
        // Double quoted formula name with extra suffix
        assertEquals("bar", m.invoke(null, "continuing with \"bar\" text"));
        // Formula name followed by a comma and additional text
        assertEquals("baz", m.invoke(null, "something continuing with 'baz, extra stuff' more"));
        // No matching pattern returns null
        assertNull(m.invoke(null, "no pattern here"));
    }

    /**
     * Verify that {@code looksLikeRealError()} recognises genuine
     * error messages by the presence of common keywords and filters
     * out spurious messages such as agent file names.  It should
     * return false for null, blank or non‑error strings and true
     * when a recognised keyword appears.  The agent file pattern
     * ending in ".kif" should be rejected even if it contains an
     * error keyword.
     */
    @Test
    public void testLooksLikeRealError() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("looksLikeRealError", String.class);
        m.setAccessible(true);

        // Null and blank inputs are not real errors
        assertFalse((Boolean) m.invoke(null, (String) null));
        assertFalse((Boolean) m.invoke(null, "    "));

        // Strings containing common error keywords should be accepted
        assertTrue((Boolean) m.invoke(null, "This is an error message"));
        assertTrue((Boolean) m.invoke(null, "Warning: unexpected token"));

        // Agent pattern ending with .kif should be rejected
        assertFalse((Boolean) m.invoke(null, "agentSomething...TQM8.kif"));

        // Non‑error strings should return false
        assertFalse((Boolean) m.invoke(null, "This is a normal line"));
    }

    /**
     * Verify that {@code safeSnippetFromFile()} returns the correct
     * line from a file and truncates long lines to the maximum
     * snippet length.  Out‑of‑range or negative line indices should
     * produce an empty string and missing files should be handled
     * gracefully.
     */
    @Test
    public void testSafeSnippetFromFile() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("safeSnippetFromFile", String.class, int.class);
        m.setAccessible(true);

        // Create a temporary file with three lines
        Path tmp = Files.createTempFile("snippet", ".tptp");
        try {
            String longLine = "x".repeat(150);
            String content = String.join("\n", "First line", "Second line", longLine);
            Files.writeString(tmp, content);

            // Valid lines should be returned intact (trimmed) or truncated
            assertEquals("First line", m.invoke(null, tmp.toString(), 0));
            assertEquals("Second line", m.invoke(null, tmp.toString(), 1));
            // The long line should be truncated to 100 characters with an ellipsis
            String truncated = (String) m.invoke(null, tmp.toString(), 2);
            assertTrue("Truncated line should start with the first 100 characters", truncated.startsWith("x".repeat(100)));
            // The truncated string should not exceed 100 characters
            assertEquals(100, truncated.length());

            // Negative index and out‑of‑range indices yield empty string
            assertEquals("", m.invoke(null, tmp.toString(), -1));
            assertEquals("", m.invoke(null, tmp.toString(), 5));
            // Non‑existent file should return empty string
            assertEquals("", m.invoke(null, "nonexistent.file", 0));
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {}
        }
    }

    /**
     * Verify that {@code writeTemp()} creates a file with the
     * specified content and suffix.  Multiple calls should yield
     * distinct temporary files.  The content of the file is read
     * back from disk to ensure correctness.  It is the caller's
     * responsibility to delete the files afterwards.
     */
    @Test
    public void testWriteTemp() throws Exception {
        Method m = SUMOjEdit.class.getDeclaredMethod("writeTemp", String.class, String.class);
        m.setAccessible(true);

        String text1 = "Hello world";
        String text2 = "Another file";
        // Create two temporary files with different suffixes
        Path f1 = (Path) m.invoke(null, text1, ".tmp");
        Path f2 = (Path) m.invoke(null, text2, ".out");
        try {
            // Ensure the suffixes are honoured
            assertTrue(f1.toString().endsWith(".tmp"));
            assertTrue(f2.toString().endsWith(".out"));
            // Files should not be the same
            assertNotEquals(f1, f2);
            // Read back the contents
            assertEquals(text1, Files.readString(f1));
            assertEquals(text2, Files.readString(f2));
        } finally {
            try { Files.deleteIfExists(f1); } catch (IOException ignore) {}
            try { Files.deleteIfExists(f2); } catch (IOException ignore) {}
        }
    }

    /**
     * Verify that calls to {@code setFOF()} and {@code setTFF()}
     * execute without throwing and update the expected static
     * language settings if those classes are present on the classpath.
     * In environments where the translation classes are absent
     * (e.g. during standalone unit testing), the test simply
     * ensures that the methods can be invoked without error.
     */
    @Test
    public void testSetFOFAndSetTFF() throws Exception {
        // Attempt to locate the translation classes; they may not be available
        Class<?> formulaClass = null;
        Class<?> kbClass = null;
        try {
            formulaClass = Class.forName("com.articulate.sigma.trans.SUMOformulaToTPTPformula");
        } catch (Throwable ignore) {}
        try {
            kbClass = Class.forName("com.articulate.sigma.trans.SUMOKBtoTPTPKB");
        } catch (Throwable ignore) {}

        // Store original values if classes are present
        String origFormulaLang = null;
        String origKbLang = null;
        if (formulaClass != null) {
            var field = formulaClass.getDeclaredField("lang");
            field.setAccessible(true);
            origFormulaLang = (String) field.get(null);
        }
        if (kbClass != null) {
            var field = kbClass.getDeclaredField("lang");
            field.setAccessible(true);
            origKbLang = (String) field.get(null);
        }

        // Invoke setFOF() and setTFF(); they should not throw
        sje.setFOF();
        sje.setTFF();

        // If the classes exist, verify that the static language fields were updated
        if (formulaClass != null) {
            var field = formulaClass.getDeclaredField("lang");
            field.setAccessible(true);
            assertEquals("tff", field.get(null));
        }
        if (kbClass != null) {
            var field = kbClass.getDeclaredField("lang");
            field.setAccessible(true);
            assertEquals("tff", field.get(null));
        }

        // Restore original values to avoid side effects on other tests
        if (formulaClass != null && origFormulaLang != null) {
            var field = formulaClass.getDeclaredField("lang");
            field.setAccessible(true);
            field.set(null, origFormulaLang);
        }
        if (kbClass != null && origKbLang != null) {
            var field = kbClass.getDeclaredField("lang");
            field.setAccessible(true);
            field.set(null, origKbLang);
        }
    }
} // end class file SUMOjEditHelperAdditionalNLanguageConversionTest.java