# 🧊 Context Condenser — JetBrains Plugin
## Installation & Setup Guide

> **Version:** 1.0.0  
> **Compatible with:** PyCharm 2026.1+ (build 261+), IntelliJ IDEA 2026.1+  
> **Platform:** Linux, macOS, Windows

---

## Table of Contents

- [What the Plugin Does](#what-the-plugin-does)
- [Prerequisites](#prerequisites)
- [Part 1 — Build the Plugin](#part-1--build-the-plugin)
- [Part 2 — Install in PyCharm](#part-2--install-in-pycharm)
- [Part 3 — First-Time Setup](#part-3--first-time-setup)
- [Part 4 — Using the Plugin](#part-4--using-the-plugin)
- [Settings Reference](#settings-reference)
- [Keyboard Shortcuts](#keyboard-shortcuts)
- [Troubleshooting](#troubleshooting)

---

## What the Plugin Does

Context Condenser sits between your Python code and your AI assistant. It uses Python's `ast` module to compress your source files into **structural skeletons** — function signatures, type hints, and one-line docstrings — with implementation bodies replaced by `...`.

```python
# What you normally paste (200+ tokens):
class UserService:
    def get_user(self, uid: str) -> dict:
        if uid in self.cache:
            return self.cache[uid]
        user = self.db.find(uid)
        self.cache[uid] = user
        return user

# What Context Condenser sends (18 tokens):
class UserService:
    def get_user(self, uid: str) -> dict:
        'Retrieve a user record by ID.'
        ...
```

The token badge in the panel footer shows your live savings: **Tokens: 1,694 raw → 468 condensed (72% saved)**.

---

## Prerequisites

### Required
| Requirement | Version | Check |
|---|---|---|
| Java (JDK) | 17 or 21 | `java --version` |
| Gradle | 8.5+ | `gradle --version` |
| Python | 3.8+ | `python3 --version` |
| PyCharm | 2026.1+ (build 261+) | Help → About |

### Installing Java 21 (Ubuntu/Debian)
```bash
sudo apt install -y openjdk-21-jdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
# Make permanent:
echo 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
source ~/.bashrc
```

### Installing Gradle 8.5 via SDKMAN (Recommended)
```bash
curl -s "https://get.sdkman.io" | bash
source ~/.sdkman/bin/sdkman-init.sh
sdk install gradle 8.5
gradle --version   # should show Gradle 8.5
```

---

## Part 1 — Build the Plugin

The plugin must be compiled against your local PyCharm installation. It cannot be pre-compiled because it requires the IntelliJ SDK JARs from your exact IDE version.

### Step 1 — Place the source files

Copy the `jetbrains-plugin/` folder into your `context-condenser` project:

```
context-condenser/
└── packages/
    └── jetbrains-plugin/       ← this folder
        ├── build.gradle.kts
        ├── settings.gradle.kts
        ├── gradle.properties
        ├── BUILD_LOCALLY.sh
        └── src/
```

### Step 2 — Set your PyCharm path in build.gradle.kts

Open `build.gradle.kts` and update the `local(...)` path to point to your PyCharm installation:

```kotlin
dependencies {
    intellijPlatform {
        local("/home/YOUR_USER/pycharm-2026.1")   // ← change this
        ...
    }
}
```

**Finding your PyCharm path:**
```bash
# Common locations:
ls ~/pycharm-*/          # extracted tarball
ls /opt/pycharm*/        # system-wide install
ls /snap/pycharm*/       # snap install
whereis pycharm
```

### Step 3 — Create the Gradle wrapper

```bash
cd ~/context-condenser/packages/jetbrains-plugin
gradle wrapper --gradle-version 8.5
```

### Step 4 — Build

```bash
./gradlew buildPlugin
```

Expected output:
```
> Task :compileKotlin
> Task :processResources
> Task :buildPlugin

BUILD SUCCESSFUL in 25s
12 actionable tasks: 12 executed
```

The compiled plugin ZIP is at:
```
build/distributions/context-condenser-jetbrains-1.0.0.zip
```

> **First build note:** Gradle downloads the IntelliJ Platform Gradle plugin and Kotlin compiler on first run (~200MB). Allow 3–5 minutes. Subsequent builds take under 30 seconds.

---

## Part 2 — Install in PyCharm

### Step 1 — Open the Plugins settings

```
PyCharm → Settings (Ctrl+Alt+S)
         → Plugins
```

Or from the Welcome screen:
```
Plugins (left sidebar)
```

### Step 2 — Install from disk

Click the **⚙️ gear icon** at the top of the Plugins panel, then select **Install Plugin from Disk...**

![Install from disk menu]

Navigate to:
```
~/context-condenser/packages/jetbrains-plugin/build/distributions/
```

Select: `context-condenser-jetbrains-1.0.0.zip`

Click **OK**.

### Step 3 — Restart PyCharm

Click **Restart IDE** when prompted. The plugin will not activate until PyCharm fully restarts.

### Step 4 — Copy the Python adapter script

After restart, copy `python_adapter.py` to the installed plugin directory:

```bash
# Find the installed plugin path (note: folder is context-condenser-jetbrains)
ls ~/.local/share/JetBrains/PyCharm2026.1/

# Create the scripts directory and copy the adapter
mkdir -p ~/.local/share/JetBrains/PyCharm2026.1/context-condenser-jetbrains/scripts/

cp ~/context-condenser/packages/jetbrains-plugin/src/main/resources/scripts/python_adapter.py \
   ~/.local/share/JetBrains/PyCharm2026.1/context-condenser-jetbrains/scripts/

# Verify
ls ~/.local/share/JetBrains/PyCharm2026.1/context-condenser-jetbrains/scripts/
# python_adapter.py
```

> **macOS path:** `~/Library/Application Support/JetBrains/PyCharm2026.1/context-condenser-jetbrains/scripts/`  
> **Windows path:** `%APPDATA%\JetBrains\PyCharm2026.1\context-condenser-jetbrains\scripts\`

---

## Part 3 — First-Time Setup

### Step 1 — Open the Context Condenser panel

```
View → Tool Windows → Context Condenser
```

The panel appears on the right sidebar. You should see:
```
Scope: [Current File ▼]  [↻ Refresh]  [⎘ Copy Prompt]
─────────────────────────────────────────────────────
# Open a .py file to see its condensed skeleton.
─────────────────────────────────────────────────────
                          Open a .py file to see...
```

### Step 2 — Configure your Python interpreter

Go to `File → Project Structure → SDKs` and confirm a Python 3.8+ interpreter is selected for your project. The plugin uses this to run the Python adapter script.

### Step 3 — Install tiktoken (optional but recommended)

Go to `Tools → Context Condenser: Setup Python Environment`

This installs `tiktoken` into your project interpreter for accurate token counting. Without it, token counts use a 4-chars-per-token estimate (±10% accuracy). A balloon notification confirms success.

You can also install it manually:
```bash
pip install tiktoken
```

### Step 4 — Configure settings

Go to `Settings → Tools → Context Condenser`

| Setting | Recommended value | Description |
|---|---|---|
| Condensation tier | **2 — Structural** | AST-based skeleton (default) |
| Auto-condense on file switch | ✅ On | Update panel when you open a new file |
| Show token badge | ✅ On | Display savings counter in footer |
| Smart Navigation | ✅ On | Double-click to jump to source |
| Preferred LLM | Claude / GPT4 / Gemini | Affects prompt header format only |
| Exclude patterns | `venv,__pycache__,.git` | Comma-separated paths to skip |
| Max project tokens | `8000` | Budget cap for Full Project mode |

> **Important:** If the panel shows `# [Tier 3 — AI summary not configured]`, the saved settings have tier=3. Fix it:  
> `Settings → Tools → Context Condenser → Condensation tier → drag to 2 → OK`  
> Or delete the saved settings file:  
> `rm ~/.config/JetBrains/PyCharm2026.1/options/CondenserSettings.xml`

---

## Part 4 — Using the Plugin

### Current File mode

1. Open any `.py` file in the editor
2. The Context Condenser panel updates automatically
3. The panel shows the condensed skeleton with token savings in the footer

**Example output:**
```python
class AuthService:
    'Handles user authentication and session management.'

    def login(self, email: str, password: str) -> bool: ...
    def logout(self, session_id: str) -> None: ...
    def _verify_token(self, token: str) -> bool: ...
```
```
Tokens: 847 raw → 43 condensed  (95% saved)
```

### Full Project mode

1. Select **Full Project** from the Scope dropdown
2. The background task scans all `.py` files in your project
3. The panel displays a Markdown architectural map with a summary table

**Example summary:**
```
| Metric           | Value  |
|---|---|
| Files processed  | 32     |
| Raw tokens       | 28,450 |
| Condensed tokens | 1,240  |
| Overall savings  | 96%    |
```

> **If Full Project shows 0 files:** Your project may not have configured source roots. This is normal for projects opened as a plain folder. The plugin falls back to scanning the project base directory automatically. Click **↻ Refresh** after switching to Full Project scope to trigger the scan.

### Sending context to your AI

**Method 1 — Copy Prompt button:**
1. Click **⎘ Copy Prompt** in the tool window
2. A formatted prompt is copied to your clipboard
3. Paste directly into Claude, ChatGPT, or Gemini

The prompt includes a header explaining the skeleton format to the AI:
```
--- CONTEXT START ---
I am using Context Condenser LVM. Code marked '...' is condensed.
Ask me for the full body of any function by name if needed.
Never guess logic inside condensed blocks.

[condensed skeleton here]
--- CONTEXT END ---
```

**Method 2 — Right-click menu:**
Right-click anywhere in a Python file → **Send Condensed to AI**  
(Keyboard shortcut: `Ctrl+Shift+Alt+C`)

**Method 3 — Project Skeleton:**
`Tools → Generate Project Skeleton` (or `Ctrl+Shift+Alt+P`)  
Shows the full architectural map in a dialog with a Copy button.

### Smart Navigation

Double-click any `def`, `async def`, or `class` line in the condensed view to jump directly to that symbol's source code in the editor.

---

## Settings Reference

| Setting | Type | Default | Description |
|---|---|---|---|
| `condensationTier` | 1–3 | `2` | 1=comments only, 2=AST structural, 3=AI-driven |
| `autoCondenseOnSwitch` | bool | `true` | Auto-update panel on file switch |
| `showTokenBadge` | bool | `true` | Show token count in footer |
| `includeDocstringSummary` | bool | `true` | Keep first docstring line in skeletons |
| `enableSmartNavigation` | bool | `true` | Double-click to jump to source |
| `preferredLLM` | string | `claude` | Affects copy prompt header: `claude`, `gpt4`, `gemini` |
| `excludePatterns` | string | `venv,__pycache__,.git,dist,build` | Comma-separated path fragments to skip |
| `maxProjectTokens` | int | `8000` | Token budget cap for Full Project mode |
| `scopeMode` | string | `file` | Default scope: `file` or `project` |

---

## Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| `Ctrl+Shift+Alt+C` | Send condensed current file to clipboard |
| `Ctrl+Shift+Alt+P` | Generate full Project Skeleton |
| `Double-click def/class` | Jump to source (Smart Navigation) |

---

## Troubleshooting

### Plugin panel is empty / shows placeholder
- Confirm a `.py` file is open and active in the editor
- Click **↻ Refresh** to manually trigger condensation
- Check `Settings → Tools → Context Condenser` — ensure **Auto-condense on file switch** is enabled

### `python_adapter.py: No such file or directory` in IDE log
The adapter script wasn't copied after install. Run:
```bash
mkdir -p ~/.local/share/JetBrains/PyCharm2026.1/context-condenser-jetbrains/scripts/
cp ~/context-condenser/packages/jetbrains-plugin/src/main/resources/scripts/python_adapter.py \
   ~/.local/share/JetBrains/PyCharm2026.1/context-condenser-jetbrains/scripts/
```

### `# [Tier 3 — AI summary not configured]` displayed
Saved settings have condensation tier = 3. Reset it:
```
Settings → Tools → Context Condenser → Condensation tier slider → set to 2 → OK
```

### Full Project shows 0 files processed
- Click **↻ Refresh** after selecting Full Project scope
- Check that `excludePatterns` isn't accidentally excluding your source files
- Verify `.py` files exist in the project: `find ~/your-project -name "*.py" | head -5`

### Token counts show only estimates (no exact counts)
Install tiktoken: `Tools → Context Condenser: Setup Python Environment`  
Or manually: `pip install tiktoken` in your project's interpreter

### Build error: `Kotlin metadata version mismatch`
Your Kotlin plugin version doesn't match PyCharm's bundled Kotlin. The `build.gradle.kts` uses Kotlin `2.1.0` with `-Xskip-metadata-version-check`. If you see this error:
```bash
rm -rf build .gradle
./gradlew buildPlugin
```

### Build error: `local("/path/to/pycharm")` — path not found
Update `build.gradle.kts` with your actual PyCharm installation path:
```bash
find ~ -name "pycharm.sh" 2>/dev/null   # find the binary
# then use the parent directory in build.gradle.kts
```

### `gradlew: command not found`
```bash
gradle wrapper --gradle-version 8.5
chmod +x gradlew
./gradlew buildPlugin
```

### `java: command not found` or wrong Java version
```bash
sudo apt install -y openjdk-21-jdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

---

## Updating the Plugin

When new source files are available:

1. Replace the changed `.kt` files in `src/main/kotlin/`
2. Rebuild:
   ```bash
   cd ~/context-condenser/packages/jetbrains-plugin
   rm -rf build
   ./gradlew buildPlugin
   ```
3. Reinstall via `Settings → Plugins → ⚙️ → Install Plugin from Disk`
4. Restart PyCharm

---

*Context Condenser JetBrains Plugin — part of the [Context Condenser](https://github.com/david-spies/context-condenser) monorepo.*  
*Context Condenser 2026*
