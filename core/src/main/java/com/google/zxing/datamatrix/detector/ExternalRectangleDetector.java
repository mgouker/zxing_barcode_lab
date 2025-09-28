package com.google.zxing.datamatrix.detector;

import com.google.zxing.NotFoundException;
import com.google.zxing.ResultPoint;

/**
 * Abstraction for external detectors (like OpenCV) that can propose rectangle corners.
 */
public interface ExternalRectangleDetector {
  ResultPoint[] detect(Object image) throws NotFoundException;
}
