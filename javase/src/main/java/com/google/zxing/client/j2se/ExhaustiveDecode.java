package com.google.zxing.client.j2se;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.Reader;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ExhaustiveDecode: Runs all ZXing readers on the same image repeatedly,
 * removing found barcodes and collecting all results until none remain.
 */
public final class ExhaustiveDecode {

  private ExhaustiveDecode() {
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Usage: ExhaustiveDecode <imagefile> [tryharder]");
      return;
    }

    boolean tryHarder = args.length > 1 && (args[1].equalsIgnoreCase("tryharder") || args[1].equalsIgnoreCase("-t"));

    BufferedImage image = ImageIO.read(new File(args[0]));
    if (image == null) {
      System.err.println("Could not read image: " + args[0]);
      return;
    }

    LuminanceSource source = new BufferedImageLuminanceSource(image);
    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

    Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    if (tryHarder) {
      hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    }
    hints.put(DecodeHintType.POSSIBLE_FORMATS,
        EnumSet.of(BarcodeFormat.QR_CODE, BarcodeFormat.PDF_417, BarcodeFormat.CODE_128, BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.ITF, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.UPC_A,
            BarcodeFormat.EAN_13));

    List<Result> allResults = decodeExhaustively(bitmap, hints);

    if (allResults.isEmpty()) {
      System.out.println("NO_RESULT");
    } else {
      for (Result r : allResults) {
        System.out.println(r.getBarcodeFormat() + ":" + r.getText());
      }
    }
  }

  private static int[] safeBoundingBox(Result r, int imgW, int imgH) {
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = Float.MIN_VALUE;
    float maxY = Float.MIN_VALUE;
    for (ResultPoint p : r.getResultPoints()) {
      if (p == null) {
        continue;
      }
      minX = Math.min(minX, p.getX());
      minY = Math.min(minY, p.getY());
      maxX = Math.max(maxX, p.getX());
      maxY = Math.max(maxY, p.getY());
    }
    int x = Math.max(0, (int) Math.floor(minX));
    int y = Math.max(0, (int) Math.floor(minY));
    int w = Math.max(0, (int) Math.ceil(maxX - minX));
    int h = Math.max(0, (int) Math.ceil(maxY - minY));
    if (x + w > imgW) {
      w = imgW - x;
    }
    if (y + h > imgH) {
      h = imgH - y;
    }
    return new int[] { x, y, w, h };
  }

//  private static List<Result> decodeExhaustively(BinaryBitmap bitmap, Map<DecodeHintType, ?> hints) {
//    List<Result> results = new ArrayList<>();
//    List<Reader> readers = ReaderFactory.buildAllReaders(hints);
//
//    for (Reader reader : readers) {
//      boolean found;
//      do {
//        found = false;
//        try {
//          Result result = reader.decode(bitmap, hints);
//          if (result != null) {
//            results.add(result);
//            ResultPoint[] points = result.getResultPoints();
//            if (points != null && points.length > 0) {
//              float minX = Float.MAX_VALUE;
//              float minY = Float.MAX_VALUE;
//              float maxX = 0.0f;
//              float maxY = 0.0f;
//              for (ResultPoint p : points) {
//                if (p != null) {
//                  minX = Math.min(minX, p.getX());
//                  minY = Math.min(minY, p.getY());
//                  maxX = Math.max(maxX, p.getX());
//                  maxY = Math.max(maxY, p.getY());
//                }
//              }
//              int pad = 10; // pixels of padding
//              int left = Math.max(0, (int) minX - pad);
//              int top = Math.max(0, (int) minY - pad);
//              int width = (int) (maxX - minX) + 2 * pad;
//              int height = (int) (maxY - minY) + 2 * pad;
//              bitmap.clear(left, top, width, height);
//            }
//            found = true;
//          }
//        } catch (NotFoundException e) {
//          // No more results for this reader
//        } catch (Exception e) {
//          e.printStackTrace(System.err);
//        }
//      } while (found);
//    }
//    return results;
//  }
//}

  private static final PrintWriter DEBUG_LOG;
  static {
    try {
      DEBUG_LOG = new PrintWriter(new FileWriter("decode_debug.log", true));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void debug(String msg) {
    DEBUG_LOG.println("[" + System.currentTimeMillis() + "] " + msg);
    DEBUG_LOG.flush();
  }

  private static List<Result> decodeExhaustively(BinaryBitmap bitmap, Map<DecodeHintType, ?> hints) {
    List<Result> results = new ArrayList<>();
    List<Reader> readers = ReaderFactory.buildAllReaders(hints);

    for (Reader reader : readers) {
      debug("Starting reader: " + reader.getClass().getSimpleName());
      int stableIterations = 0;
      Set<String> seenRegions = new HashSet<>();

      while (stableIterations < 2) {
        try {
          Result r = reader.decode(bitmap, hints);
          results.add(r);

          // Estimate bounding box from ResultPoints
          ResultPoint[] pts = r.getResultPoints();
          if (pts != null && pts.length > 0) {
            int minX = (int) Arrays.stream(pts).mapToDouble(ResultPoint::getX).min().getAsDouble();
            int maxX = (int) Arrays.stream(pts).mapToDouble(ResultPoint::getX).max().getAsDouble();
            int minY = (int) Arrays.stream(pts).mapToDouble(ResultPoint::getY).min().getAsDouble();
            int maxY = (int) Arrays.stream(pts).mapToDouble(ResultPoint::getY).max().getAsDouble();

            int pad = 20;
            int x = Math.max(0, minX - pad);
            int y = Math.max(0, minY - pad);
            int w = Math.min(bitmap.getWidth() - x, (maxX - minX) + 2 * pad);
            int h = Math.min(bitmap.getHeight() - y, (maxY - minY) + 2 * pad);

            debug("Clearing region x=" + x + " y=" + y + " w=" + w + " h=" + h);

            int[] box = safeBoundingBox(r, bitmap.getWidth(), bitmap.getHeight());
            String key = box[0] + "," + box[1] + "," + box[2] + "," + box[3];
            if (seenRegions.contains(key)) {
              System.err.println("Duplicate region detected, breaking to avoid loop: " + key);
              break; // or return collected results
            }
            seenRegions.add(key);
            if (box[2] > 0 && box[3] > 0) {
              bitmap.clear(box[0], box[1], box[2], box[3]);
            }
          }

          stableIterations = 0; // reset because we got something new
        } catch (NotFoundException e) {
          stableIterations++;
        } catch (Exception e) {
          debug("Error: " + e.getMessage());
          stableIterations++;
        }
      }
    }

    debug("Total results: " + results.size());
    return results;
  }
}
