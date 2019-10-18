package ch.ethz.ivogels;

import ch.ethz.ivogels.transformers.SDCBodyTransformer;
import soot.*;
import soot.options.Options;

import java.io.IOException;
import java.io.File;

public class Main {
    public static boolean TEST_TARGET = false;

    public static boolean nativeEnabled() {
        return !TEST_TARGET && true;
    }

    public static boolean nativeCodeWeavingEnabled() {
        return true;
    }

    public static boolean dexEncryptionEnabled() {
        return !TEST_TARGET && true;
    }

    public static boolean transformOnlyIfStatement() { return false; }

    public static void main(String[] args) {
        if (args.length == 0) {
            // Test module is marked as a dependency. Running Soot debug configuration will cause classes to build in ./sootOutput/Test
            args = new String[]{"-process-dir", "sootOutput/Test/"};
            Options.v().set_output_dir("./sootOutput/Test");
            Options.v().set_whole_program(true);
        }

        Options.v().parse(args);
        TEST_TARGET = Options.v().output_format() != Options.output_format_dex;
        if(TEST_TARGET) {
            System.err.println("Running as TEST_TARGET!");
        }

	final String ANDROID_JAR_ENV_KEY = "RP_ANDROID_JAR";
        String androidJarPath = System.getenv(ANDROID_JAR_ENV_KEY);
        if (androidJarPath == null || androidJarPath.isEmpty()) {
            System.err.printf("The environment %s is empty. It should be set to a valid path to android.jar\n", ANDROID_JAR_ENV_KEY);
            System.exit(-1);
	}
        File androidJarFile = new File(androidJarPath);
        if (!androidJarFile.isFile()) {
            System.err.printf("The environment variable %s does not represent a valid path to android.jar\n", ANDROID_JAR_ENV_KEY);
        }

        Options.v().set_soot_classpath(
                Scene.v().defaultClassPath() + ":./out/production/Transformer:" +
                androidJarPath
        );

        Scene.v().loadClassAndSupport("java.lang.Object");
        Scene.v().addBasicClass("java.lang.IllegalStateException", SootClass.SIGNATURES);

        injectClasses(new String[] {
                "embedded.SDCLoader",
                "embedded.Logger",
                "embedded.ResultWrapper"
        });

        NativeUtils.addNativeClass();

        PackManager.v().getPack("jtp").add(new Transform("jtp.sdc", new SDCBodyTransformer()));
        PackManager.v().runPacks();
        PackManager.v().writeOutput();

        if(nativeEnabled()) {
            /* Set required permissions to launch gdbserver */
            ManifestEditor.addPermission("android.permission.INTERNET"); // Not needed if we use stdin/stdout fds for communication with gdb
            ManifestEditor.setDebuggable();
            try {
                NativeUtils.installNativeLibs();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    private static void injectClasses(String[] classes) {
        for (String c: classes)
            Scene.v().addBasicClass(c, SootClass.BODIES);
        Scene.v().loadNecessaryClasses();
        for (String c: classes)
            Scene.v().getSootClass(c).setApplicationClass(); // Mark class to be part of output
    }
}
