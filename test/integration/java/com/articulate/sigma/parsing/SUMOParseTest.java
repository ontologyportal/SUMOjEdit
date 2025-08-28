package com.articulate.sigma.parsing;

import com.articulate.sigma.utils.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/** Go through the entire SUMO KB and parse, checking for syntax errors
 *
 * @author <a href="mailto:terry.norbraten@gmail.com?subject=com.articulate.sigma.parsing.SUMOParseTest">Terry Norbraten</a>
 */
public class SUMOParseTest {

    /** Relative path to a specific individual *.kif file */
    static String relPath;

    private void parsePath(String path) throws IOException {

        System.out.println("SUMOjEdit com.articulate.sigma.parsing.SUMOParseTest.parsePath(): path: " + path)
        Path inPath = Paths.get(path);
        try (Stream<Path> paths = Files.walk(inPath)) {
            paths.filter(f -> f.toString().endsWith(".kif")).sorted().forEach(f ->  {
                switch(FileUtil.noExt(f.toFile().getName())) {
                    case "Useful-terms_2023":
                        break;
                    default:
                        System.out.printf("Parsing: %s%n", f.toFile());
                        SuokifApp.process(f.toFile());
                        assertFalse(SuokifVisitor.result.isEmpty());
                        break;
                }
            });
        }
    }

    @Test
    public void test_sumo_kbs() {

        try {
            parsePath(System.getenv("ONTOLOGYPORTAL_GIT") + "/sumo");
        } catch (IOException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void test_individual_kb() {

        try {
            if (relPath == null)
                parsePath(System.getenv("ONTOLOGYPORTAL_GIT") + "/sumo/Geography.kif");
            else
                parsePath(relPath);
        } catch (IOException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {

        if (args != null && !args[0].isEmpty())
            relPath = args[0]; // $ONTOLOGYPORTAL_GIT/sumo or $ONTOLOGYPORTAL_GIT/sumo/Geography.kif
        else {
            System.err.println("Usage: ant -Dtest.path={full path to a *.kif file or directory of *.kif files} test.kif");
            return;
        }

        Request request = Request.method(SUMOParseTest.class, "test_individual_kb");
        Result result = new JUnitCore().run(request);

        for (Failure failure : result.getFailures())
            System.err.println(failure.toString());

        boolean success = result.wasSuccessful();

        if (success)
            System.out.println(SUMOParseTest.class.getName() + "#test_individual_kb " + relPath  + " success: " + success);
        else
            System.err.println(SUMOParseTest.class.getName() + "$test_individual_kb " + relPath  + " failure: " + !success);
    }

} // end class file SUMOParseTest.java