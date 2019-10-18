# Protecting Android Apps from Repackaging Using Native Code

This repository contains the code for the paper "Protecting Android Apps from Repackaging Using Native Code",
which appeared in the  conference proceedings of the [12th International Symposium on Foundations & Practice of Security](https://fps2019.sciencesconf.org/).

## About
The implementation aims to prevent malicious actors from repackaging Android apps by embedding integrity protections into a compiled Android application.
The transformation prevents normal execution if the app has been tampered with.
This protection prevents an attacker from modifying the app, and potentially bundling malware with it.
To make the protection difficult to circumvent, it relies on encrypted integrity checks in native code.

## System requirements
Currently, only UNIX-based systems are supposed.
The following packages should be installed
* JDK (tested with OpenJDK 8)
* Apache Ant
* Android SDK located at `$HOME/Android` with `~/Android/Sdk/build-tools/27.0.3` added to `$PATH` where `27.0.3` is the version number of the installed SDK.
* Android NDK located at `$HOME/Android/Sdk/ndk-bundle`
* CMake

Android SDK, NDK and CMake can be downloaded and installed using [Android Studio](https://developer.android.com/studio)

The paths to the components installed via Android Studio may be different depending on the installed version.
The following paths can be adjusted as desired:
* The `build.xml` script used by ant contains the path to the `android.jar` file of the target SDK version (tested with API level 26).
* The `native/native-build` script contains the path to the android NDK.

## Ant targets
By default, the APK to be transformed is located at`./app.apk`

This can be overridden by providing the following extra argument to ant: `-Dsrc-apk=custom-path>`


Transform and install an APK
```
ant clean transform-and-install-apk
```

Transform APK to a new protected APK
```
ant clean transform-apk-to-apk
```

Transform APK to Jimple files
```
ant clean transform-apk-to-jimple
```

Run java tests with locally installed JVM: Compares stdout between original and transformed bytecode.
Jimple files are written to `./test-out`
```
ant clean test
```

## Debugging native code
Native code can be debugged with gdb by setting the protected app as the debug app under
Settings →  System → Developer options → Select debug app. Make sure "Wait for debugger" is enabled.

The script attaches to the first pid that is hosting a JDWP transport. Make sure that no other app is hosting a JDWP transport.
Running `adb jdwp` will list all hosting processes.

An experimental script is located at `./android-debug` that attaches gdbserver to the running process.
It starts `jdb` in the background to skip the "Waiting for debugger" dialog.


Sample gdb session:
```
# Break at relevant JNI function
b Java_embedded_native_orgwhispersystemslibsignalstateStorageProtosSignedPreKeyRecordStructuret90a86f5e5d244037aacabebcbb3bb6cc
c # Continue executing until we reach a breakpoint
layout reg # Show instruction pointer and surrounding instructions
ni # Execute next instruction
<enter key to repeat last entered command>
...
```
