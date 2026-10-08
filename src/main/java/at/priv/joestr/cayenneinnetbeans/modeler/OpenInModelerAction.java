package at.priv.joestr.cayenneinnetbeans.modeler;

import at.priv.joestr.cayenneinnetbeans.CayenneFiles;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;

/**
 * Opens a Cayenne project in the CayenneModeler tab. On a DataMap, the project that declares the
 * map is opened, since the modeler only works on whole projects.
 */
@ActionID(category = "Tools", id = "at.priv.joestr.cayenneinnetbeans.modeler.OpenInModelerAction")
@ActionRegistration(displayName = "#CTL_OpenInModelerAction", iconBase = CayenneFiles.ICON_MODELER)
@ActionReferences({
    @ActionReference(path = "Loaders/" + CayenneFiles.PROJECT_MIME_TYPE + "/Actions", position = 150, separatorBefore = 125),
    @ActionReference(path = "Loaders/" + CayenneFiles.MAP_MIME_TYPE + "/Actions", position = 150, separatorBefore = 125)
})
@Messages({
    "CTL_OpenInModelerAction=Open in CayenneModeler",
    "# {0} - map file name",
    "MSG_NoProjectForMap=No Cayenne project found next to {0}; starting CayenneModeler without a project."
})
public final class OpenInModelerAction implements ActionListener {

    private final DataObject context;

    public OpenInModelerAction(DataObject context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        CayenneModelerTopComponent.showProject(projectFor(context));
    }

    /**
     * The project file to open for a project or DataMap, or {@code null} (with a status message)
     * if a DataMap has no project next to it.
     */
    static File projectFor(DataObject dataObject) {
        FileObject fo = dataObject.getPrimaryFile();
        FileObject project = CayenneFiles.isMap(fo) ? CayenneFiles.findProjectFor(fo) : fo;
        if (project == null) {
            StatusDisplayer.getDefault().setStatusText(Bundle.MSG_NoProjectForMap(fo.getNameExt()));
            return null;
        }
        return FileUtil.toFile(project);
    }
}
