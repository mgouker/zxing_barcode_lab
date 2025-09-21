package com.google.zxing.client.j2se;

import com.google.zxing.DecodeHintType;
import com.google.zxing.Reader;
import com.google.zxing.oned.MultiFormatOneDReader;
import com.google.zxing.datamatrix.DataMatrixReader;
import com.google.zxing.pdf417.PDF417Reader;
import com.google.zxing.qrcode.QRCodeReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ReaderFactory:
 * Constructs the set of ZXing readers to run in ExhaustiveDecode.
 */
public final class ReaderFactory {

  private ReaderFactory() { }

  public static List<Reader> buildAllReaders(Map<DecodeHintType, ?> hints) {
    List<Reader> readers = new ArrayList<>();

    // Explicitly list the readers we want to include
    readers.add(new QRCodeReader());
    readers.add(new DataMatrixReader());
    readers.add(new PDF417Reader());
    readers.add(new MultiFormatOneDReader(hints));

    return readers;
  }
}
