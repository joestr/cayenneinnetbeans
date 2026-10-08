package at.priv.joestr.cayenneinnetbeans.modeler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileUtil;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;
import org.openide.windows.OutputWriter;

/**
 * Launches the bundled CayenneModeler. It runs in its own JVM: the modeler is a standalone Swing
 * application that installs its own look and feel and expects to own the process, neither of
 * which is safe inside the IDE.
 */
@Messages({
    "LBL_ModelerOutput=CayenneModeler",
    "# {0} - command line",
    "MSG_ModelerStarting=Starting CayenneModeler: {0}",
    "# {0} - exit code",
    "MSG_ModelerExited=CayenneModeler exited with code {0}.",
    "# {0} - missing path",
    "ERR_ModelerMissing=The bundled CayenneModeler was not found ({0}). Try reinstalling the Cayenne plugin.",
    "# {0} - error message",
    "ERR_ModelerFailed=CayenneModeler could not be started: {0}"
})
public final class CayenneModeler {

    private static final String CODE_NAME_BASE = "at.priv.joestr.cayenneinnetbeans";
    /** Where the nbm-maven-plugin puts this module's library jars inside the cluster. */
    private static final String EXT_DIR = "modules/ext/" + CODE_NAME_BASE;
    private static final String MAIN_CLASS = "org.apache.cayenne.modeler.Main";

    private static final RequestProcessor RP = new RequestProcessor(CayenneModeler.class.getName(), 4);

    private CayenneModeler() {
    }

    /**
     * Starts CayenneModeler in the background.
     *
     * @param project a {@code cayenne-*.xml} project file to open, or {@code null} for an empty modeler
     */
    public static void launch(File project) {
        RP.post(() -> run(project));
    }

    private static void run(File project) {
        File extDir = InstalledFileLocator.getDefault().locate(EXT_DIR, CODE_NAME_BASE, false);
        if (extDir == null || !extDir.isDirectory()) {
            error(Bundle.ERR_ModelerMissing(EXT_DIR));
            return;
        }

        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-cp");
        try {
            command.add(classpath(extDir.toPath()));
        } catch (IOException ex) {
            error(Bundle.ERR_ModelerFailed(ex.getLocalizedMessage()));
            return;
        }
        command.add(MAIN_CLASS);
        if (project != null) {
            command.add(project.getAbsolutePath());
        }

        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (project != null) {
            builder.directory(project.getParentFile());
        }

        InputOutput io = IOProvider.getDefault().getIO(Bundle.LBL_ModelerOutput(), false);
        OutputWriter out = io.getOut();
        out.println(Bundle.MSG_ModelerStarting(command.get(0) + " -cp " + extDir + File.separator + "** " + MAIN_CLASS
                + (project != null ? " " + project.getAbsolutePath() : "")));
        try {
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.println(line);
                }
            }
            int exitCode = process.waitFor();
            out.println(Bundle.MSG_ModelerExited(exitCode));
        } catch (IOException ex) {
            error(Bundle.ERR_ModelerFailed(ex.getLocalizedMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            out.close();
            // The modeler may have rewritten project and map files; let the IDE pick that up.
            if (project != null) {
                FileUtil.refreshFor(project.getParentFile());
            }
        }
    }

    private static String javaExecutable() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
        Path java = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
        return java.toString();
    }

    private static String classpath(Path extDir) throws IOException {
        try (Stream<Path> files = Files.walk(extDir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.joining(File.pathSeparator));
        }
    }

    private static void error(String message) {
        DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(message, NotifyDescriptor.ERROR_MESSAGE));
    }
}
