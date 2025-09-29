package com.google.zxing.opencv;

import org.bytedeco.opencv.opencv_core.Mat;
import org.opencv.core.CvType;

import com.google.zxing.common.BitMatrix;

public final class ZXingToOpenCVConverter {

  private ZXingToOpenCVConverter() { }

  public static Mat toMat(BitMatrix bm) {
    int w = bm.getWidth();
    int h = bm.getHeight();
    Mat m = new Mat(h, w, CvType.CV_8UC1);
    byte[] row = new byte[w];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        row[x] = (byte) (bm.get(x, y) ? 255 : 0);
      }
      m.ptr(y, 0).put(row);
    }
    return m;
  }
}
