package at.priv.joestr.cayenneinnetbeans.modeler;

import at.priv.joestr.cayenneinnetbeans.CayenneFiles;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.netbeans.api.actions.Savable;
import org.netbeans.spi.actions.AbstractSavable;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * The CayenneModeler tab. There is at most one: the modeler works on one project at a time, and
 * some of its dialogs are shared static instances that would not cope with two modelers.
 * <p>
 * Unsaved modeler changes are exposed as a {@link Savable}, so the IDE's Save and Save All work on
 * them and the IDE asks about them on exit.
 */
@Messages({
    "LBL_ModelerTab=CayenneModeler",
    "# {0} - project file name",
    "LBL_ModelerTabProject=CayenneModeler: {0}",
    "# {0} - error message",
    "ERR_ModelerTabFailed=CayenneModeler cannot run in a tab: {0}",
    "BTN_OpenSeparateWindow=Open in Separate Window",
    "# {0} - list of file names",
    "MSG_SaveEditorsFirst=These Cayenne files have unsaved changes in the editor:\n{0}\n\n"
            + "CayenneModeler reads the files from disk. Save them first?"
})
public final class CayenneModelerTopComponent extends TopComponent {

    private static final Logger LOG = Logger.getLogger(CayenneModelerTopComponent.class.getName());
    private static final String PREFERRED_ID = "CayenneModelerTopComponent";

    private static CayenneModelerTopComponent instance;

