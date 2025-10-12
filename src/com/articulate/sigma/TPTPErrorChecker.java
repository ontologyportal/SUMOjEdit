package com.articulate.sigma;

import com.articulate.sigma.utils.FileUtil;
import com.articulate.sigma.utils.StringUtil;
import tptp_parser.*;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TPTP syntax and semantic checker for SUMOjEdit.
 * This class checks TPTP files for errors and warnings without UI dependencies.
 */
public class TPTPErrorChecker {

    public static boolean debug = true;
    private static String tptp4xPath = System.getenv("TPTP4X_PATH");
    
    /**
     * Command-line entry point for testing.
     * Usage: java com.articulate.sigma.TPTPErrorChecker -c file.tptp
     */
    public static void main(String[] args) {
        if (args == null || args.length == 0 || "-h".equals(args[0])) {
            showHelp();
            return;
        }
        
        if ("-c".equals(args[0]) && args.length > 1) {
            String fname = args[1];
            try {
                String contents = String.join("\n", FileUtil.readLines(fname));
                List<ErrRec> errors = check(contents, fname);
                
                System.out.println("*******************************************************");
                if (errors.isEmpty()) {
                    System.out.println("No errors found in " + fname);
                } else {
                    System.out.println("Diagnostics for " + fname + ":");
                    for (ErrRec e : errors) {
                        System.out.println(e.toString());
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to read or check file: " + fname);
                e.printStackTrace();
            }
        } else {
            showHelp();
        }
    }
    
    /**
     * Display help information.
     */
    public static void showHelp() {
        System.out.println("TPTPErrorChecker - TPTP syntax/semantic checker");
        System.out.println("Options:");
        System.out.println("  -h              Show this help screen");
        System.out.println("  -c <file.tptp>  Check the given TPTP file");
        System.out.println("  -x <path>       Set path to tptp4X executable");
    }
    
    /**
     * Check TPTP content for errors and warnings.
     * @param contents the TPTP text to check
     * @param fileName the file name for error reporting
     * @return list of error records
     */
    public static List<ErrRec> check(String contents, String fileName) {
        List<ErrRec> msgs = new ArrayList<>();
        
        if (contents == null || contents.trim().isEmpty()) {
            return msgs;
        }
        
        // First, try parsing with the TPTP parser from tptp_parser package
        checkWithTPTPParser(contents, fileName, msgs);
        
        // If tptp4X is available, use it for additional checking
        if (tptp4xPath != null && !tptp4xPath.isEmpty()) {
            checkWithTPTP4X(contents, fileName, msgs);
        }
        
        // Sort messages by line number
        msgs.sort((e1, e2) -> {
            int c = Integer.compare(e1.line, e2.line);
            if (c != 0) return c;
            c = Integer.compare(e1.start, e2.start);
            if (c != 0) return c;
            return Integer.compare(e1.type, e2.type);
        });
        
        return msgs;
    }
    
    /**
     * Check using the built-in TPTP parser.
     */
    private static void checkWithTPTPParser(String contents, String fileName, List<ErrRec> msgs) {
        try {
            // Use TPTPVisitor to parse the content
            TPTPVisitor visitor = new TPTPVisitor();
            
            // Capture any parse exceptions as errors
            try {
                visitor.parseString(contents);
            } catch (Exception parseEx) {
                // Parse error occurred
                int line = extractLineNumber(parseEx.getMessage());
                int col = extractColumnNumber(parseEx.getMessage());
                msgs.add(new ErrRec(0, fileName, line, col, col + 1, 
                    "Parse error: " + parseEx.getMessage()));
                return; // Don't try to validate if parse failed
            }
            
            // Validate the parsed formulas
            Map<String, TPTPFormula> formulas = visitor.result;
            if (formulas != null) {
                validateFormulas(formulas, fileName, msgs);
            }
            
        } catch (Exception e) {
            msgs.add(new ErrRec(0, fileName, 0, 0, 1, 
                "Parse error: " + e.getMessage()));
        }
    }
    
    /**
     * Validate parsed TPTP formulas for semantic issues.
     */
    private static void validateFormulas(Map<String, TPTPFormula> formulas, 
                                        String fileName, List<ErrRec> msgs) {
        if (formulas == null) return;
        
        Set<String> definedSymbols = new HashSet<>();
        Set<String> usedSymbols = new HashSet<>();
        
        for (Map.Entry<String, TPTPFormula> entry : formulas.entrySet()) {
            TPTPFormula formula = entry.getValue();
            
            // Check formula structure
            if (formula == null) {
                msgs.add(new ErrRec(1, fileName, 0, 0, 1, 
                    "Null formula found for: " + entry.getKey()));
                continue;
            }
            
            // Check for duplicate formula names
            String formulaName = formula.name;
            if (formulaName != null && definedSymbols.contains(formulaName)) {
                msgs.add(new ErrRec(1, fileName, 0, 0, 1, 
                    "Duplicate formula name: " + formulaName));
            } else if (formulaName != null) {
                definedSymbols.add(formulaName);
            }
            
            // Collect symbols used in the formula
            collectSymbols(formula.formula, usedSymbols);
            
            // Check role validity
            String role = formula.role;
            if (!isValidRole(role)) {
                msgs.add(new ErrRec(1, fileName, 0, 0, 1, 
                    "Invalid formula role: " + role));
            }
        }
        
        // Check for undefined symbols
        for (String symbol : usedSymbols) {
            if (!isBuiltInSymbol(symbol) && !definedSymbols.contains(symbol)) {
                // This is just a warning since symbols might be defined externally
                if (debug) {
                    System.out.println("Warning: Undefined symbol: " + symbol);
                }
            }
        }
    }
    
    /**
     * Check if a role is valid in TPTP.
     */
    private static boolean isValidRole(String role) {
        if (role == null) return false;
        
        Set<String> validRoles = new HashSet<>(Arrays.asList(
            "axiom", "hypothesis", "definition", "assumption",
            "lemma", "theorem", "corollary", "conjecture",
            "negated_conjecture", "plain", "type", "fi_domain",
            "fi_functors", "fi_predicates", "unknown"
        ));
        
        return validRoles.contains(role.toLowerCase());
    }
    
    /**
     * Check if a symbol is a built-in TPTP symbol.
     */
    private static boolean isBuiltInSymbol(String symbol) {
        // Add common built-in TPTP symbols
        Set<String> builtIns = new HashSet<>(Arrays.asList(
            "$true", "$false", "$distinct", "$less", "$lesseq",
            "$greater", "$greatereq", "$evaleq", "$is_int", "$is_rat",
            "$box", "$dia", "$necessary", "$possible", "$knows",
            "$believes", "$obligatory", "$permissible", "$forbidden"
        ));
        
        return builtIns.contains(symbol) || symbol.startsWith("$");
    }
    
    /**
     * Collect all symbols used in a formula string.
     */
    private static void collectSymbols(String formula, Set<String> symbols) {
        if (formula == null) return;
        
        // Simple pattern to extract identifiers
        Pattern pattern = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b");
        Matcher matcher = pattern.matcher(formula);
        
        while (matcher.find()) {
            String symbol = matcher.group();
            // Skip logical operators
            if (!isLogicalOperator(symbol)) {
                symbols.add(symbol);
            }
        }
    }
    
    /**
     * Check if a token is a logical operator.
     */
    private static boolean isLogicalOperator(String token) {
        Set<String> operators = new HashSet<>(Arrays.asList(
            "and", "or", "not", "implies", "iff", "xor",
            "forall", "exists", "lambda"
        ));
        return operators.contains(token.toLowerCase());
    }
    
    /**
     * Check using external tptp4X tool if available.
     */
    private static void checkWithTPTP4X(String contents, String fileName, List<ErrRec> msgs) {
        try {
            // Write contents to temp file
            File tempFile = File.createTempFile("tptp_check_", ".tptp");
            tempFile.deleteOnExit();
            
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(contents);
            }
            
            // Run tptp4X
            ProcessBuilder pb = new ProcessBuilder(
                tptp4xPath, "-c", tempFile.getAbsolutePath()
            );
            
            Process process = pb.start();
            
            // Read output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("ERROR") || line.contains("Warning")) {
                        int lineNum = extractLineNumber(line);
                        int type = line.contains("ERROR") ? 0 : 1;
                        msgs.add(new ErrRec(type, fileName, lineNum, 0, 1, line));
                    }
                }
            }
            
