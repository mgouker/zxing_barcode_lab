package com.google.zxing.opencv;

import com.google.zxing.NotFoundException;
import com.google.zxing.ResultPoint;

import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.detector.ExternalRectangleDetector;

import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Rect;

/**
 * OpenCV-based implementation of ExternalRectangleDetector.
 */

public final class OpenCVRectangleDetector implements ExternalRectangleDetector {

  @Override
  public String getName() {
    return "OpenCV";
  }

  @Override
  public ResultPoint[] detect(Object image) throws NotFoundException {
    Mat mat = null;
    if (image instanceof BitMatrix) {
      System.out.println("BitMatrix received in detect().");
      // Convert BitMatrix → Mat here
      mat = ZXingToOpenCVConverter.toMat((BitMatrix) image);
    } else if (image instanceof Mat) {
      System.out.println("Mat received in detect().");
      mat = (Mat) image;
    } else {
      System.err.println("invalid object in detect.");
      throw NotFoundException.getNotFoundInstance();
    }

    return detectInternal(mat);
  }

  public ResultPoint[] detectInternal(Mat image) throws NotFoundException {

    Mat gray = new Mat();
    if (image.channels() == 1) {
      // Already grayscale
      gray = image.clone();
    } else {
      opencv_imgproc.cvtColor(image, gray, opencv_imgproc.COLOR_BGR2GRAY);
    }

    Mat binary = new Mat();
    opencv_imgproc.adaptiveThreshold(gray, binary, 255, opencv_imgproc.ADAPTIVE_THRESH_MEAN_C,
        opencv_imgproc.THRESH_BINARY, 15, 10);

    MatVector contours = new MatVector();
    Mat hierarchy = new Mat();
    opencv_imgproc.findContours(binary, contours, hierarchy, opencv_imgproc.RETR_EXTERNAL,
        opencv_imgproc.CHAIN_APPROX_SIMPLE);

    int w = image.cols();
    int h = image.rows();
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
