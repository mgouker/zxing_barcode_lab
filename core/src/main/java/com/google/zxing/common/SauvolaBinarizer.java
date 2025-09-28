package com.google.zxing.common;

import com.google.zxing.Binarizer;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;

public class SauvolaBinarizer extends Binarizer {
  private static final int WINDOW_SIZE = 15;
  private static final double K = 0.5;
  private static final int R = 128;

  private final byte[] matrix; // full grayscale matrix
  private final int width;
  private final int height;

  public SauvolaBinarizer(LuminanceSource source) {
    super(source);
    this.width = source.getWidth();
    this.height = source.getHeight();
    this.matrix = source.getMatrix(); // row-major, length = width*height
  }

  @Override
  public BitMatrix getBlackMatrix() {
    BitMatrix out = new BitMatrix(width, height);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        double threshold = localSauvolaThreshold(x, y, WINDOW_SIZE);
        int gray = matrix[y * width + x] & 0xFF;
        if (gray < threshold) {
          out.set(x, y);
        }
      }
    }
    return out;
  }

  private double localSauvolaThreshold(int x, int y, int w) {
    int half = w / 2;
    int sum = 0;
    int sumSq = 0;
    int count = 0;

    for (int j = y - half; j <= y + half; j++) {
      if (j < 0 || j >= height) {
        continue;
      }
      int rowOffset = j * width;
      for (int i = x - half; i <= x + half; i++) {
        if (i < 0 || i >= width) {
          continue;
        }
        int val = matrix[rowOffset + i] & 0xFF;
        sum += val;
        sumSq += val * val;
        count++;
      }
    }

    double mean = sum / (double) count;
    double variance = (sumSq / (double) count) - (mean * mean);
    double stddev = Math.sqrt(Math.max(variance, 0));
    return mean * (1 + K * (stddev / R - 1));
  }

  @Override
  public Binarizer createBinarizer(LuminanceSource newSource) {
    return new SauvolaBinarizer(newSource);
  }

  @Override
  public BitArray getBlackRow(int y, BitArray row) throws NotFoundException {
    // TODO Auto-generated method stub
    return null;
  }
}
