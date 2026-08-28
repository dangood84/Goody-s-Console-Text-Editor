package consoleeditor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Snapshot-based undo.
 *
 * WORKING: Two common undo designs are:
 *   1. Command pattern — store each edit as a reversible action.
 *   2. Snapshots — copy the whole document before each change.
 *
 * For a small console editor, snapshots are the simplest thing that works.
 * Memory is not a concern for typical homework-sized files, so before every
 * mutation we push a copy of the current lines. Undo pops the latest copy
 * and restores it. There is no redo (not requested).
 */
public final class UndoManager {

    // WORKING: cap the stack so a long editing session cannot grow forever.
    private static final int MAX_DEPTH = 50;

    private final Deque<List<String>> stack = new ArrayDeque<>();

    /** Call this BEFORE changing the document. */
    public void remember(Document document) {
        stack.push(new ArrayList<>(document.lines()));
        while (stack.size() > MAX_DEPTH) {
            stack.removeLast();
        }
    }

    public boolean canUndo() {
        return !stack.isEmpty();
    }

    public boolean undo(Document document) {
        if (stack.isEmpty()) {
            return false;
        }
        document.restoreLines(stack.pop());
        return true;
    }

    /** WORKING: a new/open/close starts a different document, so old undos would be wrong. */
    public void clear() {
        stack.clear();
    }
}
