# Game Booster 🚀

A high-performance Android utility designed for power users and gamers. **Game Booster** combines advanced system-level optimizations via **Shizuku** with a premium, "Cyberpunk" inspired dashboard to provide a professional-grade gaming environment.

## 📌 Project Overview
Game Booster is built on a **Hybrid Architecture**, utilizing a Native Android backend (Kotlin/Java) for hardware communication and a highly optimized WebView frontend (HTML5/CSS3/JS). By integrating the **Shizuku API**, the app executes elevated shell commands (ADB level) to tune system performance, manage background processes, and reduce latency—all without requiring a full device Root.

---

## ✨ Key Features

### 1. Shizuku-Powered Tuning 🛠️
*   **Elevated Execution:** Uses Shizuku to perform low-level system optimizations usually restricted to system apps.
*   **Non-Root Power:** Accesses hidden Android performance APIs via the Shizuku Binder.
*   **Process Management:** Aggressively manages background "RAM-hogs" to prioritize CPU cycles for your active game.

### 2. Live Thermal Monitoring 🌡️
*   **Real-Time Tracking:** Constant monitoring of device temperature to identify thermal throttling points.
*   **Lifecycle-Aware Engine:** Polling automatically pauses when the app is in the background, ensuring zero unnecessary battery drain.
*   **Hardware Direct:** Reads data directly from system battery and thermal intents for 100% accuracy.

### 3. Persistent Game Library 🎮
*   **Local Storage (Persistence):** Unlike standard apps, your selected games are saved to `SharedPreferences`/`DataStore`. They stay on your dashboard even after reboots or app updates.
*   **Deep Package Discovery:** Uses `QUERY_ALL_PACKAGES` to scan your device and let you quickly build your custom gaming list.

### 4. Futuristic "Cyberpunk" Dashboard 🤖
*   **High-Tech UI:** Features neon glows, grid-based layouts, and sci-fi typography for a premium look.
*   **Native Bridge:** Implements a `JavascriptInterface` to stream native hardware data into the HTML interface with zero lag.

---

## 🛠️ Technical Deep Dive

### **The Shizuku Interface**
The app checks for the Shizuku service status on launch. Once authorized, it creates a secure bridge to execute shell-level commands such as:
*   `cmd package compile` (Speed optimization)
*   `settings put global` (System performance tweaks)
*   Manual memory trimming and background process restriction.

### **The Persistence Logic**
To fix the "disappearing app" bug:
1.  When a user selects a game, the **Package Name** is stored in a persistent `Set`.
2.  Upon launch, the app re-reads these IDs and fetches fresh Icons/Labels via the `PackageManager`.
3.  This data is then serialized into JSON and pushed to the `dashboard.html` via `webView.evaluateJavascript`.

---

## 📋 Requirements & Permissions

*   **Min SDK:** API 24 (Android 7.0+)
*   **Recommended:** [Shizuku App](https://shizuku.rikka.app/) installed and running.

**Permissions:**
*   `QUERY_ALL_PACKAGES`: To browse and add installed games.
*   `dev.rikka.shizuku.permission.RECEIVE`: To interact with Shizuku.
*   `BATTERY_STATS`: For hardware thermal readings.

---

## 📂 Project Structure

*   `/assets/dashboard.html`: The UI engine (CSS, JS, and HTML structure).
*   `MainActivity`: Handles WebView initialization and the Native-to-JS Bridge.
*   `AppSelectorActivity`: Manages the package querying and persistence logic.
*   `ShizukuService`: Manages the binder connection and command execution.

---

## 📜 License
This project is open-source under the **MIT License**.

---
**Powered by Shizuku. Designed for the Future of Mobile Gaming.**
