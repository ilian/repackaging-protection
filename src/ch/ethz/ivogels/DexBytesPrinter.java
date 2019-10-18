package ch.ethz.ivogels;

import soot.CompilationDeathException;
import soot.toDex.DexPrinter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DexBytesPrinter extends DexPrinter{
    public byte[] printAsByteArray() {
        try {
            Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
            Path outputDir = Files.createTempDirectory(tmpDir, "dexbytesprinter-temp-");
            System.out.println("Writing temporary dex file to  " + outputDir);
            dexBuilder.writeTo(outputDir.toString());

            // Read file to byte array
            File outDirFile = new File(outputDir.toString());
            File[] outDexFiles = outDirFile.listFiles();
            assert outDexFiles != null && outDexFiles.length == 1;
            File dexFile = outDexFiles[0];
            byte[] res = new byte[(int) dexFile.length()];
            FileInputStream fis = new FileInputStream(dexFile);
            int read = fis.read(res);
            assert res.length == read;

            // Close and delete file, delete temporary directory
            fis.close();
            boolean fileDelSuccess = dexFile.delete();
            assert fileDelSuccess;
            Files.delete(outputDir);
            return res;
        } catch (IOException e) {
            throw new CompilationDeathException("I/O exception while printing dex: " + e.getMessage());
        }
    }
}
