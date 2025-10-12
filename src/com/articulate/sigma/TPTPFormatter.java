package com.articulate.sigma;

import com.articulate.sigma.trans.TPTPutil;
import tptp_parser.*;
import java.io.*;
import java.util.*;

/**
 * TPTP formatter for SUMOjEdit.
 * This formatter follows TPTP's specific indentation and line-breaking conventions.
 */
public class TPTPFormatter {
    
    private static String tptp4xPath = System.getenv("TPTP4X_PATH");
    private static boolean preferTPTP4X = false;
    
    /**
     * Format TPTP content.
     */
    public static String format(String contents) {
        if (contents == null || contents.trim().isEmpty()) {
            return contents;
        }
        
        // Try tptp4X first if preferred and available
        if (preferTPTP4X && tptp4xPath != null && !tptp4xPath.isEmpty()) {
            String result = formatWithTPTP4X(contents);
            if (result != null) {
                return result;
            }
        }
        
        return formatDirect(contents);
    }
    
    /**
     * Direct formatting on raw text.
     */
    private static String formatDirect(String content) {
        StringBuilder result = new StringBuilder();
        StringBuilder currentStatement = new StringBuilder();
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            
            if (line.isEmpty()) {
                continue;
            }
            
            // Handle comments
            if (line.startsWith("%")) {
                if (currentStatement.length() > 0) {
                    result.append(formatTPTPStatement(currentStatement.toString()));
                    result.append("\n\n");
                    currentStatement = new StringBuilder();
                }
                result.append(line).append("\n");
                continue;
            }
            
            // Accumulate statement
            currentStatement.append(line).append(" ");
            
            // Check if complete
            if (line.endsWith(").")) {
                result.append(formatTPTPStatement(currentStatement.toString()));
                result.append("\n\n");
                currentStatement = new StringBuilder();
            }
        }
        
        // Handle remaining
        if (currentStatement.length() > 0) {
            String stmt = currentStatement.toString().trim();
            if (!stmt.isEmpty()) {
                result.append(formatTPTPStatement(stmt));
                result.append("\n\n");
            }
        }
        
