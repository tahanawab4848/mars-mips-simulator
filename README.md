<h1 align="center">MARS Studio (InsightX Edition)</h1>

<p align="center">
  <strong>A modernized, full-stack IDE for MIPS Assembly and Computer Architecture education.</strong>
</p>

## 🚀 Overview

MARS Studio is a massive structural expansion over the classic MARS (MIPS Assembler and Runtime Simulator). It transforms the standard 2005 emulator into a **state-of-the-art visual laboratory** for Systems Programming. 

This project bridges the gap between high-level logic and low-level execution by integrating a **C-to-MIPS Cross-Compiler**, advanced **Pipeline Analytics**, and a suite of **Educational Visualizers**.

## ✨ Key Features

### 1. Integrated C-to-MIPS Workflow
Write C code, compile it, and watch the assembly execute in real-time.
* **Built-in C Editor**: Syntax-highlighted "C Programming" environment directly inside MARS.
* **Cross-Compilation**: Uses external GCC/Clang to dynamically compile C into MIPS assembly.
* **Intelligent Source Mapping**: Synchronizes your C code lines with MIPS instruction addresses. As you step through execution, the IDE highlights the exact line of C being executed!
* **Auto-Cleaner**: Automatically strips unsupported GNU Assembler directives so the output is perfectly tailored for the MARS assembler.

### 2. InsightX Pipeline Visualization Suite
A dedicated sub-system to visually analyze superscalar execution and hardware behavior cycle-by-cycle.
* **Interactive Gantt Chart**: Maps out the execution timeline of instructions. See exactly which pipeline stage (IF, ID, EX, MEM, WB) an instruction occupies during any clock cycle.
* **Animated Datapath**: A cycle-synchronized, animated block diagram of the CPU routing data as instructions execute.
* **Hazard Simulation**: Automatically detects Read-After-Write (RAW) data hazards and visually renders pipeline stalls (`STL`) and bubbles.

### 3. Advanced Memory & Hardware Dashboards
Massive interactive panels that replace static text with dynamic visual feedback.
* **Live Register Tracking**: Visualizes all 35 registers, highlighting exactly which ones change during a step.
* **Memory Visualizer**: Live ASCII/Hex views of the Stack, Data, and Heap segments.
* **Execution Analytics**: Track execution metrics and recent instruction history in real-time.

### 4. Custom Educational Plugins
Beyond the core UI, new plugins have been added to the *Tools* menu:
* **Array Sorting Visualizer**: Hooks into memory access to visually animate sorting algorithms (like Bubble Sort) in real-time.
* **Stack Visualizer**: Dynamically visualizes the Call Stack, Frame Pointers (`$fp`), and Stack Pointers (`$sp`) during function calls and recursion.
* **C-AST Visualizer**: Visualizes the Abstract Syntax Tree (AST) of the C code before it is compiled into MIPS.

### 5. Modern IDE Quality-of-Life
* **"Step Over" Debugging**: Standard modern IDE debugging—step *over* subroutines instead of being forced to step *into* them.
* **Cyber-Neon Theming Engine**: Say goodbye to the 90s Swing look. Toggle between modern, high-contrast dark modes natively.
* **Auto-Assemble**: Workflow automation that automatically assembles files upon opening.

---

## 🛠 Prerequisites

### 1. Java 8+
Already required by the base MARS architecture.

### 2. MIPS Cross-Compiler
To use the C-to-MIPS features, you need a cross-compiler installed on your system.

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

---

## 💻 Build & Run

Run the project directly using the provided Gradle wrappers:

```bash
# Windows
.\gradlew.bat run

# Linux/macOS
./gradlew run
```

To build a standalone executable JAR:
```bash
.\gradlew.bat jar
java -jar build/libs/mars-4.5.jar
```

---

## 📖 Usage Guide

1. **Configure Compiler**: Go to `Settings → C Compiler → Auto-detect Compiler` (or configure manually).
2. **Write C Code**: Open the **"C Programming"** tab.
3. **Compile**: Click **⚙ Compile** to generate the MIPS assembly.
4. **Visualize**: Use **→ Step** to execute one instruction at a time and watch the Registers, Memory, Datapath, and Pipeline panels come alive.

---
*Built upon the foundation of MARS 4.5 by Pete Sanderson and Ken Vollmar.*
