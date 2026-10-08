package at.priv.joestr.cayenneinnetbeans.modeler;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.AWTEventListener;
import java.awt.event.AWTEventListenerProxy;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.apache.cayenne.configuration.ConfigurationNameMapper;
import org.apache.cayenne.dbsync.DbSyncModule;
import org.apache.cayenne.dbsync.reverse.configuration.ToolsModule;
import org.apache.cayenne.di.DIBootstrap;
import org.apache.cayenne.di.Injector;
import org.apache.cayenne.modeler.Application;
import org.apache.cayenne.modeler.CliArgs;
import org.apache.cayenne.modeler.ModelerModule;
import org.apache.cayenne.modeler.platform.UIInitializer;
import org.apache.cayenne.modeler.pref.adapters.DBConnectorsPrefs;
import org.apache.cayenne.modeler.project.ProjectDirtyListener;
import org.apache.cayenne.modeler.project.ProjectSession;
import org.apache.cayenne.modeler.service.action.GlobalActions;
import org.apache.cayenne.modeler.ui.MainFrame;
import org.apache.cayenne.modeler.ui.action.CloseProjectAction;
import org.apache.cayenne.modeler.ui.action.ExitAction;
import org.apache.cayenne.modeler.ui.action.OpenProjectAction;
import org.apache.cayenne.modeler.ui.action.SaveAction;
import org.apache.cayenne.modeler.ui.logconsole.LogConsole;
import org.apache.cayenne.modeler.ui.preferences.PreferenceDialog;
import org.apache.cayenne.modeler.ui.preferences.more.MorePrefsPanel;
import org.apache.cayenne.modeler.undo.CayenneUndoManager;
import org.apache.cayenne.project.ConfigurationNodeParentGetter;
import org.apache.cayenne.project.ProjectModule;
import org.apache.cayenne.resource.Resource;
import org.openide.util.NbBundle.Messages;
import org.slf4j.LoggerFactory;

/**
 * CayenneModeler's UI as a plain Swing component, for hosting in an IDE tab.
 * <p>
 * The modeler (5.0-M3) has no reusable panel: everything is built into {@link MainFrame}, a
 * {@code JFrame} that the rest of the modeler reaches through {@code app.getFrame()}. So the frame
 * is created as usual but never shown, and its menu bar and content are moved into a panel.
 * {@code Application.launch()} is not used, because it installs FlatLaf process-wide and changes
 * the context class loader of the event dispatch thread. Instead its steps are repeated here,
 * setting the {@link Application} fields it would set (they are private, hence reflection).
 * <p>
 * Things the modeler does that are fine in its own JVM but not inside the IDE are neutralized:
 * <ul>
 * <li>{@link ExitAction} calls {@code System.exit}: replaced by one that asks the host to close,
 * and removed from the File menu;</li>
 * <li>the search field installs a global Ctrl+F listener: removed;</li>
 * <li>menu shortcuts and mnemonics act window-wide: limited to when the focus is in the modeler;</li>
 * <li>"Reset to Defaults" in the preferences restarts the modeler with {@code launch()}: disabled;</li>
 * <li>creating the actions empties the system clipboard: its content is restored.</li>
 * </ul>
 * All methods must be called on the event dispatch thread.
 */
@Messages("TIP_ResetUnavailable=Not available while CayenneModeler runs in a NetBeans tab. Use the separate-window modeler to reset preferences.")
final class EmbeddedModeler {

    /** Callbacks into the hosting tab. */
    interface Host {

        /** The project location or modified state changed. {@code project} is null if none is open. */
        void projectChanged(File project, boolean modified);

        /** The modeler asked to exit (its Exit action); the host should close the tab. */
        void closeRequested();
    }

    private static final String MODELER_PACKAGE = "org.apache.cayenne.modeler.";

    private final Host host;
    private final Application app;
    private final MainFrame frame;
    private final JPanel component;
    private final AWTEventListener preferencesGuard = this::guardPreferences;
    private final ProjectDirtyListener dirtyListener = (wasDirty, isDirty) -> fireProjectChanged();
    private boolean disposed;

