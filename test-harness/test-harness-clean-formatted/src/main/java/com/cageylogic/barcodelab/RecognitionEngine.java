package com.cageylogic.barcodelab;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;


/** ZXing-based barcode recognition engine. */
public class RecognitionEngine {

  private final MultiFormatReader reader;
  private final Map<DecodeHintType, Object> hints;

  /** Default constructor: focuses on CODE_128 and DATA_MATRIX. */
  public RecognitionEngine() {
    this(List.of(BarcodeFormat.CODE_128, BarcodeFormat.DATA_MATRIX));
  }

/**
* Constructor with custom formats.
*
* @param formats List of barcode formats to recognize.
*/
public RecognitionEngine(List<BarcodeFormat> formats) {
  this.reader = new MultiFormatReader();
  this.hints = new EnumMap<>(DecodeHintType.class);
  hints.put(DecodeHintType.POSSIBLE_FORMATS, formats);
}

/**
* Decode a single BufferedImage into a RecognitionResult.
*
* @param image The image to decode.
* @param entry Metadata about the recognition task.
* @return A RecognitionResult containing decoded text, or null if none.
*/
public RecognitionResult decode(BufferedImage image, RecognitionEntry entry) {
  try {
    LuminanceSource source = new BufferedImageLuminanceSource(image);
    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

    Result result = reader.decode(bitmap, hints);

    return new RecognitionResult(
    entry.getId(),
    result.getText(),
    result.getBarcodeFormat().toString(),
    true,
    null);
  } catch (NotFoundException e) {
  return new RecognitionResult(entry.getId(), null, null, false, "No barcode found");
} catch (Exception e) {
return new RecognitionResult(
entry.getId(), null, null, false, "Error: " + e.getMessage());
} finally {
reader.reset();
}
}
}
