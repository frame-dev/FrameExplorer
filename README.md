# Frame Explorer (Swing)

A desktop file explorer built with Java Swing.

## Features

- Tree + table file browsing
- Favorites in left sidebar (add/remove)
- Top favorites selector
- Navigation: Back, Forward, Up, Home, Refresh
- Search/filter by file name
- Context menu actions
- File operations: New File, New Folder, Rename, Delete
- Clipboard operations: Copy, Cut, Paste
- `Open` and `Open With...`
- File details panel + properties dialog
- Themes: Ocean, Forest, Sunset, Mono, Midnight
- Density options: Compact, Comfortable, Spacious
- Scale options: 75%, 100%, 125%, 150%, 200%, 300%, 400%
- Hidden files toggle
- Keyboard shortcuts for common actions

## Requirements

- Java 21
- Maven 3.8+

## Build

```bash
mvn clean compile
```

## Run

```bash
mvn clean package
java -jar target/demo-1.0-SNAPSHOT.jar
```

## Persistent Settings

The app stores settings in:

```text
~/.frame-explorer/settings.properties
```

Persisted values include:

- Theme, density, scale
- Hidden files toggle
- Last opened directory
- Window size/position/maximized state
- Split pane positions
- Favorites list

## Git Ignore

Project includes a `.gitignore` for:

- Maven/Java build outputs (`target`, `bin`, `*.class`)
- IDE folders (`.vscode`, `.idea`)
- OS junk files
- Local runtime settings (`.frame-explorer/`)
