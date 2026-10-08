package at.priv.joestr.cayenneinnetbeans.filetype;

import at.priv.joestr.cayenneinnetbeans.CayenneFiles;
import java.io.IOException;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.text.MultiViewEditorElement;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * A Cayenne project descriptor ({@code cayenne-*.xml}), recognized by its {@code <domain>} root
 * element. Opens in the XML editor; the Cayenne actions are added to its context menu.
 */
@Messages({
    "LBL_CayenneProject_LOADER=Cayenne Project",
    "LBL_CayenneProject_EDITOR=Source"
})
@MIMEResolver.NamespaceRegistration(
        displayName = "#LBL_CayenneProject_LOADER",
        mimeType = CayenneFiles.PROJECT_MIME_TYPE,
        elementName = "domain",
        elementNS = {
            CayenneFiles.NS_DOMAIN_9, CayenneFiles.NS_DOMAIN_10, CayenneFiles.NS_DOMAIN_11,
            CayenneFiles.NS_DOMAIN_12, CayenneFiles.NS_DOMAIN_13
        },
        checkedExtension = "xml",
        position = 293)
@DataObject.Registration(
        mimeType = CayenneFiles.PROJECT_MIME_TYPE,
        iconBase = CayenneFiles.ICON_PROJECT,
        displayName = "#LBL_CayenneProject_LOADER",
        position = 300)
@ActionReferences({
    @ActionReference(path = "Loaders/" + CayenneFiles.PROJECT_MIME_TYPE + "/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.OpenAction"), position = 100),
    @ActionReference(path = "Loaders/" + CayenneFiles.PROJECT_MIME_TYPE + "/Actions",
            id = @ActionID(category = "Edit", id = "org.openide.actions.CutAction"), position = 300, separatorBefore = 250),
    @ActionReference(path = "Loaders/" + CayenneFiles.PROJECT_MIME_TYPE + "/Actions",
            id = @ActionID(category = "Edit", id = "org.openide.actions.CopyAction"), position = 400, separatorAfter = 500),
    @ActionReference(path = "Loaders/" + CayenneFiles.PROJECT_MIME_TYPE + "/Actions",
            id = @ActionID(category = "Edit", id = "org.openide.actions.DeleteAction"), position = 600),
    @ActionReference(path = "Loaders/" + CayenneFiles.PROJECT_MIME_TYPE + "/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.RenameAction"), position = 700, separatorAfter = 800),
    @ActionReference(path = "Loaders/" + CayenneFiles.PROJECT_MIME_TYPE + "/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.SaveAsTemplateAction"), position = 900, separatorAfter = 1000),
    @ActionReference(path = "Loaders/" + CayenneFiles.PROJECT_MIME_TYPE + "/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.FileSystemAction"), position = 1100, separatorAfter = 1200),
    @ActionReference(path = "Loaders/" + CayenneFiles.PROJECT_MIME_TYPE + "/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.ToolsAction"), position = 1300),
    @ActionReference(path = "Loaders/" + CayenneFiles.PROJECT_MIME_TYPE + "/Actions",
            id = @ActionID(category = "System", id = "org.openide.actions.PropertiesAction"), position = 1400)
})
public class CayenneProjectDataObject extends MultiDataObject {

    public CayenneProjectDataObject(FileObject pf, MultiFileLoader loader) throws DataObjectExistsException, IOException {
        super(pf, loader);
        registerEditor(CayenneFiles.PROJECT_MIME_TYPE, true);
    }

    @Override
    protected int associateLookup() {
        return 1;
    }

    @MultiViewElement.Registration(
            displayName = "#LBL_CayenneProject_EDITOR",
            iconBase = CayenneFiles.ICON_PROJECT,
            mimeType = CayenneFiles.PROJECT_MIME_TYPE,
            persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED,
            preferredID = "CayenneProject",
            position = 1000)
    public static MultiViewEditorElement createEditor(Lookup lkp) {
        return new MultiViewEditorElement(lkp);
    }
}
