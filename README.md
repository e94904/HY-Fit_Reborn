# HY-Fit Reborn

This project is a modern, open-source revival of the proprietary "HY-Fit" smart scale application. It interacts with smart scales powered by Chipsea Technologies Bluetooth LE chipsets, accurately reading raw weight data and utilizing the manufacturer's proprietary algorithm to calculate comprehensive body metrics. 

This project was a massive undertaking and I was able to get it complete with the help of AI assisted tools such as Google Antigravity. 

## Project Structure

This folder contains two main sections:

1. **The New Source Code:**
   The `app/src/main/java/com/example/hyfitlite` directory contains the entirety of the newly written, modern Android application (using Kotlin and Jetpack Compose). The core logic for Bluetooth scanning, parsing packets, and rendering the UI resides here.
2. **The Original App (`original_apk/`):**
   This folder contains the unmodified `HY-Fit.apk` file. We have purposefully excluded the massive, uncompressed decompiled source code from this repository to save space, but you can easily generate it yourself!

## How the App Was Decompiled

To understand how the original app communicated with the scale, we extracted the APK from an Android device and decompiled it. If you wish to view the original source code, you will need to install **Apktool** (to get the resources, `AndroidManifest.xml`, and native C++ `.so` libraries) and **JADX** (to decompile the `.dex` bytecode into readable Java source code).

**To Decompile the APK Yourself:**

1. Download and install [Apktool](https://ibotpeaches.github.io/Apktool/install/) and [JADX](https://github.com/skylot/jadx).
2. Open your terminal in the `original_apk/` directory.
3. Run `apktool d HY-Fit.apk` to extract the resources and `.so` libraries.
4. Run `jadx -d out_java HY-Fit.apk` to decompile the Java source code.

This will allow you to inspect the classes and methods the manufacturer was using exactly as we did!

## Reverse Engineering the Bluetooth Protocol

By inspecting the decompiled Java code and capturing live Bluetooth packets, we discovered that the scale broadcasts its measurements using Manufacturer Specific Data packets (identified by the `0xFF` flag).
We manually counted the byte offsets in these payloads to figure out exactly which bytes corresponded to the raw weight, the decimal divisor flags, the unit of measurement (KG vs LBS), and the raw electrical impedance.

## The Chipsea Technologies Chipset

During the decompilation process, we found a native C++ library named `libchipsea_bias_v235.so` bundled inside the APK. Chipsea Technologies is a prominent manufacturer of smart scale chips and Bluetooth modules. Because their algorithms for calculating metrics like Body Fat, Bone Mass, and Visceral Fat from raw electrical impedance are proprietary and closed-source (often referred to as the OKOK SDK), we could not write our own math to match theirs.

Instead, we copied their compiled `libchipsea_bias_v235.so` directly into our project's `app/src/main/jniLibs` folder. By perfectly recreating their Java Native Interface (JNI) class structure, we successfully bridged our open-source Kotlin app to their closed-source C++ algorithm. This ensures our app generates the exact same, accurate health metrics as the original proprietary app!

## Downloading the Rebuilt APK

The APK is located in the releases tab. It is also in the "rebuilt_apk" as well and can be installed just like any other APK. 

## Building the App (With Android Studio)

Simply download this repo as a ZIP file and then unzip it. Then open android studio and select the option to import a project and select the unzipped folder as the project directory. 

## Building the App (Without Android Studio)

If you don't have Android Studio installed, but you have the Java Development Kit (JDK) and the Android SDK configured on your machine, you can build the APK directly from the command line using the included Gradle wrapper.

1. Open your terminal or Command Prompt.
2. Navigate to this project directory.
3. Run the following command to compile a debug APK:

**On Windows:**

```cmd
./gradlew.bat assembleDebug
```

**On macOS / Linux:**

```bash
./gradlew assembleDebug
```

Once the build finishes successfully, your compiled APK will be located at:
`app\build\outputs\apk\debug\app-debug.apk`
