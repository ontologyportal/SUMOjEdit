package com.articulate.sigma;

import errorlist.ErrorSource;

/**
 * Error record for both KIF and TPTP error checking.
 * Represents a single error or warning with location information.
 */
public class ErrRec {
    public final int type;                // ErrorSource.ERROR (0) or ErrorSource.WARNING (1)
    public final String file;
    public final int line, start, end;    // jEdit 0-based line and column positions
    public final String msg;
    
    /**
     * Create an error record.
     * @param type 0 for error, 1 for warning
     * @param file the file path
     * @param line 0-based line number
     * @param start starting column position
     * @param end ending column position
     * @param msg error message
     */
    public ErrRec(int type, String file, int line, int start, int end, String msg) {
        this.type = type;
        this.file = file;
        this.line = line;
        this.start = start;
        this.end = end;
        this.msg = msg;
    }
    
    @Override
    public String toString() {
        String severity = (type == ErrorSource.ERROR || type == 0) ? "ERROR" : "WARNING";
        return String.format("%s:%d:%d: %s: %s", file, line + 1, start, severity, msg);
    }
    
    /**
     * Convert to jEdit ErrorSource type.
     */
    public int getErrorSourceType() {
        return (type == 0) ? ErrorSource.ERROR : ErrorSource.WARNING;
    }
}