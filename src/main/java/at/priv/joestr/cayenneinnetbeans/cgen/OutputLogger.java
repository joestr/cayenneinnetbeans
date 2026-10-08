package at.priv.joestr.cayenneinnetbeans.cgen;

import org.openide.windows.InputOutput;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;

/**
 * Routes Cayenne's slf4j logging into an IDE output tab. Debug and trace are dropped, as they
 * would drown the per-file messages cgen prints at info level.
 */
final class OutputLogger extends LegacyAbstractLogger {

    private static final long serialVersionUID = 1L;

    private final transient InputOutput io;

    OutputLogger(InputOutput io) {
        this.io = io;
        this.name = "cgen";
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern,
            Object[] arguments, Throwable throwable) {
        String message = MessageFormatter.basicArrayFormat(messagePattern, arguments);
        if (level == Level.ERROR || level == Level.WARN) {
            io.getErr().println(level + ": " + message);
        } else {
            io.getOut().println(message);
        }
        if (throwable != null) {
            throwable.printStackTrace(io.getErr());
        }
    }
}
