# Goody's Console Text Editor

A small line-oriented text editor that runs in your terminal. Create, open, save, and close files; copy, cut, and paste; undo the last change.

## Requirements

- A **JDK 17+** on your `PATH` (`java` and `javac`). Java 15 is the real language floor (text blocks); 17 is what this repo pins.
- On macOS/Linux with [jenv](https://www.jenv.be/), `.java-version` selects 17 in this folder. Windows does not need jenv — just install a JDK and put it on `PATH`.

## Where it runs

The app is plain Java with no native code, no GUI toolkit, and no shell-specific scripts. The same `javac` / `java` commands work in:

- **Unix and Linux** (bash, zsh, and similar)
- **macOS** Terminal
- **Git Bash** on Windows
- **PowerShell**
- **Command Prompt** (`cmd.exe`)

A couple of environment details, not code changes:

- End-of-input is **Ctrl+D** on Unix/macOS/Git Bash, and **Ctrl+Z** then Enter on PowerShell/Command Prompt. `quit` works everywhere.
- Line endings follow the host OS when you save (LF on Unix, CRLF on Windows). Opening files with either ending is fine.
- `jenv` is optional and Unix-only. The `.java-version` file is ignored on Windows.

## Build and run

```bash
javac -d out src/consoleeditor/*.java
java -cp out consoleeditor.ConsoleTextEditor
```

Those two lines are the same in bash, Git Bash, PowerShell, and Command Prompt. (`javac` accepts the `*.java` wildcard even in `cmd.exe`, which does not expand globs itself.)

Type `help` once it starts. The prompt shows the file name, a `*` if there are unsaved changes, and the line count:

```
[untitled* | 3 lines] >
```

## Commands

| Command | What it does |
|---|---|
| `new` | Create a blank untitled file |
| `open [path]` | Open an existing file |
| `save` | Save the current file |
| `saveas [path]` | Save under a new name |
| `close` | Close the current file |
| `view` | Show numbered lines |
| `append` | Add lines at the end (finish with a lone `.`) |
| `insert <n>` | Insert a line before line `n` |
| `edit <n>` | Replace line `n` |
| `delete <n> [m]` | Delete line `n`, or lines `n` through `m` |
| `copy <n> [m]` | Copy line(s) to the clipboard |
| `cut <n> [m]` | Copy line(s) then delete them |
| `paste <n>` | Insert clipboard contents before line `n` |
| `undo` | Reverse the last change |
| `help` | List commands |
| `quit` | Exit |

Line numbers are **1-based**. Shortcuts: `n` `o` `s` `sa` `c` `v` `a` `i` `e` `d` `cp` `x` `p` `u` `q`.

`open` and `saveas` expand a few tokens Java would otherwise treat as folder names:

- `~` / `~/notes.txt` / `~\notes.txt` — home directory
- `%USERPROFILE%\notes.txt` — Command Prompt style (any `%VAR%` from the environment)
- `$HOME/notes.txt` — PowerShell / Git Bash style (`$HOME` falls back to the same home as `~` if `HOME` is unset)
- Wrapping quotes are stripped: `saveas "C:\My Documents\notes.txt"`

Git Bash `/c/Users/...` paths are not rewritten; use `C:\...` or `~` instead.

Unsaved work is protected: `new`, `open`, `close`, and `quit` ask before discarding changes.

## Layout

| Class | Role |
|---|---|
| `Document` | Line-based buffer, dirty flag, load/save |
| `Clipboard` | In-app copy / cut / paste |
| `UndoManager` | Snapshot undo before each change (up to 50) |
| `UserPath` | Expands `~`, `%VAR%`, `$HOME`, and wrapping quotes |
| `ConsoleTextEditor` | Command loop and UI |

Comments in the source walk through the design choices.
