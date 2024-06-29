package org.moddingx.ljc;

import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.io.PrintStream;

public class Log {
    
    @Nullable private static PrintStream info = null;
    @Nullable private static PrintStream error = null;
    @Nullable private static PrintStream all = null;
    
    public static void info(String msg) {
        if (info != null) info.println(msg);
        if (all != null) all.println("[INFO]  " + msg);
    }
    
    public static void error(String msg) {
        if (error != null) error.println(msg);
        if (all != null) all.println("[ERROR] " + msg);
    }
    
    public static void configureLogs(@Nullable OutputStream info, @Nullable OutputStream error, @Nullable OutputStream all) {
        Log.info = wrap(info);
        Log.error = wrap(error);
        Log.all = wrap(all);
    }
    
    private static PrintStream wrap(@Nullable OutputStream out) {
        if (out == null) {
            return null;
        } else if (out instanceof PrintStream ps) {
            return ps;
        } else {
            return new PrintStream(out);
        }
    }
}
