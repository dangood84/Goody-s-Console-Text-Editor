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

    private List<String> contents = List.of();

    public boolean isEmpty() {
        return contents.isEmpty();
    }

    public void copy(List<String> lines) {
        // WORKING: copy the list so later document edits cannot change what
        // is sitting on the clipboard.
        this.contents = List.copyOf(new ArrayList<>(lines));
    }

    public List<String> paste() {
        return Collections.unmodifiableList(contents);
    }

    public int lineCount() {
        return contents.size();
    }
}
