package at.priv.joestr.cayenneinnetbeans.modeler;

import at.priv.joestr.cayenneinnetbeans.CayenneFiles;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;

/**
 * Opens a Cayenne project in the standalone CayenneModeler, in its own JVM and window.
 */
@ActionID(category = "Tools", id = "at.priv.joestr.cayenneinnetbeans.modeler.OpenInModelerWindowAction")
@ActionRegistration(displayName = "#CTL_OpenInModelerWindowAction")
@ActionReferences({
    @ActionReference(path = "Loaders/" + CayenneFiles.PROJECT_MIME_TYPE + "/Actions", position = 155),
    @ActionReference(path = "Loaders/" + CayenneFiles.MAP_MIME_TYPE + "/Actions", position = 155)
})
@Messages("CTL_OpenInModelerWindowAction=Open in CayenneModeler (Separate Window)")
public final class OpenInModelerWindowAction implements ActionListener {

    private final DataObject context;

    public OpenInModelerWindowAction(DataObject context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        CayenneModeler.launch(OpenInModelerAction.projectFor(context));
    }
}
