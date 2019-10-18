package ch.ethz.ivogels;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class DexUtils {
    public static void mergePathWithAPK(ZipFile apk, Path dir, ZipOutputStream destination) throws IOException {
        Enumeration sourceEntries = apk.entries();

        while(sourceEntries.hasMoreElements()) {
            ZipEntry sourceEntry = (ZipEntry)sourceEntries.nextElement();;
            String sourceEntryName = sourceEntry.getName();

            ZipEntry destinationEntry = new ZipEntry(sourceEntryName);
            destinationEntry.setMethod(sourceEntry.getMethod());
            destinationEntry.setSize(sourceEntry.getSize());
            destinationEntry.setCrc(sourceEntry.getCrc());
            destination.putNextEntry(destinationEntry);
            InputStream zipEntryInput = apk.getInputStream(sourceEntry);
            byte[] buffer = new byte[2048];

            for(int bytesRead = zipEntryInput.read(buffer); bytesRead > 0; bytesRead = zipEntryInput.read(buffer)) {
                destination.write(buffer, 0, bytesRead);
            }

            zipEntryInput.close();
        }

        Files.walk(dir)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    ZipEntry zipEntry = new ZipEntry(dir.relativize(path).toString());
                    try {
                        destination.putNextEntry(zipEntry);
                        Files.copy(path, destination);
                        destination.closeEntry();
                    } catch (IOException e) {
                        System.err.println(e);
                        System.exit(1);
                    }
                });
        destination.close();
    }
}
