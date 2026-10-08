# Cayenne in NetBeans

An Apache NetBeans plugin that bundles [Apache Cayenne](https://cayenne.apache.org/) 5.0-M3.

## Features

- **File types.** `cayenne-*.xml` project files and `*.map.xml` DataMaps are recognized by
  their root element and namespace (schema versions 9 to 13). They get their own icons and open
  in the XML editor.
- **CayenneModeler in a tab.** *Open in CayenneModeler* on a project or DataMap opens that
  project in a CayenneModeler editor tab. For a DataMap, it opens the project file next to it.
  *Tools > CayenneModeler* opens the tab empty. Unsaved modeler changes work with the IDE's
  Save and Save All, and the IDE asks about them on exit. See [Modeler tab](#modeler-tab).
- **CayenneModeler in a separate window.** The *(Separate Window)* variants of both actions start
  the standalone modeler in its own JVM, using the JDK the IDE runs on.
- **Class generation.** *Generate Java Classes (cgen)* on a DataMap, or on a project to cover all
  of its maps. Any cgen configuration saved in the map by CayenneModeler is used. A map without
  one generates into the owning project's main Java source folder. Output goes to the
  *Cayenne cgen* tab.
- **IDE library.** *Apache Cayenne 5.0-M3* (`cayenne`, `cayenne-di`, `slf4j-api`) shows up in
  *Tools > Libraries* for Ant-based projects. Maven and Gradle projects should declare
  `org.apache.cayenne:cayenne` as a dependency instead.

## Requirements

NetBeans 31 or newer (the plugin is built against `RELEASE310`), running on JDK 21 or newer,
because Cayenne 5.0 is compiled for Java 21.

## Build and run

```
mvn package                      # target/cayenneinnetbeans-0.1.0-SNAPSHOT.nbm
mvn package nbm:cluster nbm:run-ide "-Dnetbeans.installation=C:\Program Files\Apache NetBeans"
```

If the launcher reports that Java is too old, pass the JDK explicitly with
`"-Dnetbeans.run.params=--jdkhome <path-to-jdk>"`.

Install the `.nbm` through *Tools > Plugins > Downloaded*.

## Modeler tab

CayenneModeler has no reusable UI panel, so the tab creates the modeler's main window without
showing it and moves its menu bar and content into the tab (`EmbeddedModeler`). This depends on
modeler internals (private fields of `Application` and `GlobalActions`) and is written for
5.0-M3. If it fails, the tab shows the error and offers the separate window instead.

Things the standalone modeler does that are unsafe inside the IDE are neutralized:

- *Exit* (`System.exit`) is removed from the menu, and the action closes the tab instead.
- The search field's global Ctrl+F hook is removed.
- Menu shortcuts only act while the focus is in the tab; menu mnemonics are dropped, so they
  don't take over the IDE's own (Alt+F, ...).
- Creating the modeler empties the system clipboard; the clipboard's text, image or file list
  is restored afterwards.
- *Preferences > Reset to Defaults* is disabled; it restarts the modeler as a standalone app.
- The look and feel stays the IDE's. The modeler's FlatLaf setup is skipped, and so is the change
  it makes to the class loader of the Swing event thread.

Known limitations:

- There is one modeler tab, holding one project at a time (as in the standalone modeler).
- Dark themes: the modeler hard-codes some colors (the project tree paints a white background),
  so parts of it are hard to read.
- Dialogs belong to the hidden modeler window. It is kept at the IDE window's position and size,
  so dialogs open in the right place.
- The modeler reads files from disk. Before opening a project, the tab offers to save Cayenne
  files that have unsaved changes in the XML editor. After the modeler saves, open editors
  reload the files.

## Layout

The Cayenne jars, the modeler included, are packaged under
`modules/ext/at.priv.joestr.cayenneinnetbeans/` and are on the module classpath. The exception is
FlatLaf, in `modeler/` below that folder: only the standalone modeler JVM installs it.

The modeler jar is the slf4j provider for this module, so all of its Cayenne and Velocity
logging goes through the modeler's log factory. `ModelerLogging` drops DEBUG and TRACE and sends
the rest to `java.util.logging` and to the open tab's log console.
