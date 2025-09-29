package com.cageylogic.barcodelab;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;


/**
* Organizes test images into folders based on barcode types (from CSV).
* Includes BOTH successes and failures for ZX.
*/
public class SortReportsForAllBarcodeTypes {
  private static final String CSV_FILE = "results/Barcode_Comparison_ByEngine.csv";
  private static final String SOURCE_DIR = "test-data/images";
  private static final String TARGET_DIR = "test-data";

  public static void main(String[] args) {
    Map<String, Set<String>> fileToTypes = new HashMap<>();

    // Read CSV
    try (BufferedReader br = new BufferedReader(new FileReader(CSV_FILE))) {
      String header = br.readLine(); // skip header
      String line;

      while ((line = br.readLine()) != null) {
        String[] parts = line.split(",", -1);
        if (parts.length < 3) {
          continue;
        }

        String file = parts[0].trim();
        String type = parts[2].trim();

        if (file.isEmpty() || type.isEmpty()) {
          continue;
        }

        fileToTypes.computeIfAbsent(file, k -> new HashSet<>()).add(type);
      }
    } catch (IOException e) {
      System.err.println("Error reading CSV: " + e.getMessage());
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
        folderName = types.iterator().next().toLowerCase().replace("_", "-");
      } else {
        folderName = String.join("-and-",
        types.stream().map(t -> t.toLowerCase().replace("_", "-")).sorted().toList());
      }

      File destDir = new File(TARGET_DIR, folderName);
      destDir.mkdirs();

      Path sourcePath = Path.of(SOURCE_DIR, file);
      Path destPath = destDir.toPath().resolve(file);

      try {
        Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);
        System.out.printf("Copied %s → %s%n", file, folderName);
      } catch (IOException e) {
        System.err.printf("⚠️ Could not copy %s: %s%n", file, e.getMessage());
      }
    }

    System.out.println("✅ Sorting complete.");
  }
}
