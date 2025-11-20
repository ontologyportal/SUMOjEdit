package com.articulate.sigma.jedit;

import org.gjt.sp.jedit.textarea.TextArea;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Unit tests for private helper methods on
 * com.articulate.sigma.jedit.fastac.smartcompose.TopCompletionAdapter.
 *
 * We use reflection because the methods under test are private/package-private
 * and we want to avoid touching GUI code directly.
 *
 *
 * @author <a href="mailto:adam.pease@nps.edu?subject=com.articulate.sigma.jedit.SUOKIFErrorCheckTest">Simon Deng, NPS ORISE Intern 2025</a>
 */

public class TopCompletionAdapterTest {

    private static Object invokePrivateStatic(String methodName,
                                              Class<?>[] paramTypes,
                                              Object... args) throws Exception {
        Class<?> clazz = Class.forName(
                "com.articulate.sigma.jedit.fastac.smartcompose.TopCompletionAdapter");
        Method m = clazz.getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    @Test
    public void testIsWordRecognizesIdentifierCharacters() throws Exception {
        // Valid identifier characters: letters, digits, '_' and '-'
        assertTrue((Boolean) invokePrivateStatic(
                "isWord", new Class[]{char.class}, 'a'));
        assertTrue((Boolean) invokePrivateStatic(
                "isWord", new Class[]{char.class}, 'Z'));
        assertTrue((Boolean) invokePrivateStatic(
                "isWord", new Class[]{char.class}, '0'));
        assertTrue((Boolean) invokePrivateStatic(
                "isWord", new Class[]{char.class}, '_'));
        assertTrue((Boolean) invokePrivateStatic(
                "isWord", new Class[]{char.class}, '-'));

        // Non-identifier characters
        assertFalse((Boolean) invokePrivateStatic(
                "isWord", new Class[]{char.class}, ' '));
        assertFalse((Boolean) invokePrivateStatic(
                "isWord", new Class[]{char.class}, '\n'));
        assertFalse((Boolean) invokePrivateStatic(
                "isWord", new Class[]{char.class}, '+'));
        assertFalse((Boolean) invokePrivateStatic(
                "isWord", new Class[]{char.class}, '$'));
    }

    @Test
    public void testBetterPrefersShorterCandidate() throws Exception {
        String prefix = "inst";

        // Shorter candidate should be preferred
        assertTrue((Boolean) invokePrivateStatic(
                "better",
                new Class[]{String.class, String.class, String.class},
                prefix, "instance", "instanceFoo"));

        // And the inverse should be false
        assertFalse((Boolean) invokePrivateStatic(
                "better",
                new Class[]{String.class, String.class, String.class},
                prefix, "instanceFoo", "instance"));
    }

    @Test
    public void testBetterUsesLexicographicalOrderWhenSameLength() throws Exception {
        String prefix = "foo";

        // "alpha" < "beta" lexicographically, so "alpha" is the better suggestion
        assertTrue((Boolean) invokePrivateStatic(
                "better",
                new Class[]{String.class, String.class, String.class},
                prefix, "alpha", "beta"));

        // Reverse order should be false
        assertFalse((Boolean) invokePrivateStatic(
                "better",
                new Class[]{String.class, String.class, String.class},
                prefix, "beta", "alpha"));
    }

    @Test
    public void testBestFullUsesSumoVocabularyWhenAvailable() throws Exception {
        // The first pass in bestFull() uses the SUMO vocabulary and does not
        // touch the TextArea parameter, so we can safely pass null here as
        // long as the prefix matches a SUMO word (e.g. "instance").
        Class<?> clazz = Class.forName(
                "com.articulate.sigma.jedit.fastac.smartcompose.TopCompletionAdapter");
        Method bestFull = clazz.getDeclaredMethod(
                "bestFull", TextArea.class, String.class);
        bestFull.setAccessible(true);

        Object result = bestFull.invoke(null, new Object[]{null, "inst"});

        assertEquals("instance", result);
    }
}