    private final InstanceContent content = new InstanceContent();
    private final ModelerSavable savable = new ModelerSavable();
    private final ComponentListener mainWindowListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            syncBounds();
        }

        @Override
        public void componentMoved(ComponentEvent e) {
            syncBounds();
        }
    };

    private EmbeddedModeler modeler;
    /** The project last asked for, offered to the standalone modeler if embedding fails. */
    private File requestedProject;
    private File project;
    private boolean modified;

    private CayenneModelerTopComponent() {
        setLayout(new BorderLayout());
        setName(Bundle.LBL_ModelerTab());
        setIcon(ImageUtilities.loadImage(CayenneFiles.ICON_MODELER));
        associateLookup(new AbstractLookup(content));
    }

    /**
     * Opens the modeler tab, and the given project in it.
     *
     * @param projectFile a {@code cayenne-*.xml} file, or {@code null} to just show the tab
     */
    public static void showProject(File projectFile) {
        if (projectFile != null && !saveOpenEditors(projectFile)) {
            return;
        }
        if (instance == null) {
            instance = new CayenneModelerTopComponent();
        }
        CayenneModelerTopComponent tc = instance;
        if (!tc.isOpened()) {
            Mode editor = WindowManager.getDefault().findMode("editor");
            if (editor != null) {
                editor.dockInto(tc);
            }
            tc.open();
        }
        tc.requestActive();
        if (projectFile != null) {
            tc.requestedProject = projectFile;
        }
        if (projectFile != null && tc.modeler != null) {
            tc.modeler.open(projectFile);
        }
    }

    @Override
    public int getPersistenceType() {
        return PERSISTENCE_NEVER;
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    @Override
    protected void componentOpened() {
        try {
            modeler = EmbeddedModeler.create(new EmbeddedModeler.Host() {
                @Override
                public void projectChanged(File projectFile, boolean isModified) {
                    onProjectChanged(projectFile, isModified);
                }

                @Override
                public void closeRequested() {
                    EventQueue.invokeLater(CayenneModelerTopComponent.this::close);
                }
            });
            add(modeler.getComponent(), BorderLayout.CENTER);
            Frame mainWindow = WindowManager.getDefault().getMainWindow();
            mainWindow.addComponentListener(mainWindowListener);
            syncBounds();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            LOG.log(Level.WARNING, "Cannot embed CayenneModeler", ex);
            modeler = null;
            add(errorPanel(ex), BorderLayout.CENTER);
        }
        revalidate();
    }

    @Override
    public boolean canClose() {
        return modeler == null || modeler.closeProject();
    }

    @Override
    protected void componentClosed() {
        WindowManager.getDefault().getMainWindow().removeComponentListener(mainWindowListener);
        if (modeler != null) {
            modeler.dispose();
            modeler = null;
        }
        onProjectChanged(null, false);
        removeAll();
    }

    private void onProjectChanged(File projectFile, boolean isModified) {
        boolean saved = modified && !isModified && projectFile != null && projectFile.equals(project);
        project = projectFile;
        modified = isModified;

        String name = projectFile != null ? Bundle.LBL_ModelerTabProject(projectFile.getName()) : Bundle.LBL_ModelerTab();
        setDisplayName(name);
        setHtmlDisplayName(isModified ? "<html><b>" + escapeHtml(name) + "</b>" : null);
        setToolTipText(projectFile != null ? projectFile.getAbsolutePath() : null);

        if (isModified) {
            savable.activate();
        } else {
            savable.deactivate();
        }
        // the modeler wrote (or reverted) project and map files; let open editors pick that up
        if (saved) {
            FileUtil.refreshFor(projectFile.getParentFile());
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void syncBounds() {
        if (modeler != null) {
            modeler.setReferenceBounds(WindowManager.getDefault().getMainWindow().getBounds());
        }
    }

    private JPanel errorPanel(Throwable error) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(new JLabel(Bundle.ERR_ModelerTabFailed(error.toString())));
        JButton separate = new JButton(Bundle.BTN_OpenSeparateWindow());
        separate.addActionListener(e -> {
            close();
            CayenneModeler.launch(requestedProject);
        });
        panel.add(separate);
        return panel;
    }

    /**
     * The modeler reads project and maps from disk, so unsaved editor changes to them would be
     * lost or overwritten. Offers to save them first.
     *
     * @return {@code false} if the user cancelled
     */
    private static boolean saveOpenEditors(File projectFile) {
        FileObject folder = FileUtil.toFileObject(FileUtil.normalizeFile(projectFile.getParentFile()));
        if (folder == null) {
            return true;
        }
        List<Savable> unsaved = new ArrayList<>();
        StringBuilder names = new StringBuilder();
        for (FileObject fo : folder.getChildren()) {
            if (!CayenneFiles.isProject(fo) && !CayenneFiles.isMap(fo)) {
                continue;
            }
            try {
                DataObject dob = DataObject.find(fo);
                Savable s = dob.isModified() ? dob.getLookup().lookup(Savable.class) : null;
                if (s != null) {
                    unsaved.add(s);
                    names.append("  ").append(fo.getNameExt()).append('\n');
                }
            } catch (DataObjectNotFoundException ex) {
                LOG.log(Level.FINE, null, ex);
            }
        }
        if (unsaved.isEmpty()) {
            return true;
        }
        Object answer = DialogDisplayer.getDefault().notify(new NotifyDescriptor.Confirmation(
                Bundle.MSG_SaveEditorsFirst(names.toString().stripTrailing()), NotifyDescriptor.YES_NO_CANCEL_OPTION));
        if (answer == NotifyDescriptor.YES_OPTION) {
            for (Savable s : unsaved) {
                try {
                    s.save();
                } catch (IOException ex) {
                    LOG.log(Level.WARNING, null, ex);
                    return false;
                }
            }
            return true;
        }
        return answer == NotifyDescriptor.NO_OPTION;
    }

    /** Unsaved modeler changes, as seen by the IDE's Save and Save All and its exit check. */
    private final class ModelerSavable extends AbstractSavable {

        private boolean active;

        void activate() {
            if (!active) {
                active = true;
                register();
                content.add(this);
            }
        }

        void deactivate() {
            if (active) {
                active = false;
                content.remove(this);
                unregister();
            }
        }

        @Override
        protected String findDisplayName() {
            return getDisplayName();
        }

        @Override
        protected void handleSave() throws IOException {
            try {
                if (EventQueue.isDispatchThread()) {
                    saveInModeler();
                } else {
                    EventQueue.invokeAndWait(this::saveInModeler);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException(ex);
            } catch (InvocationTargetException ex) {
                throw new IOException(ex.getCause());
            }
        }

        private void saveInModeler() {
            // the IDE unregisters this savable after handleSave; it is registered again if the
            // save was cancelled (e.g. in the modeler's Save As dialog) and changes remain
            active = false;
            content.remove(this);
            if (modeler != null) {
                modeler.save();
                if (modeler.isModified()) {
                    EventQueue.invokeLater(this::activate);
                }
            }
        }

        private CayenneModelerTopComponent tab() {
            return CayenneModelerTopComponent.this;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ModelerSavable other && other.tab() == tab();
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(tab());
        }
    }
}
