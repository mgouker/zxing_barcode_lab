package com.google.zxing.client.j2se;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.EnumMap;
import java.util.Map;
import java.util.EnumSet;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.GlobalHistogramBinarizer;


public final class MultiDecode {

  private MultiDecode() { }

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Usage: MultiDecode <imagefile>");
      return;
    }

    boolean tryHarder = args.length > 1 &&
        (args[1].equalsIgnoreCase("tryharder") || args[1].equalsIgnoreCase("-t"));

    try {
      BufferedImage image = ImageIO.read(new File(args[0]));
      if (image == null) {
        System.err.println("Could not read image: " + args[0]);
        return;
      }

      MultiFormatReader formatReader = new MultiFormatReader();
      AllResultsMultipleBarcodeReader multiReader =
          new AllResultsMultipleBarcodeReader(formatReader);

      // Optionally use TRY_HARDER
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

      LuminanceSource source = new BufferedImageLuminanceSource(image);
      
      BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
      Result[] results;
      formatReader.setHints(hints);
      try {
        results = multiReader.decodeMultiple(bitmap, hints);
      } catch (NotFoundException e) {
        // fallback to histogram
        bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source));
        results = multiReader.decodeMultiple(bitmap, hints);
      }

      if (results.length == 0) {
        System.out.println("NO_RESULT");
      } else {
        for (Result r : results) {
          System.out.println(r.getBarcodeFormat() + ":" + r.getText());
        }
      }

    } catch (NotFoundException e) {
      // No barcodes found, exit quietly
    } catch (Exception e) {
      e.printStackTrace(System.err);
    }
  }
}
