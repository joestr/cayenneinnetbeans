package at.priv.joestr.cayenneinnetbeans.modeler;

import at.priv.joestr.cayenneinnetbeans.CayenneFiles;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * Tools &gt; CayenneModeler: shows the modeler tab.
 */
@ActionID(category = "Tools", id = "at.priv.joestr.cayenneinnetbeans.modeler.LaunchModelerAction")
@ActionRegistration(displayName = "#CTL_LaunchModelerAction", iconBase = CayenneFiles.ICON_MODELER)
@ActionReference(path = "Menu/Tools", position = 1450)
@Messages("CTL_LaunchModelerAction=CayenneModeler")
public final class LaunchModelerAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        CayenneModelerTopComponent.showProject(null);
    }
}
