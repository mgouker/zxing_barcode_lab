package com.cageylogic.barcodelab;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Utility methods for working with recognition images. */
public final class RecognitionEngineUtils {

  private RecognitionEngineUtils() {}

  static final class PreprocOptions {
    final int maxDimension;      // Downscale if either side is larger (e.g., 1800)
    final int medianRadius;      // 0 = off, 1 = 3x3 median
    final int padding;           // pixels of white quiet-zone to add
    final boolean saveStages;    // write debug PNGs per stage
    final String debugDir;       // where to write debug files

    PreprocOptions(int maxDimension, int medianRadius, int padding,
                   boolean saveStages, String debugDir) {
      this.maxDimension = maxDimension;
      this.medianRadius = medianRadius;
      this.padding = padding;
      this.saveStages = saveStages;
      this.debugDir = debugDir == null ? "debug" : debugDir;
    }

    static PreprocOptions defaults() {
      // Good starting point for large scanned pages
      return new PreprocOptions(/*maxDimension=*/1800, /*medianRadius=*/1,
          /*padding=*/20, /*saveStages=*/true, /*debugDir=*/"debug");
    }
  }
  
  /**
   * Create a copy of the image in ARGB format.
   *
   * @param image Input image.
   * @return A copy in ARGB format.
   */
  public static BufferedImage copyToARGB(BufferedImage image) {
    BufferedImage argbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = argbImage.createGraphics();
    try {
      g2d.drawImage(image, 0, 0, null);
    } finally {
      g2d.dispose();
    }
    return argbImage;
  }

  /**
   * Create a grayscale version of the given image.
   *
   * @param image Input image.
   * @return A grayscale copy.
   */
  public static BufferedImage toGrayscale(BufferedImage image) {
    BufferedImage grayImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D g2d = grayImage.createGraphics();
    try {
      g2d.drawImage(image, 0, 0, null);
    } finally {
      g2d.dispose();
    }
    return grayImage;
  }

  /**
   * Scale an image with smooth rendering hints.
   *
   * @param image  Input image.
   * @param width  Target width.
   * @param height Target height.
   * @return A scaled copy.
   */
  public static BufferedImage scale(BufferedImage image, int width, int height) {
    BufferedImage scaled = new BufferedImage(width, height,
        image.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : image.getType());
    Graphics2D g2d = scaled.createGraphics();
    try {
      g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2d.drawImage(image, 0, 0, width, height, null);
    } finally {
      g2d.dispose();
    }
    return scaled;
  }

  /**
   * Tint an image with a given color and alpha.
   *
   * @param image Input image.
   * @param color Tint color.
   * @param alpha Opacity (0.0–1.0).
   * @return A tinted copy.
   */
  public static BufferedImage tint(BufferedImage image, Color color, float alpha) {
    BufferedImage tinted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = tinted.createGraphics();
    try {
      g2d.drawImage(image, 0, 0, null);
      g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(alpha * 255)));
      g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
    } finally {
      g2d.dispose();
    }
    return tinted;
  }
}
