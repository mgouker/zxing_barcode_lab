package com.cageylogic.barcodelab;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
  List<RecognitionResult> results = new ArrayList<>();
  File[] files =
  dir.listFiles(
  (d, name) ->
  name.toLowerCase().endsWith(".png")
  || name.toLowerCase().endsWith(".jpg")
  || name.toLowerCase().endsWith(".jpeg"));

  if (files == null) {
    return results;
  }

for (File f : files) {
  try {
    BufferedImage image = ImageIO.read(f);
    if (image != null) {
      RecognitionEntry entry = new RecognitionEntry(f.getName());
      RecognitionResult result = engine.decode(image, entry);
      results.add(result);
      System.out.printf(
      "%s -> %s%n",
      f.getName(),
      result.isSuccess() ? result.getText() : result.getMessage());
    }
} catch (Exception e) {
results.add(
new RecognitionResult(
f.getName(), null, null, false, "Error: " + e.getMessage()));
}
}
return results;
}

public static void main(String[] args) {
  if (args.length < 1) {
    System.err.println(
    "Usage: java com.cageylogic.barcodelab.DocumentRecognition <imageDir>");
    System.exit(1);
  }

DocumentRecognition dr = new DocumentRecognition();
File dir = new File(args[0]);
dr.scanDirectory(dir);
}
}
