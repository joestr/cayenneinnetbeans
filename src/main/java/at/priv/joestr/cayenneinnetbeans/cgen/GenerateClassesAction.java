package at.priv.joestr.cayenneinnetbeans.cgen;

import at.priv.joestr.cayenneinnetbeans.CayenneFiles;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;

/**
 * Generate Java Classes: runs cgen for a DataMap, or for all DataMaps of a Cayenne project.
 */
@ActionID(category = "Tools", id = "at.priv.joestr.cayenneinnetbeans.cgen.GenerateClassesAction")
@ActionRegistration(displayName = "#CTL_GenerateClassesAction")
@ActionReferences({
    @ActionReference(path = "Loaders/" + CayenneFiles.PROJECT_MIME_TYPE + "/Actions", position = 160, separatorAfter = 200),
    @ActionReference(path = "Loaders/" + CayenneFiles.MAP_MIME_TYPE + "/Actions", position = 160, separatorAfter = 200)
})
@Messages("CTL_GenerateClassesAction=Generate Java Classes (cgen)")
public final class GenerateClassesAction implements ActionListener {

    private final DataObject context;

    public GenerateClassesAction(DataObject context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (FileUtil.toFile(context.getPrimaryFile()) != null) {
            CayenneClassGenerator.generate(context.getPrimaryFile());
        }
    }
}
