package consoleeditor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a path the user typed into a real filesystem path.
 *
 * WORKING: once you are inside the editor, no shell is expanding tokens.
 * Java's Paths.get treats ~, %USERPROFILE%, and $HOME as literal folder names.
 * We do the small set of expansions a user would expect, then hand off to Path.
 *
 * Intentionally not handled: Git Bash /c/Users/... (MSYS) paths. Those are a
 * Unix view of a Windows drive; the reliable forms are C:\... or ~ .
 */
final class UserPath {

    private static final Pattern WINDOWS_ENV = Pattern.compile("%([^%]+)%");
    private static final Pattern UNIX_BRACED_ENV = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}");
    private static final Pattern UNIX_ENV = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");

    private UserPath() {
    }

    static Path resolve(String raw) {
        String path = stripWrappingQuotes(raw.trim());
        path = expand(WINDOWS_ENV, path);
        path = expand(UNIX_BRACED_ENV, path);
        path = expand(UNIX_ENV, path);
        // WORKING: Windows users type backslashes. Paths.get only treats \ as a
        // separator on Windows, so "%USERPROFILE%\notes.txt" would become one
        // odd filename on Unix. Forward slashes are valid on both families
        // (including Windows UNC as //server/share).
        path = path.replace('\\', '/');
        path = expandLeadingTilde(path);
        // WORKING (Java 8): Path.of(...) arrived in Java 11. Paths.get is the
        // Java 7 NIO.2 equivalent and behaves the same for a single path string.
        return Paths.get(path).toAbsolutePath().normalize();
    }

    /**
     * WORKING: Windows users often quote paths because of spaces
     * (saveas "C:\My Documents\notes.txt"). Without stripping, the quotes
     * become part of the filename. We only strip a matching pair wrapping
     * the whole string, so a quote in the middle of a name is left alone.
     */
    private static String stripWrappingQuotes(String path) {
        if (path.length() >= 2) {
            char first = path.charAt(0);
            char last = path.charAt(path.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return path.substring(1, path.length() - 1);
            }
        }
        return path;
    }

    /**
     * WORKING: %USERPROFILE% is the Command Prompt counterpart of ~.
     * %HOMEDRIVE%%HOMEPATH% also shows up; running the same expander twice
     * in one pass covers stacked variables. Unknown names are left as typed
     * so a typo stays visible instead of vanishing.
     */
    private static String expand(Pattern pattern, String path) {
        Matcher matcher = pattern.matcher(path);
        // WORKING (Java 8): Matcher.appendReplacement/appendTail gained a
        // StringBuilder overload in Java 9. On 8 they only accept StringBuffer.
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(0);
            String name = matcher.group(1);
            matcher.appendReplacement(out, Matcher.quoteReplacement(lookup(name, token)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * WORKING: $HOME is common in PowerShell and Git Bash. HOME is often
     * unset in Command Prompt, so $HOME falls back to the JVM user.home
     * (same value ~ uses). Other $VARS use the process environment.
     */
    private static String lookup(String name, String originalToken) {
        if ("HOME".equals(name)) {
            String home = System.getenv("HOME");
            // WORKING (Java 8): String.isBlank() is Java 11. trim + isEmpty
            // treats whitespace-only HOME the same way isBlank would.
            return home != null && !home.trim().isEmpty() ? home : System.getProperty("user.home");
        }
        String value = System.getenv(name);
        return value != null ? value : originalToken;
    }

    private static String expandLeadingTilde(String path) {
        String home = System.getProperty("user.home");
        if (path.equals("~")) {
            return home;
        }
        if (path.startsWith("~/")) {
            return Paths.get(home).resolve(path.substring(2)).toString();
        }
        return path;
    }
}