            // Read error stream
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        msgs.add(new ErrRec(0, fileName, 0, 0, 1, 
                            "tptp4X error: " + line));
                    }
                }
            }
            
            process.waitFor();
            
        } catch (Exception e) {
            if (debug) {
                System.err.println("Could not run tptp4X: " + e.getMessage());
            }
        }
    }
    
    /**
     * Extract line number from error message.
     */
    private static int extractLineNumber(String message) {
        Pattern p = Pattern.compile("line[\\s:]*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1)) - 1; // Convert to 0-based
            } catch (NumberFormatException e) {
                // Fall through
            }
        }
        
        // Try another pattern
        p = Pattern.compile("(\\d+):");
        m = p.matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1)) - 1;
            } catch (NumberFormatException e) {
                // Fall through
            }
        }
        
        return 0;
    }
    
    /**
     * Extract column number from error message.
     */
    private static int extractColumnNumber(String message) {
        Pattern p = Pattern.compile(":(\\d+):(\\d+)");
        Matcher m = p.matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(2));
            } catch (NumberFormatException e) {
                // Fall through
            }
        }
        
        return 0;
    }
    
    /**
     * Set the path to the tptp4X executable.
     */
    public static void setTPTP4XPath(String path) {
        tptp4xPath = path;
    }
}