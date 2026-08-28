package consoleeditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-app clipboard for copy / cut / paste.
 *
 * WORKING: A real GUI editor would talk to the OS clipboard (AWT/Swing).
 * In a console app that extra dependency is awkward (and fails in headless
 * environments), so we keep a small internal clipboard: a list of lines
 * the user last copied or cut. Paste inserts those lines back into the document.
 */
public final class Clipboard {

    // WORKING (Java 8): List.of() is Java 9. emptyList() is the same idea —
    // an immutable empty list so we never accidentally add to a null clipboard.
    private List<String> contents = Collections.emptyList();

    public boolean isEmpty() {
        return contents.isEmpty();
    }

    public void copy(List<String> lines) {
        // WORKING (Java 8): List.copyOf(...) is Java 10. Copy into a new
        // ArrayList, then wrap it unmodifiable so later document edits cannot
        // change what is sitting on the clipboard.
        this.contents = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public List<String> paste() {
        return Collections.unmodifiableList(contents);
    }

    public int lineCount() {
        return contents.size();
    }
}
