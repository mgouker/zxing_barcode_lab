package com.google.zxing.common.detector;

import com.google.zxing.NotFoundException;
import com.google.zxing.ResultPoint;

public interface ExternalRectangleDetector {
  ResultPoint[] detect(Object image) throws NotFoundException;

  String getName();
}
