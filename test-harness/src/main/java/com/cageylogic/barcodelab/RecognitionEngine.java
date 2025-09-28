package com.cageylogic.barcodelab;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.cageylogic.barcodelab.RecognitionEngineUtils.PreprocOptions;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.SauvolaBinarizer;

/**
 * RecognitionEngine handles decoding of barcodes from images. It applies
 * preprocessing (scaling, grayscale, Otsu threshold, padding), tries multiple
 * binarizers, and falls back to scaled versions if needed.
 */
public class RecognitionEngine {

  private final MultiFormatReader reader;
  private final Map<DecodeHintType, Object> hints;
  private final RecognitionEngineUtils.PreprocOptions pre;
  private final int rotationSections; // e.g., 4 (0/90/180/270)

  /**
   * Default: focus on CODE_128 + DATA_MATRIX, try harder, with logged
   * preprocessing.
   */
  public RecognitionEngine() {
    this(List.of(BarcodeFormat.CODE_128, BarcodeFormat.DATA_MATRIX), RecognitionEngineUtils.PreprocOptions.defaults(),
        4);
  }

  public RecognitionEngine(List<BarcodeFormat> formats, RecognitionEngineUtils.PreprocOptions pre,
      int rotationSections) {
    this.reader = new MultiFormatReader();
    this.hints = new EnumMap<>(DecodeHintType.class);
    this.hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
    this.hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    this.pre = pre == null ? RecognitionEngineUtils.PreprocOptions.defaults() : pre;
    this.rotationSections = Math.max(1, rotationSections);
  }

  /**
   * Preprocess the image: scale up, grayscale, Otsu threshold, and add padding.
   *
   * @param src Source image.
   * @return Preprocessed binary image.
   */
  static BufferedImage preprocess(BufferedImage src, String name, PreprocOptions opt) throws IOException {
    long t0 = System.nanoTime();
    BarcodeLog.debug("preprocess", "Start %s (size=%dx%d, type=%d)", name, src.getWidth(), src.getHeight(),
        src.getType());
    ensureDir(opt.debugDir);

    // 1) Downscale (if needed)
    BufferedImage img = downscaleIfNeeded(src, opt.maxDimension);
    BarcodeLog.debug("preprocess", "Downscaled -> %dx%d", img.getWidth(), img.getHeight());
    save(opt, img, name, "1-downscale");

    // 2) Grayscale
    img = toGray(img);
    BarcodeLog.debug("preprocess", "Grayscale done");
    save(opt, img, name, "2-grayscale");

    // 3) Median denoise (3x3) if requested
    if (opt.medianRadius > 0) {
      img = median3x3(img);
      BarcodeLog.debug("preprocess", "Median filter applied");
      save(opt, img, name, "3-median");
    }

    // 4) Otsu threshold to clean black/white modules
    BufferedImage bin = otsuThreshold(img);
    BarcodeLog.debug("preprocess", "Otsu threshold applied, result size=%dx%d", bin.getWidth(), bin.getHeight());
    save(opt, bin, name, "4-otsu");

    // 5) Add quiet-zone padding
    BufferedImage padded = addPadding(bin, opt.padding);
    BarcodeLog.debug("preprocess", "Padding added (%dpx) -> %dx%d", opt.padding, padded.getWidth(), padded.getHeight());
    save(opt, padded, name, "5-padded");

    long tEnd = System.nanoTime();
    BarcodeLog.info("preprocess", "Done %s in %.1f ms", name, (tEnd - t0) / 1_000_000.0);

    return padded;
  }

  /**
   * Apply Otsu’s thresholding to a grayscale image.
   *
   * @param gray Grayscale image.
   * @return Binarized image.
   */

  private static void ensureDir(String dir) {
    File f = new File(dir);
    if (!f.exists()) {
      // noinspection ResultOfMethodCallIgnored
      f.mkdirs();
    }
  }

  /**
   * 
   * @param opt   - options for saving (incl output directory)
   * @param img   - image being ssaved
   * @param base  - base name
   * @param stage - step of decodinng (for debug output)
   * @throws IOException - cannot write file.
   */
  private static void save(PreprocOptions opt, BufferedImage img, String base, String stage) throws IOException {
    if (!opt.saveStages) {
      return;
    }
    String safe = new File(base).getName().replaceAll("[^a-zA-Z0-9._-]", "_");
    File out = new File(opt.debugDir, stage + "-" + safe + ".png");
    ImageIO.write(img, "png", out);
    BarcodeLog.info("  wrote %s%n", out.getPath());
  }

