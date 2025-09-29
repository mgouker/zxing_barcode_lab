package com.cageylogic.barcodelab;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;


/** Utility methods for barcode recognition using ZXing. */
public class RecognitionEngineUtils {

  /**
  * Load an image from file into a BufferedImage.
  *
  * @param file image file
  * @return BufferedImage or null if error
  */
  public static BufferedImage loadImage(File file) {
    try {
      return ImageIO.read(file);
    } catch (IOException e) {
    System.err.println("Error loading image: " + file.getAbsolutePath());
    return null;
  }
}

/**
* Convert a BufferedImage into a ZXing BinaryBitmap.
*
* @param image BufferedImage
* @return BinaryBitmap
*/
public static BinaryBitmap toBinaryBitmap(BufferedImage image) {
  LuminanceSource source = new BufferedImageLuminanceSource(image);
  return new BinaryBitmap(new HybridBinarizer(source));
}

/**
* Rotate an image by degrees (useful if barcodes are rotated).
*
* @param img BufferedImage
* @param angle angle in degrees
* @return rotated BufferedImage
*/
public static BufferedImage rotateImage(BufferedImage img, double angle) {
  double rads = Math.toRadians(angle);
  double sin = Math.abs(Math.sin(rads));
  double cos = Math.abs(Math.cos(rads));
  int w = img.getWidth();
  int h = img.getHeight();
  int newWidth = (int) Math.floor(w * cos + h * sin);
  int newHeight = (int) Math.floor(h * cos + w * sin);

  BufferedImage rotated = new BufferedImage(newWidth, newHeight, img.getType());
  Graphics2D g2d = rotated.createGraphics();
  AffineTransform at = new AffineTransform();
  at.translate((newWidth - w) / 2, (newHeight - h) / 2);
  at.rotate(rads, w / 2.0, h / 2.0);
  g2d.drawRenderedImage(img, at);
  g2d.dispose();
  return rotated;
}

/**
* Scale an image by a factor.
*
* @param img BufferedImage
* @param factor scale factor (e.g., 2.0 doubles size)
* @return scaled BufferedImage
*/
public static BufferedImage scaleImage(BufferedImage img, double factor) {
  int newW = (int) (img.getWidth() * factor);
  int newH = (int) (img.getHeight() * factor);
  Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
  BufferedImage scaled = new BufferedImage(newW, newH, img.getType());
  Graphics2D g2d = scaled.createGraphics();
  g2d.drawImage(tmp, 0, 0, null);
  g2d.dispose();
  return scaled;
}
}
