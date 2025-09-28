package com.cageylogic.barcodelab;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Organizes test images into folders based on barcode types (from CSV).
 * Includes BOTH successes and failures for ZX.
 */
public final class SortReportsForAllBarcodeTypes {

  private static final String CSV_FILE = "results/Barcode_Comparison_ByEngine_PageAware.csv";
  private static final String SOURCE_DIR = "test-data/images";
  private static final String TARGET_DIR = "test-data";

  // Only accept known ZXing formats; anything else is ignored.
  private static final Set<String> VALID_FORMATS = Set.of("AZTEC", "CODABAR", "CODE_39", "CODE_93", "CODE_128",
      "DATA_MATRIX", "EAN_8", "EAN_13", "ITF", "MAXICODE", "PDF_417", "QR_CODE", "UPC_A", "UPC_E");

  private SortReportsForAllBarcodeTypes() {
    // utility class
  }

  public static void main(String[] args) {
    Map<String, Set<String>> fileToTypes = new HashMap<>();

    // Parse CSV robustly (handles quotes/commas)
    try (Reader in = new FileReader(CSV_FILE);
        CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim().withQuote('"')
            .parse(in)) {

      for (CSVRecord rec : parser) {
        // columns: 0 = filename, 2 = type
        String file = rec.get(0).trim();
        String rawType = rec.get(2).trim();

        if (file.isEmpty() || rawType.isEmpty()) {
          continue;
        }

        String type = rawType.toUpperCase(Locale.ROOT);
        if (!VALID_FORMATS.contains(type)) {
          System.err.printf("Skipping unknown format '%s' for file %s%n", rawType, file);
          continue;
        }

        fileToTypes.computeIfAbsent(file, k -> new HashSet<>()).add(type);
      }
    } catch (IOException e) {
      System.err.println("Error reading CSV: " + e.getMessage());
      return;
    }

    // Build a lookup of all files under SOURCE_DIR (recursively)
    Map<String, Path> fileLookup = new HashMap<>();
    try {
      Files.walk(Path.of(SOURCE_DIR)).filter(Files::isRegularFile)
          .forEach(p -> fileLookup.put(p.getFileName().toString(), p));
    } catch (IOException e) {
      System.err.println("Error scanning source dir: " + e.getMessage());
      return;
    }

    // Ensure target base dir exists
    new File(TARGET_DIR).mkdirs();

    // Process each file
    for (Map.Entry<String, Set<String>> entry : fileToTypes.entrySet()) {
      String file = entry.getKey();
      Set<String> types = entry.getValue();

      String folderName;
      if (types.size() == 1) {
        folderName = slug(types.iterator().next());
      } else {
        folderName = String.join("-and-", types.stream().map(SortReportsForAllBarcodeTypes::slug).sorted().toList());
      }

      File destDir = new File(TARGET_DIR, folderName);
      destDir.mkdirs();

      Path sourcePath = fileLookup.get(file);
      if (sourcePath == null) {
        System.err.printf("⚠ File %s not found anywhere under %s%n", file, SOURCE_DIR);
        continue;
      }

      Path destPath = destDir.toPath().resolve(file);

      try {
        Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
        System.out.printf("Copied %s -> %s%n", file, folderName);
      } catch (IOException e) {
        System.err.printf("Could not copy %s: %s%n", file, e.getMessage());
      }
    }

    System.out.println("Sorting complete.");
  }

  private static String slug(String formatUpper) {
    // Convert ZXing enum-like to folder name: CODE_128 -> code-128
    return formatUpper.toLowerCase(Locale.ROOT).replace("_", "-");
  }
}
