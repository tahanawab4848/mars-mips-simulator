<h1 align="center">MARS-MIPS-SIMULATOR (MARS STUDIO)</h1>


<p align="center">
  <strong>A modernized, full-stack IDE for MIPS Assembly and Computer Architecture education.</strong>
</p>

<p align="center">
  <a href="https://github.com/tahanawab4848/mars-mips-simulator/actions/workflows/build.yml">
    <img src="https://github.com/tahanawab4848/mars-mips-simulator/actions/workflows/build.yml/badge.svg" alt="Build Status" />
  </a>
  <a href="https://opensource.org/licenses/MIT">
    <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT" />
  </a>
  <a href="https://www.oracle.com/java/">
    <img src="https://img.shields.io/badge/Java-8%20to%2022%2B-orange.svg" alt="Java Version" />
  </a>
  <img src="https://img.shields.io/badge/Language-MIPS%20Assembly-yellowgreen.svg" alt="MIPS Assembly" />
  <img src="https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey.svg" alt="Platform Compatibility" />
  <a href="http://makeapullrequest.com">
    <img src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square" alt="PRs Welcome" />
  </a>
</p>

<p align="center">
  <a href="https://github.com/tahanawab4848/mars-mips-simulator/stargazers">
    <img src="https://img.shields.io/github/stars/tahanawab4848/mars-mips-simulator.svg?style=social" alt="GitHub stars" />
  </a>
  <a href="https://github.com/tahanawab4848/mars-mips-simulator/network/members">
    <img src="https://img.shields.io/github/forks/tahanawab4848/mars-mips-simulator.svg?style=social" alt="GitHub forks" />
  </a>
</p>

## 🚀 Overview

MARS Studio represents a complete modernization and revitalization of the legacy 2005 MARS (MIPS Assembler and Runtime Simulator). We have transformed a basic, text-heavy MIPS emulator into a **state-of-the-art visual laboratory** for Systems Programming. 

Moving far beyond simple assembly execution, MARS Studio bridges the gap between high-level logic and low-level hardware behavior. By integrating a seamless C-to-MIPS cross-compilation workflow, a visually rich 5-stage superscalar datapath animation, and advanced cycle-by-cycle hazard simulation (via the InsightX suite), this project brings computer architecture education into the modern development era.

> ⚠️ **Are you facing Java or macOS errors with the original MARS?**
> The original MARS v4.5 suffers from graphics rendering glitches on macOS and crashes on modern Java runtimes. MARS Studio is rebuilt using Gradle and the FlatLaf layout engine, providing seamless cross-platform performance on Windows, macOS, and Linux, with full support for Java versions 8 through 22+.

## ✨ Key Features

### 1. Integrated C-to-MIPS Workflow
Write C code, compile it, and watch the assembly execute in real-time.
* **Built-in C Editor**: Syntax-highlighted "C Programming" environment directly inside MARS.
* **Cross-Compilation**: Uses external GCC/Clang to dynamically compile C into MIPS assembly.
* **Intelligent Source Mapping**: Synchronizes your C code lines with MIPS instruction addresses. As you step through execution, the IDE highlights the exact line of C being executed!
* **Auto-Cleaner**: Automatically strips unsupported GNU Assembler directives so the output is perfectly tailored for the MARS assembler.

### 2. InsightX Pipeline Visualization Suite
A dedicated sub-system to visually analyze superscalar execution and hardware behavior cycle-by-cycle.
* **Pipeline Visualizer**: A complete 5-stage pipeline model (IF, ID, EX, MEM, WB) that renders the execution flow in real-time.
* **Interactive Gantt Chart**: Maps out the execution timeline of instructions. See exactly which pipeline stage an instruction occupies during any clock cycle.
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

## 🛠 Compatibility & Prerequisites

### 1. Java 8 to 22+ (JRE/JDK)
Fully compatible with modern Java runtimes as well as legacy Java 8 environments.

### 2. Cross-Platform Support
Runs natively on Windows, Linux, and macOS (fixing the UI freeze and font scaling bugs present in the original MARS emulator).

### 3. MIPS Cross-Compiler
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

First, clone the repository and navigate to the project directory:

```bash
git clone https://github.com/tahanawab4848/mars-mips-simulator.git
cd mars-mips-simulator
```

Run the project directly using the provided Gradle wrappers:

```bash
# Windows
.\gradlew.bat run

# Linux/macOS
./gradlew run
```

To build a standalone executable JAR:
```bash
# Windows
.\gradlew.bat jar
java -jar build/libs/mars-4.5.jar

# Linux/macOS
./gradlew jar
java -jar build/libs/mars-4.5.jar
```

---

## 📖 Usage Guide

1. **Configure Compiler**: Go to `Settings → C Compiler → Auto-detect Compiler` (or configure manually).
2. **Write C Code**: Open the **"C Programming"** tab.
3. **Compile**: Click **⚙ Compile** to generate the MIPS assembly.
4. **Visualize**: Use **→ Step** to execute one instruction at a time and watch the Registers, Memory, Datapath, and Pipeline panels come alive.
## 💻⚙️🔧Developed By
╰┈➤ **Muhammad Taha Nawab**

╰┈➤ **Muhammad Zain Nadeem**

---
*Built upon the foundation of MARS 4.5 by Pete Sanderson and Ken Vollmar by Muhammad Taha Nawab*.
