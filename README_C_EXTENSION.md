# MARS — Integrated C to MIPS Visualization Interface

## Overview

This extension adds a full **C Programming** workflow to MARS (MIPS Assembler and Runtime Simulator). Users can:

1. **Write C code** directly inside MARS using a syntax-highlighted editor
2. **Compile to MIPS** using an external GCC or Clang cross-compiler
3. **Execute** the generated assembly inside MARS
4. **Visualize** registers, memory, datapath, pipeline, and instruction flow in real-time
5. **Synchronize** C source lines with MIPS instructions during step execution

---

## Prerequisites

### 1. Java 8+
Already required by MARS.

### 2. MIPS Cross-Compiler (one of the following)

**Linux / WSL:**
```bash
sudo apt install gcc-mips-linux-gnu
# or
sudo apt install clang llvm
```

**Windows (via MSYS2):**
```bash
pacman -S mingw-w64-x86_64-mips-toolchain
```

**macOS (via Homebrew):**
```bash
brew install gcc-mips-elf
```

Verify installation:
```bash
mips-linux-gnu-gcc --version
# or
clang -target mips-linux-gnu --version
```

---

## Build & Run

```bash
# In the mars-mips-simulator directory:
.\gradlew.bat run        # Windows
./gradlew run            # Linux/macOS
```

Or build a JAR:
```bash
.\gradlew.bat jar
java -jar build/libs/mars-4.5.jar
```

---

## Usage

### Step 1 — Configure Compiler (first time only)
1. Open **Settings → C Compiler → Auto-detect Compiler**
2. If not found: **Settings → C Compiler → Configure Compiler...**
3. Set the compiler path and click **Save**

### Step 2 — Write C Code
1. Click the **"C Programming"** tab in MARS
2. Write or paste C code in the editor (left pane)
3. Sample programs are in `src/main/resources/csamples/`

### Step 3 — Compile
- Click **⚙ Compile** to compile C → MIPS assembly
- The generated `.asm` file is automatically loaded into MARS
- Errors are shown in the console (bottom of editor)

### Step 4 — Execute & Visualize
- Click **→ Step** to execute one instruction at a time
- Watch the **Registers**, **Memory**, **Datapath**, and **Pipeline** panels update
- The C source line currently being executed is highlighted in the editor
- Use **▶ Run** for continuous execution

---

## Supported C Features

| Feature           | Supported |
|-------------------|-----------|
| int variables     | ✅         |
| Arithmetic ops    | ✅         |
| for / while loops | ✅         |
| if / else         | ✅         |
| Arrays (int[])    | ✅         |
| Functions         | ✅         |
| Recursion         | ✅         |
| pointers          | ⚠️ partial |
| structs           | ❌ not yet |
| malloc/free       | ❌ not yet |
| printf            | ❌ (use MIPS syscalls directly) |

---

## Architecture

```
mars/
├── ccompiler/
│   ├── CCompilerConfig.java    — settings (Java Preferences)
│   ├── CompilerOutput.java     — result data object
│   ├── CSourceCleaner.java     — GAS→MARS assembly post-processor
│   ├── CCompiler.java          — ProcessBuilder compiler engine
│   └── CompilerRunner.java     — SwingWorker async wrapper
│
├── mapping/
│   ├── InstructionMapping.java — C line ↔ MIPS address data
│   ├── SourceMapper.java       — parses .loc directives
│   └── ExecutionTracker.java   — singleton, fires events per step
│
├── visualization/
│   ├── VisualizationPanel.java — top-level "C Programming" tab
│   ├── CEditorPanel.java       — C editor with syntax highlight
│   ├── ExecutionAnimator.java  — smooth 30fps fade animations
│   ├── RegisterVisualizer.java — all 35 registers with live highlight
│   ├── MemoryVisualizer.java   — Stack/Data/Heap with ASCII
│   ├── DatapathPanel.java      — animated Graphics2D datapath
│   ├── PipelinePanel.java      — 5-stage pipeline diagram table
│   ├── InstructionFlowPanel.java — recent instruction history
│   └── StatisticsPanel.java    — execution stats dashboard
│
└── venus/ (modified)
    ├── MainPane.java           — +1 "C Programming" tab
    ├── VenusUI.java            — +C Compiler settings menu
    ├── RunAssembleAction.java  — +tracker.onAssemblyComplete()
    └── CCompilerSettingsDialog.java — compiler config dialog

simulator/
└── Simulator.java              — +tracker.onInstructionExecuted()
```

---

## Compiler Command Used

**GCC:**
```
mips-linux-gnu-gcc -S -g -O0 -mips32r2 -mabi=32 input.c -o output.s
```

**Clang:**
```
clang -S -g -O0 -target mips-linux-gnu input.c -o output.s
```

The generated `.s` file is then cleaned by `CSourceCleaner` to remove directives
that MARS's assembler doesn't support (`.frame`, `.mask`, `.set noreorder`, etc.)

---

## Pipeline Visualization Notes

The pipeline panel implements a **simplified 5-stage model**:
- **IF** → Instruction Fetch
- **ID** → Instruction Decode / Register Read  
- **EX** → ALU Execute
- **MEM** → Data Memory Access
- **WB** → Write Back

RAW (Read-After-Write) hazards are detected when a load instruction is immediately
followed by an instruction that reads the loaded register. A **STL** (stall) cell
is shown in the pipeline diagram.

---

## License

This extension is built on top of MARS 4.5 (MIT License).
Extension code: MIT License.