        return result.toString().trim();
    }
    
    /**
     * Format a single TPTP statement.
     */
    private static String formatTPTPStatement(String statement) {
        statement = statement.trim();
        
        if (!statement.matches("^(thf|tff|fof|cnf|tpi)\\s*\\(.*")) {
            return statement;
        }
        
        try {
            // Extract type
            int firstParen = statement.indexOf('(');
            String type = statement.substring(0, firstParen).trim();
            
            // Get content
            String remainder = statement.substring(firstParen + 1);
            if (remainder.endsWith(").")) {
                remainder = remainder.substring(0, remainder.length() - 2).trim();
            } else if (remainder.endsWith(")")) {
                remainder = remainder.substring(0, remainder.length() - 1).trim();
            }
            
            // Parse components
            TPTPComponents comp = parseComponents(remainder);
            if (comp == null) {
                return statement;
            }
            
            // Build output
            StringBuilder sb = new StringBuilder();
            sb.append("    ").append(type).append("(");
            sb.append(comp.name).append(",").append(comp.role).append(",\n");
            
            // Format formula
            String formatted = formatFormula(comp.formula);
            sb.append(formatted);
            
            // Add source
            if (comp.source != null && !comp.source.isEmpty()) {
                sb.append(",\n        ").append(comp.source);
            }
            
            sb.append(" ).");
            
            return sb.toString();
            
        } catch (Exception e) {
            System.err.println("Error formatting: " + e.getMessage());
            return statement;
        }
    }
    
    /**
     * Parse components of a TPTP statement.
     */
    private static TPTPComponents parseComponents(String content) {
        List<String> parts = splitTopLevel(content, ',');
        
        if (parts.size() < 3) {
            return null;
        }
        
        TPTPComponents comp = new TPTPComponents();
        comp.name = parts.get(0).trim();
        comp.role = parts.get(1).trim();
        comp.formula = parts.get(2).trim();
        
        // Source (4th component)
        if (parts.size() > 3) {
            StringBuilder source = new StringBuilder();
            for (int i = 3; i < parts.size(); i++) {
                if (i > 3) source.append(",");
                source.append(parts.get(i).trim());
            }
            comp.source = source.toString();
        }
        
        return comp;
    }
    
    /**
     * Split by delimiter at top level only.
     */
    private static List<String> splitTopLevel(String s, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        char stringChar = 0;
        
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            
            // Strings
            if (!inString && (c == '\'' || c == '"')) {
                inString = true;
                stringChar = c;
                current.append(c);
                continue;
            }
            
            if (inString) {
                current.append(c);
                if (c == stringChar && (i == 0 || s.charAt(i - 1) != '\\')) {
                    inString = false;
                }
                continue;
            }
            
            // Depth tracking
            if (c == '(' || c == '[') {
                depth++;
                current.append(c);
                continue;
            }
            
            if (c == ')' || c == ']') {
                depth--;
                current.append(c);
                continue;
            }
            
            // Split at delimiter
            if (c == delimiter && depth == 0) {
                result.add(current.toString());
                current = new StringBuilder();
                continue;
            }
            
            current.append(c);
        }
        
        if (current.length() > 0) {
            result.add(current.toString());
        }
        
        return result;
    }
    
    /**
     * Format the formula body with TPTP conventions.
     * Key rules:
     * 1. Negation stays with quantifier: "~ ! [A: w] :"
     * 2. Implication followed by quantifier: "=> ? [B: $i] :"
     * 3. Nested quantifiers with & at same level
     * 4. No extra parentheses around simple expressions
     */
    private static String formatFormula(String formula) {
        formula = formula.trim();
        
        // Remove outermost parentheses if they wrap everything
        formula = stripOutermostParens(formula);
        
        FormulaFormatter ff = new FormulaFormatter();
        ff.format(formula);
        return ff.getResult();
    }
    
    /**
     * Strip outermost parentheses only if they wrap the entire expression.
     */
    private static String stripOutermostParens(String s) {
        s = s.trim();
        if (!s.startsWith("(") || !s.endsWith(")")) {
            return s;
        }
        
        // Check if outer parens actually wrap everything
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '(') depth++;
            if (s.charAt(i) == ')') depth--;
            if (depth == 0 && i < s.length() - 1) {
                return s; // Outer parens don't wrap everything
            }
        }
        
        return s.substring(1, s.length() - 1).trim();
    }
    
    /**
     * Formatter class that tracks state and builds formatted output.
     */
    private static class FormulaFormatter {
        private StringBuilder result = new StringBuilder();
        private String input;
        private int pos;
        private int indent = 2; // Start at 2 (8 spaces)
        private boolean needsIndent = true;
        
        void format(String formula) {
            this.input = formula;
            this.pos = 0;
            processExpression();
        }
        
        String getResult() {
            return result.toString();
        }
        
        private void processExpression() {
            while (pos < input.length()) {
                skipWhitespace();
                if (pos >= input.length()) break;
                
                char c = input.charAt(pos);
                
                // Negation
                if (c == '~') {
                    if (needsIndent) addIndent();
                    result.append("~ ");
                    pos++;
                    needsIndent = false;
                    
                    // Check if quantifier follows
                    skipWhitespace();
                    if (pos < input.length() && (input.charAt(pos) == '!' || input.charAt(pos) == '?')) {
                        // Keep going on same line
                        continue;
                    }
                    continue;
                }
                
                // Quantifiers
                if ((c == '!' || c == '?') && isQuantifier(pos)) {
                    if (needsIndent) addIndent();
                    
                    // Add quantifier and variable declaration
                    result.append(c).append(" ");
                    pos++;
                    skipWhitespace();
                    
                    // Add variable list [...]
                    if (pos < input.length() && input.charAt(pos) == '[') {
                        int closeBracket = findClosingBracket(pos);
                        if (closeBracket > pos) {
                            result.append(input.substring(pos, closeBracket + 1));
                            pos = closeBracket + 1;
                            skipWhitespace();
                            
                            // Add colon if present
                            if (pos < input.length() && input.charAt(pos) == ':') {
                                result.append(" :\n");
                                pos++;
                                indent++;
                                needsIndent = true;
                                continue;
                            }
                        }
                    }
                    needsIndent = false;
                    continue;
                }
                
                // Implication
                if (c == '=' && pos + 1 < input.length() && input.charAt(pos + 1) == '>') {
                    result.append("\n");
                    indent++;
                    addIndent();
                    result.append("=> ");
                    pos += 2;
                    needsIndent = false;
                    
                    // Check if quantifier follows
                    skipWhitespace();
                    if (pos < input.length() && (input.charAt(pos) == '!' || input.charAt(pos) == '?')) {
                        // Keep on same line
                        continue;
                    }
                    continue;
                }
                
                // Conjunction
                if (c == '&') {
                    result.append("\n");
                    addIndent();
                    result.append("& ");
                    pos++;
                    needsIndent = false;
                    continue;
                }
                
                // Disjunction
                if (c == '|') {
                    result.append("\n");
                    addIndent();
                    result.append("| ");
                    pos++;
                    needsIndent = false;
                    continue;
                }
                
                // Opening parenthesis
                if (c == '(') {
                    if (needsIndent) addIndent();
                    result.append("( ");
                    pos++;
                    needsIndent = false;
                    continue;
                }
                
                // Closing parenthesis
                if (c == ')') {
                    result.append(" )");
                    pos++;
                    needsIndent = false;
                    
                    // Check if we should decrease indent
                    skipWhitespace();
                    if (pos < input.length() && input.charAt(pos) == ')') {
                        indent = Math.max(2, indent - 1);
                    }
                    continue;
                }
                
                // @ symbol
                if (c == '@') {
                    result.append(" @ ");
                    pos++;
                    needsIndent = false;
                    continue;
                }
                
                // Comma
                if (c == ',') {
                    result.append(",");
                    pos++;
                    needsIndent = false;
                    continue;
                }
                
                // Regular tokens (identifiers, numbers, $-terms, etc.)
                if (needsIndent) addIndent();
                int tokenEnd = findTokenEnd(pos);
                result.append(input.substring(pos, tokenEnd));
                pos = tokenEnd;
                needsIndent = false;
            }
        }
        
        private boolean isQuantifier(int p) {
            if (p >= input.length()) return false;
            char c = input.charAt(p);
            if (c != '!' && c != '?') return false;
            
            // Look ahead for [
            int next = p + 1;
            while (next < input.length() && Character.isWhitespace(input.charAt(next))) {
                next++;
            }
            return next < input.length() && input.charAt(next) == '[';
        }
        
        private int findClosingBracket(int start) {
            if (start >= input.length() || input.charAt(start) != '[') return -1;
            
            int depth = 0;
            for (int i = start; i < input.length(); i++) {
                if (input.charAt(i) == '[') depth++;
                if (input.charAt(i) == ']') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
            return -1;
        }
        
        private int findTokenEnd(int start) {
            int i = start;
            boolean inString = false;
            char stringChar = 0;
            
            while (i < input.length()) {
                char c = input.charAt(i);
                
                // Handle strings
                if (!inString && (c == '\'' || c == '"')) {
                    inString = true;
                    stringChar = c;
                    i++;
                    continue;
                }
                
                if (inString) {
                    if (c == stringChar && (i == 0 || input.charAt(i - 1) != '\\')) {
                        inString = false;
                    }
                    i++;
                    continue;
                }
                
                // Break on special chars
                if ("()[]&|~@,:=>".indexOf(c) >= 0 || Character.isWhitespace(c)) {
                    break;
                }
                
                i++;
            }
            
            return i;
        }
        
        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }
        
        private void addIndent() {
            for (int i = 0; i < indent * 4; i++) {
                result.append(' ');
            }
        }
    }
    
    /**
     * Format using external tptp4X tool.
     */
    private static String formatWithTPTP4X(String contents) {
        try {
            File tempInput = File.createTempFile("tptp_format_in_", ".tptp");
            File tempOutput = File.createTempFile("tptp_format_out_", ".tptp");
            tempInput.deleteOnExit();
            tempOutput.deleteOnExit();
            
            try (FileWriter writer = new FileWriter(tempInput)) {
                writer.write(contents);
            }
            
            ProcessBuilder pb = new ProcessBuilder(
                tptp4xPath, "-f", "tptp:short",
                tempInput.getAbsolutePath(),
                "-o", tempOutput.getAbsolutePath()
            );
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0 && tempOutput.exists()) {
                StringBuilder result = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(tempOutput))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line).append("\n");
                    }
                }
                return result.toString();
            }
            
        } catch (Exception e) {
            System.err.println("Could not format with tptp4X: " + e.getMessage());
        }
        
        return null;
    }
    
    public static void setTPTP4XPath(String path) {
        tptp4xPath = path;
    }
    
    public static void setPreferTPTP4X(boolean prefer) {
        preferTPTP4X = prefer;
    }
    
    private static class TPTPComponents {
        String name;
        String role;
        String formula;
        String source;
    }
}