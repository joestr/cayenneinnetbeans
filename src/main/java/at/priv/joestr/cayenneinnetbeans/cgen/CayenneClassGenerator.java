package at.priv.joestr.cayenneinnetbeans.cgen;

import at.priv.joestr.cayenneinnetbeans.CayenneFiles;
import java.io.File;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.cayenne.configuration.DataChannelDescriptorLoader;
import org.apache.cayenne.configuration.DataMapLoader;
import org.apache.cayenne.configuration.xml.DataChannelMetaData;
import org.apache.cayenne.dbsync.reverse.configuration.ToolsModule;
import org.apache.cayenne.di.Injector;
import org.apache.cayenne.gen.CgenConfigList;
import org.apache.cayenne.gen.CgenConfiguration;
import org.apache.cayenne.gen.ClassGenerationAction;
import org.apache.cayenne.gen.ClassGenerationActionFactory;
import org.apache.cayenne.map.DataMap;
import org.apache.cayenne.resource.URLResource;
import org.apache.cayenne.tools.ToolsInjectorBuilder;
import org.apache.cayenne.validation.ValidationFailure;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.SourceGroup;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;

/**
 * Runs Cayenne class generation (cgen) in the IDE, the way the Maven plugin does when it has no
 * configuration of its own: every cgen configuration stored in a DataMap by CayenneModeler is
 * honored; a DataMap without one gets the defaults, generating into the owning project's Java
 * source folder.
 */
@Messages({
    "LBL_CgenOutput=Cayenne cgen",
    "# {0} - file name",
    "MSG_CgenStarting=Generating classes for {0}",
    "# {0} - DataMap name",
    "# {1} - output directory",
    "MSG_CgenMap=DataMap \"{0}\" -> {1}",
    "# {0} - DataMap name",
    "MSG_CgenStoredConfig=DataMap \"{0}\": using the cgen configuration stored in the map",
    "# {0} - DataMap name",
    "ERR_CgenNoOutputDir=DataMap \"{0}\": no output directory. Set one in CayenneModeler, or put the map in a project with a Java source folder.",
    "MSG_CgenFinished=Class generation finished.",
    "MSG_CgenFailed=Class generation failed, see the Cayenne cgen output."
})
final class CayenneClassGenerator {

    private static final String SOURCES_TYPE_JAVA = "java"; // JavaProjectConstants.SOURCES_TYPE_JAVA

    private static final RequestProcessor RP = new RequestProcessor(CayenneClassGenerator.class.getName(), 1);

    private CayenneClassGenerator() {
    }

    /** Generates classes for a DataMap, or for every DataMap of a project file, in the background. */
    static void generate(FileObject file) {
        RP.post(() -> run(file));
    }

    private static void run(FileObject file) {
        InputOutput io = IOProvider.getDefault().getIO(Bundle.LBL_CgenOutput(), true);
        io.select();
        OutputLogger logger = new OutputLogger(io);
        logger.info(Bundle.MSG_CgenStarting(FileUtil.getFileDisplayName(file)));

        // Cayenne loads its DI modules, templates and Velocity tools through the context class
        // loader; in the IDE that must be the loader that sees the bundled Cayenne jars.
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(CayenneClassGenerator.class.getClassLoader());
        Injector injector = null;
        Set<File> outputDirs = new LinkedHashSet<>();
        boolean ok = true;
        try {
            injector = new ToolsInjectorBuilder().addModule(new ToolsModule(logger)).create();
            Path defaultOutputDir = defaultOutputDir(file);
            ClassGenerationActionFactory factory = injector.getInstance(ClassGenerationActionFactory.class);

            for (DataMap map : loadMaps(injector, file, logger)) {
                for (CgenConfiguration config : configurations(injector, map, defaultOutputDir, logger)) {
                    Path outputDir = config.outputDirectory().orElse(null);
                    if (outputDir == null) {
                        logger.error(Bundle.ERR_CgenNoOutputDir(map.getName()));
                        ok = false;
                        continue;
                    }
                    logger.info(Bundle.MSG_CgenMap(map.getName(), outputDir));
                    ClassGenerationAction action = factory.createAction(config, logger);
                    action.prepareArtifacts();
                    action.execute();
                    outputDirs.add(outputDir.toFile());
                }
            }
        } catch (Exception ex) {
            logger.error(ex.getLocalizedMessage() != null ? ex.getLocalizedMessage() : ex.toString(), ex);
            ok = false;
        } finally {
            if (injector != null) {
                injector.shutdown();
            }
            thread.setContextClassLoader(previous);
            outputDirs.forEach(FileUtil::refreshFor);
        }

        String result = ok ? Bundle.MSG_CgenFinished() : Bundle.MSG_CgenFailed();
        logger.info(result);
        StatusDisplayer.getDefault().setStatusText(result);
        io.getOut().close();
        io.getErr().close();
    }

    private static Collection<DataMap> loadMaps(Injector injector, FileObject file, OutputLogger logger)
            throws MalformedURLException {
        URLResource resource = new URLResource(FileUtil.toFile(file).toURI().toURL());
        if (CayenneFiles.isProject(file)) {
            var tree = injector.getInstance(DataChannelDescriptorLoader.class).load(resource);
            for (ValidationFailure failure : tree.getLoadFailures()) {
                logger.warn(failure.getDescription());
            }
            return tree.getRootNode().getDataMaps();
        }
        return List.of(injector.getInstance(DataMapLoader.class).load(resource));
    }

    private static List<CgenConfiguration> configurations(Injector injector, DataMap map, Path defaultOutputDir,
            OutputLogger logger) {
        CgenConfigList stored = injector.getInstance(DataChannelMetaData.class).get(map, CgenConfigList.class);
        if (stored != null && !stored.getAll().isEmpty()) {
            logger.info(Bundle.MSG_CgenStoredConfig(map.getName()));
            return new ArrayList<>(stored.getAll());
        }
        return List.of(CgenConfiguration.createDefault(map, defaultOutputDir));
    }

    /**
     * The main Java source folder of the project owning the file, or {@code null} to let Cayenne
     * derive one (it looks for a Maven layout above the map).
     */
    private static Path defaultOutputDir(FileObject file) {
        Project project = FileOwnerQuery.getOwner(file);
        if (project == null) {
            return null;
        }
        SourceGroup fallback = null;
        for (SourceGroup group : ProjectUtils.getSources(project).getSourceGroups(SOURCES_TYPE_JAVA)) {
            String path = group.getRootFolder().getPath();
            if (!path.contains("/test/") && !path.endsWith("/test")) {
                return FileUtil.toFile(group.getRootFolder()).toPath();
            }
            if (fallback == null) {
                fallback = group;
            }
        }
        return fallback != null ? FileUtil.toFile(fallback.getRootFolder()).toPath() : null;
    }
}
