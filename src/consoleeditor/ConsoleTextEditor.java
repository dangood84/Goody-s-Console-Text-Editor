package consoleeditor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Goody's Console Text Editor — a fairly simple Java console text editor.
 *
 * Features: new / open / close / save, copy-cut-paste, and undo.
 *
 * WORKING (overall shape):
 * This is a command loop, not a full-screen TUI. The user types commands like
 * "open notes.txt" or "copy 2 4". After each command we print a short status
 * and wait for the next one. That keeps the code small and easy to follow —
 * no need for raw terminal control or a GUI toolkit.
 *
 * Run:
 *   javac -d out src/consoleeditor/*.java
 *   java -cp out consoleeditor.ConsoleTextEditor
 */
public final class ConsoleTextEditor {

    // WORKING: document is null when nothing is open. That lets "close" be a
    // real action, and it stops edit commands from running against thin air.
    private Document document;
    private final Clipboard clipboard = new Clipboard();
    private final UndoManager undo = new UndoManager();
    private final BufferedReader in;

    public ConsoleTextEditor(BufferedReader in) {
        this.in = in;
    }

    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        new ConsoleTextEditor(reader).run();
    }

    public void run() throws IOException {
        println("Goody's Console Text Editor  —  type 'help' for commands, 'quit' to exit.");
        println("");

        while (true) {
            // WORKING: the prompt itself is a mini status bar so you always know
            // which file is open and whether it has unsaved changes (*).
            System.out.print(prompt());
            System.out.flush();

            String raw = in.readLine();
            if (raw == null) {
                // WORKING: Ctrl+D / end-of-stream. Treat like quit.
                println("");
                if (!confirmAbandonIfDirty("Quit")) {
                    continue;
                }
                println("Bye.");
                return;
            }

            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }

            // WORKING: first token is the command; the rest is the argument string
            // (so paths can contain spaces:  open my notes.txt).
            String[] tokens = line.split("\\s+");
            String command = tokens[0].toLowerCase(Locale.ROOT);
            String rest = line.length() > tokens[0].length()
                    ? line.substring(tokens[0].length()).trim()
                    : "";

            try {
                if (handle(command, rest, tokens)) {
                    println("Bye.");
                    return;
                }
            } catch (IllegalArgumentException ex) {
                println("  " + ex.getMessage());
            } catch (IOException ex) {
                println("  File error: " + ex.getMessage());
            }
        }
    }

    /**
     * @return true if the editor should exit
     */
    private boolean handle(String command, String rest, String[] tokens) throws IOException {
        switch (command) {
            case "help":
            case "h":
            case "?":
                printHelp();
                return false;
            case "new":
            case "n":
                newFile();
                return false;
            case "open":
            case "o":
                openFile(rest);
                return false;
            case "save":
            case "s":
                saveFile();
                return false;
            case "saveas":
            case "sa":
                saveFileAs(rest);
                return false;
            case "close":
            case "c":
                closeFile();
                return false;
            case "view":
            case "v":
                requireOpen();
                view();
                return false;
            case "append":
            case "a":
                requireOpen();
                appendLines();
                return false;
            case "insert":
            case "i":
                requireOpen();
                insertLine(tokens);
                return false;
            case "edit":
            case "e":
                requireOpen();
                editLine(tokens);
                return false;
            case "delete":
            case "d":
                requireOpen();
                deleteLines(tokens);
                return false;
            case "copy":
            case "cp":
                requireOpen();
                copyLines(tokens);
                return false;
            case "cut":
            case "x":
                requireOpen();
                cutLines(tokens);
                return false;
            case "paste":
            case "p":
                requireOpen();
                pasteLines(tokens);
                return false;
            case "undo":
            case "u":
                requireOpen();
                undoLast();
                return false;
            case "quit":
            case "q":
            case "exit":
                return confirmAbandonIfDirty("Quit");
            default:
                println("  Unknown command '" + command + "'. Type 'help'.");
                return false;
        }
    }

    // -------------------------------------------------------------------------
    // File commands
    // -------------------------------------------------------------------------

    private void newFile() throws IOException {
        if (!confirmAbandonIfDirty("Start a new file")) {
            return;
        }
        document = Document.blank();
        undo.clear();
        println("  New empty file (untitled). Use 'append' to start typing, 'saveas' to name it.");
    }

    private void openFile(String rest) throws IOException {
        if (rest.isEmpty()) {
            rest = ask("  Path to open: ");
            if (rest == null || rest.isBlank()) {
                println("  Open cancelled.");
                return;
            }
        }
        if (!confirmAbandonIfDirty("Open another file")) {
            return;
        }
        Path path = resolveUserPath(rest);
        if (!Files.isRegularFile(path)) {
            println("  No such file: " + path);
            return;
        }
        document = Document.open(path);
        undo.clear();
        println("  Opened " + path + " (" + document.lineCount() + " lines).");
        view();
    }

    private void saveFile() throws IOException {
        requireOpen();
        if (document.getPath() == null) {
            // WORKING: a brand-new file has no path yet, so Save becomes Save As.
            saveFileAs("");
            return;
        }
        document.save();
        println("  Saved " + document.getPath());
    }

    private void saveFileAs(String rest) throws IOException {
        requireOpen();
        if (rest.isEmpty()) {
            rest = ask("  Save as path: ");
            if (rest == null || rest.isBlank()) {
                println("  Save cancelled.");
                return;
            }
        }
        Path path = resolveUserPath(rest);
        document.saveAs(path);
        println("  Saved " + path);
    }

    private void closeFile() throws IOException {
        requireOpen();
        if (!confirmAbandonIfDirty("Close")) {
            return;
        }
        println("  Closed " + document.displayName() + ".");
        document = null;
        undo.clear();
        clipboard.copy(List.of()); // WORKING: closing the file also clears the selection clipboard.
    }

    // -------------------------------------------------------------------------
    // Editing commands
    // -------------------------------------------------------------------------

    private void view() {
        if (document.lineCount() == 0) {
            println("  (empty document)");
            return;
        }
        // WORKING: pad line numbers so a 100-line file still lines up in a column.
        int width = String.valueOf(document.lineCount()).length();
        int n = 1;
        for (String text : document.lines()) {
            println(String.format("  %" + width + "d | %s", n, text));
            n++;
        }
    }

    /**
     * WORKING: multi-line entry without a GUI. Type as many lines as you like;
     * a lone '.' on a line finishes (same convention as the classic Unix 'ed'
     * editor and mail). An empty document starts at line 1.
     */
    private void appendLines() throws IOException {
        println("  Append mode — type lines, then a lone '.' to finish.");
        List<String> added = readUntilDot();
        if (added.isEmpty()) {
            println("  Nothing appended.");
            return;
        }
        undo.remember(document);
        document.insertLines(document.lineCount(), added);
        println("  Appended " + added.size() + " line(s). Now " + document.lineCount() + " line(s).");
    }

    private void insertLine(String[] tokens) throws IOException {
        int at = parseLineNumber(tokens, 1, "Usage: insert <lineNumber>");
        // WORKING: inserting *before* that line, so insert 1 puts text at the top.
        // Allow insert at lineCount+1 as a synonym for append.
        int index = at - 1;
        if (index < 0 || index > document.lineCount()) {
            throw new IllegalArgumentException(
                    "Line must be between 1 and " + (document.lineCount() + 1) + ".");
        }
        String text = ask("  Text to insert: ");
        if (text == null) {
            return;
        }
        undo.remember(document);
        document.insertLines(index, List.of(text));
        println("  Inserted at line " + at + ".");
    }

    private void editLine(String[] tokens) throws IOException {
        int n = parseExistingLine(tokens, 1, "Usage: edit <lineNumber>");
        println("  Current: " + document.lineAt(n - 1));
        String text = ask("  New text: ");
        if (text == null) {
            return;
        }
        undo.remember(document);
        document.setLine(n - 1, text);
        println("  Line " + n + " updated.");
    }

    private void deleteLines(String[] tokens) {
        int[] range = parseRange(tokens, "Usage: delete <start> [end]");
        undo.remember(document);
        int count = range[1] - range[0];
        document.deleteRange(range[0], range[1]);
        println("  Deleted " + count + " line(s).");
    }

    // -------------------------------------------------------------------------
    // Clipboard
    // -------------------------------------------------------------------------

    private void copyLines(String[] tokens) {
        int[] range = parseRange(tokens, "Usage: copy <start> [end]");
        List<String> slice = new ArrayList<>(document.lines().subList(range[0], range[1]));
        clipboard.copy(slice);
        println("  Copied " + slice.size() + " line(s) to the clipboard.");
    }

    private void cutLines(String[] tokens) {
        // WORKING: cut = copy + delete. We snapshot once so a single undo
        // brings the cut lines back (and the clipboard still holds them).
        int[] range = parseRange(tokens, "Usage: cut <start> [end]");
        List<String> slice = new ArrayList<>(document.lines().subList(range[0], range[1]));
        clipboard.copy(slice);
        undo.remember(document);
        document.deleteRange(range[0], range[1]);
        println("  Cut " + slice.size() + " line(s). Paste with 'paste <lineNumber>'.");
    }

    private void pasteLines(String[] tokens) {
        if (clipboard.isEmpty()) {
            throw new IllegalArgumentException("Clipboard is empty. Copy or cut some lines first.");
        }
        int at = parseLineNumber(tokens, 1, "Usage: paste <lineNumber>");
        int index = at - 1;
        if (index < 0 || index > document.lineCount()) {
            throw new IllegalArgumentException(
                    "Line must be between 1 and " + (document.lineCount() + 1) + ".");
        }
        undo.remember(document);
        document.insertLines(index, clipboard.paste());
        println("  Pasted " + clipboard.lineCount() + " line(s) at line " + at + ".");
    }

    private void undoLast() {
        if (!undo.canUndo()) {
            println("  Nothing to undo.");
            return;
        }
        undo.undo(document);
        println("  Undid last change. Now " + document.lineCount() + " line(s).");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void requireOpen() {
        if (document == null) {
            throw new IllegalArgumentException("No file is open. Use 'new' or 'open' first.");
        }
    }

    /**
     * WORKING: any command that would throw away unsaved work asks first.
     * Returning false means "user cancelled — keep the current document".
     */
    private boolean confirmAbandonIfDirty(String action) throws IOException {
        if (document == null || !document.isDirty()) {
            return true;
        }
        String answer = ask("  Unsaved changes in " + document.displayName()
                + ". " + action + " anyway? (y/N): ");
        return answer != null && answer.trim().toLowerCase(Locale.ROOT).startsWith("y");
    }

    /**
     * Parse a 1-based line number from tokens[index].
     */
    private int parseLineNumber(String[] tokens, int index, String usage) {
        if (tokens.length <= index) {
            throw new IllegalArgumentException(usage);
        }
        try {
            int n = Integer.parseInt(tokens[index]);
            if (n < 1) {
                throw new IllegalArgumentException("Line numbers start at 1.");
            }
            return n;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(usage);
        }
    }

    private int parseExistingLine(String[] tokens, int index, String usage) {
        int n = parseLineNumber(tokens, index, usage);
        if (document.lineCount() == 0) {
            throw new IllegalArgumentException("Document is empty.");
        }
        if (n > document.lineCount()) {
            throw new IllegalArgumentException("Line " + n + " is out of range (1–"
                    + document.lineCount() + ").");
        }
        return n;
    }

    /**
     * WORKING: copy/cut/delete all take a line range. One number means a single
     * line; two numbers mean an inclusive range (copy 2 4 = lines 2, 3 and 4).
     * Returned as a half-open 0-based [from, to) pair for List.subList.
     */
    private int[] parseRange(String[] tokens, String usage) {
        int start = parseExistingLine(tokens, 1, usage);
        int end = tokens.length > 2 ? parseExistingLine(tokens, 2, usage) : start;
        if (end < start) {
            throw new IllegalArgumentException("End line must be >= start line.");
        }
        return new int[] { start - 1, end };
    }

    private List<String> readUntilDot() throws IOException {
        List<String> added = new ArrayList<>();
        while (true) {
            System.out.print("  | ");
            System.out.flush();
            String line = in.readLine();
            if (line == null || ".".equals(line)) {
                break;
            }
            added.add(line);
        }
        return added;
    }

    /**
     * WORKING: Java's Path.of does not expand ~ the way a shell does.
     * "saveas ~/notes.txt" would otherwise create a folder literally named "~"
     * in the current directory. ~ and ~/... (also ~\... on Windows) map onto
     * user.home, which is the real home on Unix, macOS, and Windows.
     * We only expand when ~ is the whole path or followed by a separator, so
     * a file named "~backup.txt" is left alone.
     */
    static Path resolveUserPath(String raw) {
        String trimmed = raw.trim();
        String home = System.getProperty("user.home");
        if (trimmed.equals("~")) {
            return Path.of(home).toAbsolutePath().normalize();
        }
        if (trimmed.startsWith("~/") || trimmed.startsWith("~\\")) {
            return Path.of(home).resolve(trimmed.substring(2)).toAbsolutePath().normalize();
        }
        return Path.of(trimmed).toAbsolutePath().normalize();
    }

    private String ask(String prompt) throws IOException {
        System.out.print(prompt);
        System.out.flush();
        return in.readLine();
    }

    private String prompt() {
        if (document == null) {
            return "[no file] > ";
        }
        String mark = document.isDirty() ? "*" : "";
        return "[" + document.displayName() + mark + " | " + document.lineCount() + " lines] > ";
    }

    private void printHelp() {
        println("""
                Commands
                  new                 create a blank untitled file
                  open [path]         open an existing file
                  save                save the current file
                  saveas [path]       save under a new name
                  close               close the current file
                  view                show numbered lines
                  append              add lines at the end (finish with a lone .)
                  insert <n>          insert a line before line n
                  edit <n>            replace line n
                  delete <n> [m]      delete line n, or lines n through m
                  copy <n> [m]        copy line(s) to the clipboard
                  cut <n> [m]         copy line(s) then delete them
                  paste <n>           insert clipboard contents before line n
                  undo                reverse the last change
                  help                this list
                  quit                exit

                Line numbers are 1-based. A trailing * in the prompt means unsaved changes.
                Paths may start with ~ for your home folder (saveas ~/notes.txt).
                Shortcuts: n o s sa c v a i e d cp x p u q
                """);
    }

    private static void println(String text) {
        System.out.println(text);
    }
}
