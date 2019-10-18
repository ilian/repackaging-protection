package ch.ethz.ivogels;

import pxb.android.axml.*;
import soot.SourceLocator;
import soot.options.Options;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ManifestEditor {

    public static Path getAPKInputPath() {
        return Paths.get(Options.v().process_dir().stream().findFirst().orElseThrow(IllegalStateException::new));
    }

    public static Path getAPKOutputPath() {
        /* Alternative implementation:
                String APKFileName = getAPKInputPath.toRealPath().getFileName().toString(); // Get real filename (follows symlinks)
                Path sourceAPKPath = Paths.get(Options.v().output_dir() + "/" + APKFileName); // Transformed APK
         */
        File srcPath = SourceLocator.v().dexClassIndex().values().stream().findFirst().orElseThrow(IllegalStateException::new);
        return Paths.get(SourceLocator.v().getOutputDir(), srcPath.getName());
    }

    public static List<String> getCompatibleArchitectures() {
        List<String> res = new ArrayList<>();
        try {
            FileSystem zipFS = FileSystems.newFileSystem(getAPKInputPath(), null);
            for (Path root : zipFS.getRootDirectories()) {
                Path lib = root.resolve("lib");
                if(Files.notExists(lib)) continue;
                Files.list(lib).forEach(p -> res.add(p.getFileName().toString().replaceAll("/$", "")));
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        assert res.stream().noneMatch(s -> s.contains("/"));

        // Compile for all architectures if apk contains no native libraries
        if(res.isEmpty()) {
            res.add("arm64-v8a");
            res.add("armeabi-v7a");
            res.add("x86");
            res.add("x86_64");
        }

        // TODO: Remove deprecated lib folders from target APK
        if(res.remove("armeabi")) {
            System.err.println("Ignoring deprecated armeabi ABI. See https://developer.android.com/ndk/guides/abis#sa " +
                    "for more information\n");
        }

        if(res.remove("mips")) {
            System.err.println("Ignoring deprecated mips ABI. See https://developer.android.com/ndk/guides/abis#sa " +
                    "for more information\n");
        }

        if(res.remove("mips64")) {
            System.err.println("Ignoring deprecated mips64 ABI. See https://developer.android.com/ndk/guides/abis#sa " +
                    "for more information\n");
        }

        if(res.isEmpty()) {
            System.err.println("No supported architectures found. Exiting...");
            System.exit(1);
        }

        return res;
    }

    public static void addPermission(String permName) {
        try (FileSystem apk = FileSystems.newFileSystem(getAPKOutputPath(), null)) {
            Path mfPath = apk.getPath("/AndroidManifest.xml");
            AxmlReader r = new AxmlReader(Files.readAllBytes(mfPath));
            AxmlWriter w = new AxmlWriter();
            r.accept(new AxmlVisitor(w) {
                @Override
                public NodeVisitor child(String ns, String name) { // ns: null; name: manifest
                    NodeVisitor nv = super.child(ns, name);
                    NodeVisitor permVisitor = nv.child(null, "uses-permission");
                    permVisitor.attr("http://schemas.android.com/apk/res/android", "name", 0x1010003, 3, permName);
                    return nv;
                }
            });
            Files.write(mfPath, w.toByteArray(), StandardOpenOption.TRUNCATE_EXISTING); // Overwrite manifest
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void setDebuggable() {
        try (FileSystem apk = FileSystems.newFileSystem(getAPKOutputPath(), null)) {
            Path mfPath = apk.getPath("/AndroidManifest.xml");
            AxmlReader r = new AxmlReader(Files.readAllBytes(mfPath));
            AxmlWriter w = new AxmlWriter();
            r.accept(new AxmlVisitor(w) {
                @Override
                public NodeVisitor child(String ns, String name) { // ns: null; name: manifest
                    return new NodeVisitor(super.child(ns, name)) {
                        @Override
                        public void attr(String ns, String name, int resourceId, int type, Object obj) {
                            super.attr(ns, name, resourceId, type, obj);
                        }

                        @Override
                        public NodeVisitor child(String ns, String name) {
                            NodeVisitor nv = super.child(ns, name);
                            if(name.equals("application")) {
                                nv.attr("http://schemas.android.com/apk/res/android", "debuggable", 0x0101000f, 0x12, 0xffffffff);
                            }
                            return nv;
                        }
                    };
                }
            });
            Files.write(mfPath, w.toByteArray(), StandardOpenOption.TRUNCATE_EXISTING); // Overwrite manifest
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static String getPackageName() {
        final String[] packageName = {null};
        try (FileSystem apk = FileSystems.newFileSystem(getAPKInputPath(), null)) {
            Path mfPath = apk.getPath("/AndroidManifest.xml");
            AxmlReader r = new AxmlReader(Files.readAllBytes(mfPath));
            r.accept(new AxmlVisitor() {
                @Override
                public NodeVisitor child(String ns, String name) { // ns: null; name: manifest
                    return new NodeVisitor(super.child(ns, name)) {
                        @Override
                        public void attr(String ns, String name, int resourceId, int type, Object obj) {
                            if(name.equals("package"))
                                packageName[0] = (String) obj;
                        }
                    };
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        if(packageName[0] == null) throw new IllegalArgumentException("No package attribute supplied in 'manifest' XML element");
        return packageName[0];
    }
}