    private EmbeddedModeler(Host host) throws ReflectiveOperationException {
        this.host = host;
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Set<AWTEventListener> listenersBefore = awtListeners(toolkit);

        Injector injector = DIBootstrap.createInjector(
                new ProjectModule(),
                new DbSyncModule(),
                new ToolsModule(LoggerFactory.getLogger(EmbeddedModeler.class)),
                new ModelerModule());
        app = new Application(injector, new UIInitializer() {
        }, CliArgs.parse(new String[0]));

        // what Application.launch() does, minus look and feel, EDT class loader and showing the frame
        set("actionManager", createActions(injector));
        LogConsole logConsole = new LogConsole(app);
        set("logConsole", logConsole);
        app.getPrefsManager().runMigrations();
        set("dbConnectors", new DBConnectorsPrefs(app.getPrefsLocator().appNode(DBConnectorsPrefs.NODE)).getConnectors());
        app.refreshClassLoader();
        set("undoManager", new CayenneUndoManager(app));
        frame = new MainFrame(app);
        set("frame", frame);
        replaceExitAction();

        // global listeners the modeler UI installed (the search field's Ctrl+F hook)
        for (AWTEventListener listener : awtListeners(toolkit)) {
            if (!listenersBefore.contains(listener) && listener.getClass().getName().startsWith(MODELER_PACKAGE)) {
                toolkit.removeAWTEventListener(listener);
            }
        }

        component = new JPanel(new BorderLayout());
        component.add(moveMenus(frame.getJMenuBar()), BorderLayout.NORTH);
        frame.setJMenuBar(null);
        Container content = frame.getContentPane();
        frame.setContentPane(new JPanel());
        component.add(content, BorderLayout.CENTER);

        frame.addPropertyChangeListener("title", e -> fireProjectChanged());
        frame.getProjectSession().addDirtyListener(dirtyListener);
        toolkit.addAWTEventListener(preferencesGuard, AWTEvent.WINDOW_EVENT_MASK);
        ModelerLogging.setConsole(logConsole);
        logConsole.showConsoleIfNeeded();
    }

    /**
     * Builds the modeler UI.
     *
     * @throws ReflectiveOperationException if the bundled modeler is not the version this was written for
     */
    static EmbeddedModeler create(Host host) throws ReflectiveOperationException {
        return new EmbeddedModeler(host);
    }

    JComponent getComponent() {
        return component;
    }

    /**
     * Dialogs are owned by the hidden frame and some panels size themselves from it, so it should
     * cover the IDE window even though it is never shown.
     */
    void setReferenceBounds(Rectangle bounds) {
        frame.setBounds(bounds);
    }

    /** The open project file, or {@code null}. */
    File getProject() {
        ProjectSession session = frame.getProjectSession();
        Resource resource = session.project() != null ? session.project().getConfigurationResource() : null;
        if (resource == null) {
            return null;
        }
        try {
            return new File(resource.getURL().toURI());
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return null;
        }
    }

    boolean isModified() {
        return frame.getProjectSession().isDirty();
    }

    /**
     * Opens a project, closing the current one first (with the modeler's unsaved changes prompt).
     * Does nothing if that project is already open.
     */
    void open(File project) {
        if (project.equals(getProject())) {
            return;
        }
        if (CloseProjectAction.closeProject(app, true)) {
            app.getActionManager().getAction(OpenProjectAction.class).openProject(project, null);
        }
    }

