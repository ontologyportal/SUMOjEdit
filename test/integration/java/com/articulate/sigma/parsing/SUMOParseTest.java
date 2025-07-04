package com.articulate.sigma.parsing;

import com.articulate.sigma.utils.FileUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.Test;
import static org.junit.Assert.*;

/** Go through the entire SUMO KB and parse, checking for syntax errors
 *
 * @author <a href="mailto:terry.norbraten@gmail.com?subject=com.articulate.sigma.parsing.SUMOParseTest">Terry Norbraten</a>
 */
public class SUMOParseTest {

    private void parsePath(String path) throws IOException {
        Path inPath = Paths.get(path);
        try (Stream<Path> paths = Files.walk(inPath)) {
            paths.filter(f -> f.toString().endsWith(".kif")).sorted().forEach(f ->  {
                switch(FileUtil.noExt(f.toFile().getName())) {
                    case "MilitaryDevices":
                    case "Transportation":
                    case "UXExperimentalTerms":
                    case "Enumerations":
                    case "GDPRTerms":
                    case "MedReason":
                    case "Useful-terms_2023":
                    case "modals":
                    case "TQM3":
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

} // end class file SUMOParseTest.java