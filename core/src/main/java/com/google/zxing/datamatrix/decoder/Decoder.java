/*
 * Copyright 2007 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.datamatrix.decoder;

import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.google.zxing.common.reedsolomon.GenericGF;
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder;
import com.google.zxing.common.reedsolomon.ReedSolomonException;

/**
 * <p>
 * The main class which implements Data Matrix Code decoding -- as opposed to
 * locating and extracting the Data Matrix Code from an image.
 * </p>
 *
 * @author bbrown@google.com (Brian Brown)
 */
public final class Decoder {

  private final ReedSolomonDecoder rsDecoder;

  public Decoder() {
    rsDecoder = new ReedSolomonDecoder(GenericGF.DATA_MATRIX_FIELD_256);
  }

  /**
   * <p>
   * Convenience method that can decode a Data Matrix Code represented as a 2D
   * array of booleans. "true" is taken to mean a black module.
   * </p>
   *
   * @param image booleans representing white/black Data Matrix Code modules
   * @return text and bytes encoded within the Data Matrix Code
   * @throws FormatException   if the Data Matrix Code cannot be decoded
   * @throws ChecksumException if error correction fails
   */
  public DecoderResult decode(boolean[][] image) throws FormatException, ChecksumException {
    return decode(BitMatrix.parse(image));
  }

  /**
   * <p>
   * Decodes a Data Matrix Code represented as a {@link BitMatrix}. A 1 or "true"
   * is taken to mean a black module.
   * </p>
   *
   * @param bits booleans representing white/black Data Matrix Code modules
   * @return text and bytes encoded within the Data Matrix Code
   * @throws FormatException   if the Data Matrix Code cannot be decoded
   * @throws ChecksumException if error correction fails
   */
  public DecoderResult decode(BitMatrix bits) throws FormatException, ChecksumException {
    BitMatrixParser parser = new BitMatrixParser(bits);
    Version version = parser.getVersion();

    byte[] codewords;
    try {
      codewords = parser.readCodewords();
    } catch (FormatException fe) {
      System.out.printf("Decoder", "Failed to read codewords: %s", fe.getMessage());
      throw fe;
    }

    DataBlock[] dataBlocks;
    dataBlocks = DataBlock.getDataBlocks(codewords, version);

    System.out.printf("Decoder", "version=%s, codewords=%d, dataBlocks=%d",
        version, codewords != null ? codewords.length : 0,
        dataBlocks != null ? dataBlocks.length : 0);

    int totalBytes = 0;
    for (DataBlock db : dataBlocks) {
      totalBytes += db.getNumDataCodewords();
    }
    System.out.printf("Decoder", "total data bytes=%d", totalBytes);

    byte[] resultBytes = new byte[totalBytes];
    int errorsCorrected = 0;
    int dataBlocksCount = dataBlocks.length;

    for (int j = 0; j < dataBlocksCount; j++) {
      DataBlock dataBlock = dataBlocks[j];
      byte[] codewordBytes = dataBlock.getCodewords();
      int numDataCodewords = dataBlock.getNumDataCodewords();

      try {
        int corrected = correctErrors(codewordBytes, numDataCodewords);
        errorsCorrected += corrected;
        System.out.printf("Decoder", "Block %d corrected %d errors", j, corrected);
      } catch (ChecksumException ce) {
        System.out.printf("Decoder", "Checksum failed on block %d: %s", j, ce.getMessage());
        throw ce;
      }

      for (int i = 0; i < numDataCodewords; i++) {
        resultBytes[i * dataBlocksCount + j] = codewordBytes[i];
      }
    }

    try {
      DecoderResult result = DecodedBitStreamParser.decode(resultBytes);
      System.out.printf("Decoder", "Decoded text: %s", result.getText());
      result.setErrorsCorrected(errorsCorrected);
      return result;
    } catch (FormatException fe) {
      System.out.printf("Decoder", "Failed to parse bit stream: %s", fe.getMessage());
      throw fe;
    }
  }

  /**
   * <p>
   * Given data and error-correction codewords received, possibly corrupted by
   * errors, attempts to correct the errors in-place using Reed-Solomon error
   * correction.
   * </p>
   *
   * @param codewordBytes    data and error correction codewords
   * @param numDataCodewords number of codewords that are data bytes
   * @return the number of errors corrected
   * @throws ChecksumException if error correction fails
   */
  private int correctErrors(byte[] codewordBytes, int numDataCodewords) throws ChecksumException {
    int numCodewords = codewordBytes.length;
    // First read into an array of ints
    int[] codewordsInts = new int[numCodewords];
    for (int i = 0; i < numCodewords; i++) {
      codewordsInts[i] = codewordBytes[i] & 0xFF;
    }
    int errorsCorrected = 0;
    try {
      errorsCorrected = rsDecoder.decodeWithECCount(codewordsInts, codewordBytes.length - numDataCodewords);
    } catch (ReedSolomonException ignored) {
      System.out.printf("correctErrors has reed solomon exception.\n");
      throw ChecksumException.getChecksumInstance();
    }
    // Copy back into array of bytes -- only need to worry about the bytes that were
    // data
    // We don't care about errors in the error-correction codewords
    for (int i = 0; i < numDataCodewords; i++) {
      codewordBytes[i] = (byte) codewordsInts[i];
    }
    return errorsCorrected;
  }

}