    void save() {
        app.getActionManager().getAction(SaveAction.class)
                .actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED, "Save"));
    }

    /**
     * Closes the project, asking about unsaved changes.
     *
     * @return {@code false} if the user cancelled
     */
    boolean closeProject() {
        return CloseProjectAction.closeProject(app, true);
    }

    /** Releases the modeler; the instance must not be used afterwards. */
    void dispose() {
        disposed = true;
        frame.getProjectSession().saveSelectionToPrefs();
        frame.getProjectSession().removeDirtyListener(dirtyListener);
        Toolkit.getDefaultToolkit().removeAWTEventListener(preferencesGuard);
        ModelerLogging.setConsole(null);
        frame.dispose();
    }

    /**
     * Tells the host after the current event: the modeler changes the frame title before it
     * updates the project (on close, the title is cleared while the project is still set).
     */
    private void fireProjectChanged() {
        SwingUtilities.invokeLater(() -> {
            if (!disposed) {
                host.projectChanged(getProject(), isModified());
            }
        });
    }

    private void set(String field, Object value) throws ReflectiveOperationException {
        Field f = Application.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(app, value);
    }

    /**
     * Creates the modeler's actions. One of them (OpenProjectAction) empties the system clipboard
     * when constructed, which in the IDE would throw away whatever the user copied, so the
     * clipboard content is restored afterwards. Windows briefly locks the clipboard while other
     * applications use it, hence the retries.
     */
    private GlobalActions createActions(Injector injector) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable saved = snapshot(clipboard);
        for (int attempt = 1; ; attempt++) {
            try {
                GlobalActions actions = new GlobalActions(app,
                        injector.getInstance(ConfigurationNameMapper.class),
                        injector.getInstance(ConfigurationNodeParentGetter.class));
                if (saved != null) {
                    clipboard.setContents(saved, null);
                }
                return actions;
            } catch (IllegalStateException clipboardBusy) {
                if (attempt == 5) {
                    throw clipboardBusy;
                }
                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw clipboardBusy;
                }
            }
        }
    }

    /**
     * A copy of the clipboard's text, image and file list content. The live clipboard
     * transferable reads lazily from the system, so it would be empty once replaced.
     */
    private static Transferable snapshot(Clipboard clipboard) {
        Map<DataFlavor, Object> data = new LinkedHashMap<>();
        try {
            Transferable contents = clipboard.getContents(null);
            if (contents == null) {
                return null;
            }
            for (DataFlavor flavor : List.of(DataFlavor.stringFlavor, DataFlavor.imageFlavor, DataFlavor.javaFileListFlavor)) {
                if (contents.isDataFlavorSupported(flavor)) {
                    data.put(flavor, contents.getTransferData(flavor));
                }
            }
        } catch (IllegalStateException | UnsupportedFlavorException | IOException ex) {
            // clipboard busy or content unreadable: nothing to restore
        }
        if (data.isEmpty()) {
            return null;
        }
        return new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return data.keySet().toArray(new DataFlavor[0]);
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return data.containsKey(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                Object value = data.get(flavor);
                if (value == null) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return value;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private void replaceExitAction() throws ReflectiveOperationException {
        Field f = GlobalActions.class.getDeclaredField("actionMap");
        f.setAccessible(true);
        Map<String, Action> actions = (Map<String, Action>) f.get(app.getActionManager());
        actions.put(ExitAction.class.getName(), new ExitAction(app) {
            @Override
            public boolean exit() {
                host.closeRequested();
                return false;
            }
        });
    }

    /**
     * Moves the menus into a menu bar whose shortcuts only fire while the focus is inside the
     * modeler: a menu bar handles accelerators for its whole window, which here is the IDE's main
     * window. Mnemonics are dropped, they would shadow the IDE's main menu (Alt+F, ...).
     */
    private JMenuBar moveMenus(JMenuBar original) {
        JMenuBar bar = new JMenuBar() {
            @Override
            protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
                Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (condition == WHEN_IN_FOCUSED_WINDOW
                        && (focus == null || !SwingUtilities.isDescendingFrom(focus, component))) {
                    return false;
                }
                return super.processKeyBinding(ks, e, condition, pressed);
            }
        };
        List<JMenu> menus = new ArrayList<>();
        for (int i = 0; i < original.getMenuCount(); i++) {
            menus.add(original.getMenu(i));
        }
        for (JMenu menu : menus) {
            menu.setMnemonic(0);
            removeExitItem(menu);
            bar.add(menu);
        }
        return bar;
    }

    private static void removeExitItem(JMenu menu) {
        Component[] items = menu.getMenuComponents();
        for (int i = 0; i < items.length; i++) {
            if (items[i] instanceof JMenuItem item && item.getAction() instanceof ExitAction) {
                menu.remove(item);
                if (i > 0 && items[i - 1] instanceof JSeparator) {
                    menu.remove(items[i - 1]);
                }
                return;
            }
        }
    }

    /** Disables "Reset to Defaults": it restarts the modeler as a standalone application. */
    private void guardPreferences(AWTEvent event) {
        if (event.getID() == WindowEvent.WINDOW_OPENED && event.getSource() instanceof PreferenceDialog dialog
                && isOwnedByFrame(dialog)) {
            for (MorePrefsPanel panel : find(dialog, MorePrefsPanel.class)) {
                for (JButton button : find(panel, JButton.class)) {
                    if ("Reset to Defaults".equals(button.getText())) {
                        button.setEnabled(false);
                        button.setToolTipText(Bundle.TIP_ResetUnavailable());
                    }
                }
            }
        }
    }

    private boolean isOwnedByFrame(Window window) {
        for (Window w = window; w != null; w = w.getOwner()) {
            if (w == frame) {
                return true;
            }
        }
        return false;
    }

    private static <T> List<T> find(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container container) {
                found.addAll(find(container, type));
            }
        }
        return found;
    }

    private static Set<AWTEventListener> awtListeners(Toolkit toolkit) {
        return Arrays.stream(toolkit.getAWTEventListeners())
                .map(l -> l instanceof AWTEventListenerProxy proxy ? proxy.getListener() : l)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
