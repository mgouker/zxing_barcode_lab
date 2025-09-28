package com.google.zxing.common.detector;

import com.google.zxing.NotFoundException;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.BitMatrix;

public interface ExternalRectangleDetector {
  ResultPoint[] detect(BitMatrix image) throws NotFoundException;

  default String getName() {
    return getClass().getSimpleName();
  }
}
