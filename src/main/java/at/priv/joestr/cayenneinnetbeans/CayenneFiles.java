package at.priv.joestr.cayenneinnetbeans;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.filesystems.FileObject;

/**
 * MIME types and lookup helpers for Cayenne project ({@code cayenne-*.xml}) and
 * DataMap ({@code *.map.xml}) files.
 */
public final class CayenneFiles {

    public static final String PROJECT_MIME_TYPE = "text/x-cayenne-project+xml";
    public static final String MAP_MIME_TYPE = "text/x-cayenne-map+xml";

    // Schema namespaces of the project formats Cayenne 5.0 can read (and upgrade).
    public static final String NS_DOMAIN_9 = "http://cayenne.apache.org/schema/9/domain";
    public static final String NS_DOMAIN_10 = "http://cayenne.apache.org/schema/10/domain";
    public static final String NS_DOMAIN_11 = "http://cayenne.apache.org/schema/11/domain";
    public static final String NS_DOMAIN_12 = "http://cayenne.apache.org/schema/12/domain";
    public static final String NS_DOMAIN_13 = "http://cayenne.apache.org/schema/13/domain";
    public static final String NS_MAP_9 = "http://cayenne.apache.org/schema/9/modelMap";
    public static final String NS_MAP_10 = "http://cayenne.apache.org/schema/10/modelMap";
    public static final String NS_MAP_11 = "http://cayenne.apache.org/schema/11/modelMap";
    public static final String NS_MAP_12 = "http://cayenne.apache.org/schema/12/modelMap";
    public static final String NS_MAP_13 = "http://cayenne.apache.org/schema/13/modelMap";

    public static final String ICON_PROJECT = "at/priv/joestr/cayenneinnetbeans/resources/cayenne-project.png";
    public static final String ICON_MAP = "at/priv/joestr/cayenneinnetbeans/resources/cayenne-map.png";
    public static final String ICON_MODELER = "at/priv/joestr/cayenneinnetbeans/resources/cayenne.png";

    private static final String MAP_SUFFIX = ".map.xml";

    private CayenneFiles() {
    }

    public static boolean isProject(FileObject fo) {
        return fo != null && PROJECT_MIME_TYPE.equals(fo.getMIMEType());
    }

    public static boolean isMap(FileObject fo) {
        return fo != null && MAP_MIME_TYPE.equals(fo.getMIMEType());
    }

    /**
     * Finds the Cayenne project file that declares the given DataMap. Cayenne keeps the project
     * and its maps in one folder, so only siblings are checked. A project that references the map
     * by name wins; otherwise the first project file in the folder is returned.
     *
     * @return the project file, or {@code null} if the folder has none
     */
    public static FileObject findProjectFor(FileObject map) {
        FileObject folder = map.getParent();
        if (folder == null) {
            return null;
        }
        String reference = "name=\"" + mapName(map) + "\"";
        FileObject fallback = null;
        for (FileObject candidate : folder.getChildren()) {
            if (!isProject(candidate)) {
                continue;
            }
            try {
                if (candidate.asText(StandardCharsets.UTF_8.name()).contains(reference)) {
                    return candidate;
                }
            } catch (IOException ex) {
                Logger.getLogger(CayenneFiles.class.getName()).log(Level.FINE, null, ex);
            }
            if (fallback == null) {
                fallback = candidate;
            }
        }
        return fallback;
    }

    /** The DataMap name, i.e. the file name without the {@code .map.xml} suffix. */
    public static String mapName(FileObject map) {
        String name = map.getNameExt();
        return name.endsWith(MAP_SUFFIX) ? name.substring(0, name.length() - MAP_SUFFIX.length()) : map.getName();
    }
}