  /**
   * 
   * @param stage  - This is a text description of the stage of decoding.
   * @param name   - document name
   * @param tPrev  - previous time
   * @param before - imagee before
   * @param after  - image after
   */
  @SuppressWarnings("unused")
  private static void logStage(String stage, String name, long tPrev, BufferedImage before, BufferedImage after) {
    double ms = (System.nanoTime() - tPrev) / 1_000_000.0;
    if (before == null) {
      BarcodeLog.info("  %-12s -> %dx%d (%.1f ms)%n", stage, after.getWidth(), after.getHeight(), ms);
    } else {
      BarcodeLog.info("  %-12s %dx%d -> %dx%d (%.1f ms)%n", stage, before.getWidth(), before.getHeight(),
          after.getWidth(), after.getHeight(), ms);
    }
  }

  /**
   * 
   * @param src    - allows for downscaling
   * @param maxDim - largest size allowed
   * @return - new image
   */
  private static BufferedImage downscaleIfNeeded(BufferedImage src, int maxDim) {
    int w = src.getWidth();
    int h = src.getHeight();
    int max = Math.max(w, h);
    if (max <= maxDim || maxDim <= 0) {
      return src;
    }
    double scale = (double) maxDim / (double) max;
    int nw = Math.max(1, (int) Math.round(w * scale));
    int nh = Math.max(1, (int) Math.round(h * scale));

    BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = dst.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
      g.drawImage(src, 0, 0, nw, nh, null);
    } finally {
      g.dispose();
    }
    return dst;
  }

  /**
   * 
   * @param src - image to change to grey sccale
   * @return - new image
   */
  private static BufferedImage toGray(BufferedImage src) {
    int w = src.getWidth();
    int h = src.getHeight();
    BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D g = gray.createGraphics();
    try {
      g.drawImage(src, 0, 0, null);
    } finally {
      g.dispose();
    }
    return gray;
  }

  /**
   * 
   * @param src - image to round over area
   * @return - new image
   */
  private static BufferedImage median3x3(BufferedImage src) {
    int w = src.getWidth();
    int h = src.getHeight();
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);

    int[] window = new int[9];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int k = 0;
        for (int dy = -1; dy <= 1; dy++) {
          int yy = clamp(y + dy, 0, h - 1);
          for (int dx = -1; dx <= 1; dx++) {
            int xx = clamp(x + dx, 0, w - 1);
            int p = src.getRGB(xx, yy) & 0xFF;
            window[k++] = p;
          }
        }
        // median of 9 (partial selection sort is fine)
        for (int i = 0; i < 5; i++) {
          int minIdx = i;
          for (int j = i + 1; j < 9; j++) {
            if (window[j] < window[minIdx]) {
              minIdx = j;
            }
          }
          int tmp = window[i];
          window[i] = window[minIdx];
          window[minIdx] = tmp;
        }
        int med = window[4];
        int rgb = (med << 16) | (med << 8) | med;
        out.setRGB(x, y, (0xFF << 24) | rgb);
      }
    }
    return out;
  }

  /**
   * 
   * @param v  - value (stay within low and high)
   * @param lo - low value cutoff
   * @param hi - hi value cutoff
   * @return - return value
   */
  private static int clamp(int v, int lo, int hi) {
    return (v < lo) ? lo : (v > hi ? hi : v);
  }

  /**
   * Allows images without borders to be detected and scanned.
   * 
   * @param src - image to pad
   * @param pad - amount of pad
   * @return - padded image
   */
  private static BufferedImage addPadding(BufferedImage src, int pad) {
    if (pad <= 0) {
      return src;
    }
    int w = src.getWidth();
    int h = src.getHeight();
    BufferedImage out = new BufferedImage(w + pad * 2, h + pad * 2, BufferedImage.TYPE_BYTE_BINARY);
    // fill white
    for (int y = 0; y < out.getHeight(); y++) {
      for (int x = 0; x < out.getWidth(); x++) {
        out.setRGB(x, y, 0xFFFFFFFF);
      }
    }
    Graphics2D g = out.createGraphics();
    try {
      g.drawImage(src, pad, pad, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  /**
   * Apply Otsu’s thresholding to a grayscale image.
   *
   * @param gray Grayscale image.
   * @return Binarized image.
   */
  public static BufferedImage otsuThreshold(BufferedImage gray) {
    int[] hist = new int[256];
    for (int y = 0; y < gray.getHeight(); y++) {
      for (int x = 0; x < gray.getWidth(); x++) {
        int value = gray.getRaster().getSample(x, y, 0);
        hist[value]++;
      }
    }

    int total = gray.getWidth() * gray.getHeight();

    float sum = 0;
    for (int t = 0; t < 256; t++) {
      sum += t * hist[t];
    }

    float sumB = 0;
    int wB = 0;
    int threshold = 0;
    float varMax = 0;

    for (int t = 0; t < 256; t++) {
      wB += hist[t];
      if (wB == 0) {
        continue;
      }
      int wF = total - wB;
      if (wF == 0) {
        break;
      }

      sumB += (float) (t * hist[t]);
      float mB = sumB / wB;
      float mF = (sum - sumB) / wF;
      float varBetween = (float) wB * wF * (mB - mF) * (mB - mF);

      if (varBetween > varMax) {
        varMax = varBetween;
        threshold = t;
      }
    }

    BufferedImage binarized = new BufferedImage(gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_BINARY);

    for (int y = 0; y < gray.getHeight(); y++) {
      for (int x = 0; x < gray.getWidth(); x++) {
        int value = gray.getRaster().getSample(x, y, 0);
        binarized.setRGB(x, y, value < threshold ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
      }
    }
    return binarized;
  }

  /**
   * Resize an image by scale factor.
   *
   * @param src   Source image.
   * @param scale Scale factor (0 < scale <= 1).
   * @return Resized image.
   */
  private static BufferedImage resizeImage(BufferedImage src, double scale) {
    int newW = Math.max(1, (int) (src.getWidth() * scale));
    int newH = Math.max(1, (int) (src.getHeight() * scale));

    BufferedImage resized = new BufferedImage(newW, newH, src.getType());
    Graphics2D g2d = resized.createGraphics();
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2d.drawImage(src, 0, 0, newW, newH, null);
    g2d.dispose();

    return resized;
  }

  /**
   * rotateImage: Turns an image.
   * 
   * @param src   - image to rotate
   * @param angle - radians of rotation - 2pi is a full circle.
   * @return - new image
   */
  private static BufferedImage rotateImage(BufferedImage src, double angle) {
    int w = src.getWidth();
    int h = src.getHeight();
    BufferedImage dst = new BufferedImage(w, h, src.getType());
    Graphics2D g2d = dst.createGraphics();
    g2d.rotate(angle, w / 2.0, h / 2.0);
    g2d.drawImage(src, 0, 0, null);
    g2d.dispose();
    return dst;
  }

  /**
   * Save ZXing’s BitMatrix as a debug PNG file.
   *
   * @param matrix   BitMatrix to save.
   * @param fileName File name suffix.
   * @throws IOException If saving fails.
   */
  private void saveBitMatrix(BitMatrix matrix, String fileName) throws IOException {
    int width = matrix.getWidth();
    int height = matrix.getHeight();
    BufferedImage bmImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int val = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
        bmImage.setRGB(x, y, val);
      }
    }
    String safeName = new File(fileName).getName().replaceAll("[^a-zA-Z0-9._-]", "_");
    File outFile = new File("debug", String.format("bitmatrix-%s.png", safeName));
    outFile.getParentFile().mkdirs();
    ImageIO.write(bmImage, "png", outFile);
  }

  /**
   * Try decoding with Hybrid first, then Global histogram binarizer.
   * 
   * @param img      - image to decode
   * @param safeName - temp file name
   * @param entry    - might be pieces
   * @return - result of scan
   * @throws Exception - possible exception
   */
  private RecognitionResult tryDecodeWithBinarizers(BufferedImage img, String safeName, RecognitionEntry entry)
      throws Exception {

    LuminanceSource source = new BufferedImageLuminanceSource(img);

    // 1. Try Hybrid
    try {
      BinaryBitmap hybrid = new BinaryBitmap(new HybridBinarizer(source));
      BarcodeLog.debug("decode", "Trying Hybrid binarizer on %s", safeName);

      // Inspect candidate BitMatrix before decoding
      BitMatrix candidate = hybrid.getBlackMatrix();
      int w = candidate.getWidth();
      int h = candidate.getHeight();
      double aspect = (double) w / (double) h;
      if (w < 8 || h < 8 || aspect < 0.5 || aspect > 2.0) {
        BarcodeLog.warn("decode", "Skipping Hybrid result: nonsensical matrix %dx%d (aspect=%.2f)", w, h, aspect);
        throw NotFoundException.getNotFoundInstance();
      }

      Result result = reader.decode(hybrid, hints);
      BarcodeLog.debug("decode", "Hybrid success: %s format=%s", result.getText(), result.getBarcodeFormat());

      return new RecognitionResult(entry.getDocumentName(), result.getText(), result.getBarcodeFormat().toString(),
          true, null);

    } catch (NotFoundException e1) {
      // 2. Try Global Histogram
      try {
        BinaryBitmap histogram = new BinaryBitmap(new GlobalHistogramBinarizer(source));
        BitMatrix candidate = histogram.getBlackMatrix();
        int w = candidate.getWidth();
        int h = candidate.getHeight();
        double aspect = (double) w / (double) h;
        if (w < 8 || h < 8 || aspect < 0.5 || aspect > 2.0) {
          BarcodeLog.warn("decode", "Skipping Histogram result: nonsensical matrix %dx%d (aspect=%.2f)", w, h, aspect);
          throw NotFoundException.getNotFoundInstance();
        }

        saveBitMatrix(candidate, "histogram-" + safeName);
        BarcodeLog.warn("decode", "Hybrid failed, trying Global Histogram on %s", safeName);

        Result result = reader.decode(histogram, hints);
        BarcodeLog.debug("decode", "Histogram success: %s format=%s", result.getText(), result.getBarcodeFormat());

        return new RecognitionResult(entry.getDocumentName(), result.getText(), result.getBarcodeFormat().toString(),
            true, null);

      } catch (NotFoundException e2) {
        // Sauvola fallback
        BinaryBitmap sauvola = new BinaryBitmap(new SauvolaBinarizer(source));
        saveBitMatrix(sauvola.getBlackMatrix(), "sauvola-" + safeName);
        BarcodeLog.warn("decode", "Histogram failed, trying Sauvola on %s", safeName);
        Result result = reader.decode(sauvola, hints);
        return new RecognitionResult(entry.getDocumentName(), result.getText(),
            result.getBarcodeFormat().toString(), true, null);
      } catch (Exception e3) {
        BarcodeLog.error("decode", "All binarizers (and PURE_BARCODE) failed on %s", safeName);
        throw e3;
      }
    }
  }

  /**
   * Single decode with preprocessing and two binarizers; logs each stage.
   * 
   * @param image    - image to decode
   * @param fileName - nameof documennt (for debugging)
   * @param entry    - slice
   * @return - result of decoding
   */
  public RecognitionResult decodeOnce(BufferedImage image, String fileName, RecognitionEntry entry) {
    try {
      BufferedImage clean = preprocess(image, fileName, this.pre);

      BarcodeLog.info("Decoding %s: size=%dx%d, type=%d%n", fileName, clean.getWidth(), clean.getHeight(),
          clean.getType());

      LuminanceSource source = new BufferedImageLuminanceSource(clean);

      // 1) Hybrid
      try {
        BinaryBitmap bb = new BinaryBitmap(new HybridBinarizer(source));
        Result r = reader.decode(bb, hints);
        return new RecognitionResult(entry.getDocumentName(), r.getText(), r.getBarcodeFormat().toString(), true, null);
      } catch (NotFoundException ignore) {
        // fall through
      } finally {
        reader.reset();
      }

      // 2) Global histogram
      try {
        BinaryBitmap bb = new BinaryBitmap(new GlobalHistogramBinarizer(source));
        Result r = reader.decode(bb, hints);
        return new RecognitionResult(entry.getDocumentName(), r.getText(), r.getBarcodeFormat().toString(), true, null);
      } catch (NotFoundException ignore) {
        // fall through
      } finally {
        reader.reset();
      }

      return new RecognitionResult(entry.getDocumentName(), null, null, false, "No barcode found");
    } catch (Exception e) {
      return new RecognitionResult(entry.getDocumentName(), null, null, false, "Error: " + e.getMessage());
    } finally {
      reader.reset();
    }
  }

  /**
   * Decode across rotations; returns multiple results (one per angle).
   * 
   * @param image    - image to decode
   * @param fileName - name of document
   * @param entry    - entry (might be pieces)
   * @return - results
   * @throws Exception - possible failures
   */
  public List<RecognitionResult> decodeAllAngles(BufferedImage image, String fileName, RecognitionEntry entry)
      throws Exception {
    List<RecognitionResult> out = new ArrayList<>();
    double step = (Math.PI * 2.0) / (double) rotationSections;

    for (int i = 0; i < rotationSections; i++) {
      double angle = i * step;
      BufferedImage rotated = (i == 0) ? image : rotateImage(image, angle);
      String tag = (i == 0) ? String.format(Locale.ROOT, "%s@0deg", fileName)
          : String.format(Locale.ROOT, "%s@%.1fdeg", fileName, Math.toDegrees(angle));
      RecognitionResult res = decodeOnce(rotated, tag, entry);
      out.add(res);
    }
    return out;
  }

  /**
   * 
   * @param img      - image to save
   * @param fileName - document name
   * @param stage    - where are we in process
   * @throws IOException - possible failure
   */
  private void saveDebugImage(BufferedImage img, String fileName, String stage) throws IOException {
    String safeName = new File(fileName).getName();
    File outFile = new File("debug", String.format("%s-%s.png", stage, safeName));
    outFile.getParentFile().mkdirs();
    ImageIO.write(img, "png", outFile);
  }

  /**
   * 
   * @param clean    - image to process
   * @param fileName - file name
   * @param entry    - entry piece
   * @return - result of scan
   */
  private RecognitionResult tryDecodeAllScales(BufferedImage clean, String fileName, RecognitionEntry entry) {
    double[] scales = { 0.75, 0.5, 0.25 };
    for (double scale : scales) {
      BufferedImage resized = resizeImage(clean, scale);
      BarcodeLog.debug("decode", "Retrying %s at scale %.2f -> %dx%d", fileName, scale, resized.getWidth(),
          resized.getHeight());
      try {
        return tryDecodeWithBinarizers(resized, fileName + "-scaled-" + scale, entry);
      } catch (NotFoundException ignored) {
        // keep looping
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    /// String documentName, String text, String format, boolean success, String
    /// message
    return new RecognitionResult(fileName, "", "", false, "No barcode found (after scaling)");
  }

  /**
   * Decode an image with preprocessing, binarizers, and scale retries.
   *
   * @param image    The image to decode.
   * @param fileName File name (used for debug/logging).
   * @param entry    Metadata about the recognition task.
   * @return RecognitionResult with success or failure details.
   */
  public RecognitionResult decode(BufferedImage image, String fileName, RecognitionEntry entry) {
    BufferedImage clean = null;
    RecognitionResult recognitionResult = null;
    try {
      // --- Preprocess ---
      clean = preprocess(image, fileName, this.pre);

      BarcodeLog.debug("decode", "Decoding %s: size=%dx%d, type=%d", fileName, clean.getWidth(), clean.getHeight(),
          clean.getType());
      saveDebugImage(clean, fileName, "preprocessed");

      // --- Decode at original scale ---
      recognitionResult = tryDecodeWithBinarizers(clean, fileName, entry);
    } catch (NotFoundException nf) {
      // --- Retry at smaller scales ---
      recognitionResult = tryDecodeAllScales(clean, fileName, entry);
    } catch (Exception e) {
      recognitionResult = new RecognitionResult(fileName, "", "", false, "Error: " + e.getMessage());
    } finally {
      reader.reset();
    }
    return recognitionResult;
  }

  /**
   * Decode a single BufferedImage into a RecognitionResult.
   *
   * @param image    The image to decode.
   * @param fileName The name of the file that we are scanning (for debug output)
   * @param entry    Metadata about the recognition task.
   * @return A RecognitionResult containing decoded text or an error if not found.
   */
  /**
   * Preprocess the input image: 1. Convert to grayscale 2. Normalize contrast
   * (RescaleOp) 3. Apply fixed threshold (Otsu binarization)
   */

  public ArrayList<RecognitionResult> decode(BufferedImage image, String name, RecognitionEntry entry, int sections) {
    ArrayList<RecognitionResult> results = new ArrayList<RecognitionResult>();
    try {
      // try each rotation
      double step = (2 * Math.PI) / sections;
      for (int i = 0; i < sections; i++) {
        double angle = i * step;
        BufferedImage rotated = rotateImage(image, angle);
        RecognitionResult result = decode(rotated, name, entry);
        results.add(result);
      }
    } catch (Exception e) {
    }
    return results;
  }

}
