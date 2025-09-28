package com.google.zxing.opencv;

import com.google.zxing.NotFoundException;
import com.google.zxing.ResultPoint;
import com.google.zxing.datamatrix.detector.ExternalRectangleDetector;

import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;

/**
 * OpenCV-based implementation of ExternalRectangleDetector.
 */

public final class OpenCVRectangleDetector implements ExternalRectangleDetector {

  public String getName() {
    return "OpenCV";
  }

  public ResultPoint[] detect(BitMatrix image) throws NotFoundException {
    // Convert BitMatrix → Mat here
    Mat mat = ZXingToOpenCVConverter.toMat(image);
    return detectInternal(mat);
}
  
  @Override
  public ResultPoint[] detectInternal(Mat image) throws NotFoundException {
    if (!(image instanceof Mat)) {
      throw NotFoundException.getNotFoundInstance();
    }
    Mat mat = (Mat) image;

    Mat gray = new Mat();
    opencv_imgproc.cvtColor(mat, gray, opencv_imgproc.COLOR_BGR2GRAY);

    Mat binary = new Mat();
    opencv_imgproc.adaptiveThreshold(
        gray, binary, 255,
        opencv_imgproc.ADAPTIVE_THRESH_MEAN_C,
        opencv_imgproc.THRESH_BINARY,
        15, 10);

    MatVector contours = new MatVector();
    Mat hierarchy = new Mat();
    opencv_imgproc.findContours(
        binary, contours, hierarchy,
        opencv_imgproc.RETR_EXTERNAL,
        opencv_imgproc.CHAIN_APPROX_SIMPLE);

    int w = mat.cols();
    int h = mat.rows();
    double minArea = (w * h) * 0.0002;
    double maxArea = (w * h) * 0.10;

    for (long i = 0; i < contours.size(); i++) {
      Mat contour = contours.get(i);
      Rect rect = opencv_imgproc.boundingRect(contour);

      double area = (double) rect.width() * (double) rect.height();
      if (area < minArea || area > maxArea) {
        continue;
      }

      double aspect = (double) rect.width() / (double) rect.height();
      if (aspect < 0.6 || aspect > 1.6) {
        continue;
      }

      ResultPoint tl = new ResultPoint(rect.x(), rect.y());
      ResultPoint bl = new ResultPoint(rect.x(), rect.y() + rect.height());
      ResultPoint br = new ResultPoint(rect.x() + rect.width(), rect.y() + rect.height());
      ResultPoint tr = new ResultPoint(rect.x() + rect.width(), rect.y());

      return new ResultPoint[] { tl, bl, br, tr };
    }

    throw NotFoundException.getNotFoundInstance();
  }
}
