package at.priv.joestr.cayenneinnetbeans.modeler;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

/**
 * Tools &gt; CayenneModeler (Separate Window): starts the standalone modeler without a project.
 */
@ActionID(category = "Tools", id = "at.priv.joestr.cayenneinnetbeans.modeler.LaunchModelerWindowAction")
@ActionRegistration(displayName = "#CTL_LaunchModelerWindowAction")
@ActionReference(path = "Menu/Tools", position = 1460)
@Messages("CTL_LaunchModelerWindowAction=CayenneModeler (Separate Window)")
public final class LaunchModelerWindowAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        CayenneModeler.launch(null);
    }
}
