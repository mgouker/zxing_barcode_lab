package com.google.zxing.client.j2se;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;

import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class DebugDecode {

  private DebugDecode() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Usage: DebugDecode <imagefile> [tryharder]");
      return;
    }

    boolean tryHarder = args.length > 1 &&
        (args[1].equalsIgnoreCase("tryharder") || args[1].equalsIgnoreCase("-t"));

    BufferedImage image = ImageIO.read(new File(args[0]));
    if (image == null) {
      System.err.println("Could not read image: " + args[0]);
      return;
    }

    LuminanceSource source = new BufferedImageLuminanceSource(image);

    // Hints
    Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    if (tryHarder) {
      hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    }
    hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(
        BarcodeFormat.QR_CODE,
        BarcodeFormat.PDF_417,
        BarcodeFormat.CODE_128,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.ITF,
        BarcodeFormat.CODE_39,
        BarcodeFormat.CODE_93,
        BarcodeFormat.UPC_A,
        BarcodeFormat.EAN_13
    ));
    
    hints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);

    MultiFormatReader formatReader = new MultiFormatReader();

    // Try with HybridBinarizer first
    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
    try {
      Result result = formatReader.decode(bitmap, hints);
      dumpResult(result);
      return;
    } catch (NotFoundException e) {
      System.out.println("HybridBinarizer failed. Trying GlobalHistogram...");
    }

    // Fallback to GlobalHistogramBinarizer
    bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
    try {
      Result result = formatReader.decode(bitmap, hints);
      dumpResult(result);
    } catch (NotFoundException e) {
      System.out.println("No barcode found in image: " + args[0]);
    }
  }

  private static void dumpResult(Result result) {
    if (result == null) {
      System.out.println("NO_RESULT");
      return;
    }
    System.out.println("Format: " + result.getBarcodeFormat());
    System.out.println("Text: " + result.getText());
    if (result.getResultMetadata() != null) {
      System.out.println("Metadata: " + result.getResultMetadata());
    }
  }
}
