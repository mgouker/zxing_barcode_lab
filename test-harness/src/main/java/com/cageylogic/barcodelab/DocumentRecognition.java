package com.cageylogic.barcodelab;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;

/** DocumentRecognition harness that drives the RecognitionEngine. */
public class DocumentRecognition {

  private final RecognitionEngine engine;

  public DocumentRecognition() {
    // Default: CODE_128 + DATA_MATRIX
    this.engine = new RecognitionEngine();
  }

  public DocumentRecognition(RecognitionEngine engine) {
    this.engine = engine;
  }

  /**
   * Scan all images in a directory.
   *
   * @param dir Directory containing images.
   * @return List of RecognitionResults.
   */
  public List<RecognitionResult> scanDirectory(File dir) {
    List<RecognitionResult> allResults = new ArrayList<>();

    // Extensions we want to allow
    Set<String> exts = Set.of(".png", ".jpg", ".jpeg", ".tif", ".tiff");

    try {
      Files.walk(dir.toPath()).filter(Files::isRegularFile).forEach(path -> {
        String name = path.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);

        // Skip files we don’t know how to handle
        if (exts.stream().noneMatch(lower::endsWith)) {
          BarcodeLog.debug("scan", "Skipping unsupported file: %s\n", name);
          return;
        }

        BarcodeLog.info("scan", "Processing file: %s\n", path.toAbsolutePath());

        try {
          BufferedImage image = ImageIO.read(path.toFile());
          if (image != null) {
            RecognitionEntry entry = new RecognitionEntry(name);

            // Multi-rotation decode (returns multiple results)
            List<RecognitionResult> results =
                engine.decode(image, path.toAbsolutePath().toString(), entry, 4);

            allResults.addAll(results);

            // Print all results for this file
            for (RecognitionResult result : results) {
              if (result.isSuccess()) {
                BarcodeLog.info("result", "%s -> %s\n\n", name, result.getText());
              } else {
                BarcodeLog.warn("result", "%s -> %s\n\n", name, result.getMessage());
              }
            }
          } else {
            allResults.add(
                new RecognitionResult(name, null, null, false,
                    "Unsupported format or unreadable image"));
            BarcodeLog.error("scan", "Unsupported format or unreadable image: %s\n", name);
          }
        } catch (Exception e) {
          allResults.add(new RecognitionResult(name, null, null, false, "Error: " + e.getMessage()));
          BarcodeLog.error("scan", "Failed to process %s: %s\n", name, e.getMessage());
        }
      });
    } catch (IOException e) {
      BarcodeLog.error("scan", "Failed to walk directory %s: %s\n", dir, e.getMessage());
    }

    return allResults;
  }

  public static void main(String[] args) {
    BarcodeLog.info("startup", "Welcome! This program scans a directory and its subdirectories for barcodes.\n");
    BarcodeLog.debug("startup", "Current working directory: %s\n", System.getProperty("user.dir"));

    if (args.length < 1) {
      System.err.println("Usage: java com.cageylogic.barcodelab.DocumentRecognition <imageDir>");
      System.exit(1);
    }

    DocumentRecognition dr = new DocumentRecognition();
    File dir = new File(args[0]);
    dr.scanDirectory(dir);
  }
}
