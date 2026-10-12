package com.carwash.shared.application;

/** Stack locations only: never render Throwable.toString(), messages, or nested causes. */
public final class SafeDiagnostics {
    private SafeDiagnostics() { }
    public static String location(Throwable failure) {
        for (StackTraceElement frame : failure.getStackTrace()) {
            if (frame.getClassName().startsWith("com.carwash.")) {
                return frame.getClassName() + ":" + frame.getLineNumber();
            }
        }
        return "framework";
    }
}
