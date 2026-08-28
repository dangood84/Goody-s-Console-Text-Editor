package consoleeditor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory text buffer for the console editor.
 *
 * WORKING: A console editor is line-oriented (view/edit/copy by line number),
 * so storing the file as a List of lines is much simpler than one giant String.
 * Users see 1-based line numbers; this class uses 0-based indexes internally.
 */
public final class Document {

    // WORKING: null path means "untitled" — created with 'new' and not yet saved.
    private Path path;

    private final List<String> lines;

    // WORKING: dirty tracks unsaved edits so close/quit/open can warn the user.
    private boolean dirty;

    private Document(Path path, List<String> lines, boolean dirty) {
        this.path = path;
        this.lines = new ArrayList<>(lines);
        this.dirty = dirty;
    }

    /** Brand-new empty buffer. Not dirty until the user actually types. */
    public static Document blank() {
        return new Document(null, new ArrayList<>(), false);
    }

    public static Document open(Path path) throws IOException {
        List<String> loaded = Files.readAllLines(path, StandardCharsets.UTF_8);
        return new Document(path, loaded, false);
    }

    public Path getPath() {
        return path;
    }

    public boolean isDirty() {
        return dirty;
    }

    public int lineCount() {
        return lines.size();
    }

    public String displayName() {
        return path == null ? "untitled" : path.toString();
    }

    /**
     * WORKING: callers that only need to read (view, copy, undo snapshots) get
     * an unmodifiable view so they cannot mutate the buffer by accident.
     */
    public List<String> lines() {
        return Collections.unmodifiableList(lines);
    }

    public String lineAt(int index) {
        return lines.get(index);
    }

    public void setLine(int index, String text) {
        lines.set(index, text);
        dirty = true;
    }

    public void insertLines(int index, List<String> newLines) {
        lines.addAll(index, newLines);
        dirty = true;
    }

    public void deleteRange(int fromInclusive, int toExclusive) {
        lines.subList(fromInclusive, toExclusive).clear();
        dirty = true;
    }

    /**
     * WORKING: used by undo. We replace the whole buffer in one go, then mark
     * dirty because the on-disk file (if any) no longer matches what we show.
     */
    public void restoreLines(List<String> snapshot) {
        lines.clear();
        lines.addAll(snapshot);
        dirty = true;
    }

    public void save() throws IOException {
        if (path == null) {
            throw new IllegalStateException("No path yet — use save-as first.");
        }
        writeTo(path);
    }

    public void saveAs(Path newPath) throws IOException {
        writeTo(newPath);
        path = newPath;
    }

    private void writeTo(Path target) throws IOException {
        // WORKING: UTF-8 is the safe default for a simple editor.
        // Files.write creates/overwrites the file with the current lines.
        // We join with the platform newline via Files.write(List).
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.write(target, lines, StandardCharsets.UTF_8);
        dirty = false;
    }
}
