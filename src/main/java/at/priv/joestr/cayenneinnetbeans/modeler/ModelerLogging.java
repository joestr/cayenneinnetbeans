package at.priv.joestr.cayenneinnetbeans.modeler;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.cayenne.modeler.log.LogAppender;
import org.apache.cayenne.modeler.log.ModelerLogFactory;
import org.openide.modules.OnStart;

/**
 * Tames the modeler's logging inside the IDE.
 * <p>
 * The modeler jar registers itself as the slf4j provider, so with the modeler on the module
 * classpath every slf4j logger of this module (Cayenne, Velocity, the modeler) ends up in
 * {@link ModelerLogFactory}. Its default appender prints everything from DEBUG up to stderr,
 * which would flood the IDE log, and {@link ModelerLogFactory#setAppender} always keeps that
 * stderr copy. So the appender field is replaced directly: DEBUG and TRACE are dropped, the rest
 * goes to java.util.logging and, while a modeler tab is open, to its log console.
 */
@OnStart
public final class ModelerLogging implements Runnable {

    private static final Logger LOG = Logger.getLogger("org.apache.cayenne");

    private static volatile LogAppender console;

    @Override
    public void run() {
        LogAppender appender = (level, message) -> {
            if ("DEBUG".equals(level) || "TRACE".equals(level)) {
                return;
            }
            LogAppender target = console;
            if (target != null) {
                target.appendMessage(level, message);
            }
            LOG.log(toJul(level), message.stripTrailing());
        };
        try {
            Field field = ModelerLogFactory.class.getDeclaredField("appender");
            field.setAccessible(true);
            field.set(null, appender);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOG.log(Level.INFO, "Cannot redirect CayenneModeler logging", ex);
        }
    }

    /** Sends modeler log messages to the given console (the open tab's), or nowhere for {@code null}. */
    static void setConsole(LogAppender appender) {
        console = appender;
    }

    private static Level toJul(String level) {
        return switch (level) {
            case "ERROR" -> Level.WARNING;
            case "WARN" -> Level.INFO;
            default -> Level.FINE;
        };
    }
}
