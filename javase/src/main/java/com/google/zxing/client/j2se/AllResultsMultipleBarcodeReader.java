package com.google.zxing.client.j2se;

import com.google.zxing.multi.MultipleBarcodeReader;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Reader;
import com.google.zxing.ReaderException;
import com.google.zxing.ResultPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A MultipleBarcodeReader that does NOT deduplicate by text.
 * It will return all results found, even if they have identical text.
 */
public final class AllResultsMultipleBarcodeReader implements MultipleBarcodeReader {

  private static final int MAX_DEPTH = 4;
  private static final int MIN_DIMENSION_TO_RECUR = 100;

  private final Reader delegate;

  public AllResultsMultipleBarcodeReader(Reader delegate) {
    this.delegate = delegate;
  }

  @Override
  public Result[] decodeMultiple(BinaryBitmap image, Map<DecodeHintType,?> hints)
      throws NotFoundException {
    List<Result> results = new ArrayList<>();
    doDecodeMultiple(image, hints, results, 0, 0, 0);
    if (results.isEmpty()) {
      throw NotFoundException.getNotFoundInstance();
    }
    return results.toArray(new Result[0]);
  }

  @Override
  public Result[] decodeMultiple(BinaryBitmap image) throws NotFoundException {
    return decodeMultiple(image, null);
  }

  private boolean isDuplicate(Result newResult, List<Result> existing) {
    for (Result r : existing) {
      ResultPoint[] pts1 = r.getResultPoints();
      ResultPoint[] pts2 = newResult.getResultPoints();
      if (pts1 != null && pts2 != null) {
        for (ResultPoint p1 : pts1) {
          for (ResultPoint p2 : pts2) {
            if (Math.abs(p1.getX() - p2.getX()) < 5 &&
                Math.abs(p1.getY() - p2.getY()) < 5) {
              return true; // same location
            }
          }
        }
      }
    }
    return false;
  }

  private void doDecodeMultiple(BinaryBitmap image,
                                Map<DecodeHintType,?> hints,
                                List<Result> results,
                                int xOffset,
                                int yOffset,
                                int currentDepth) {
    if (currentDepth > MAX_DEPTH) {
      return;
    }

    Result result;
    try {
      result = delegate.decode(image, hints);
    } catch (ReaderException ignored) {
      return;
    }

    // test for duplicates
    if (!isDuplicate(result, results)) {
      results.add(translateResultPoints(result, xOffset, yOffset));
    }

    ResultPoint[] resultPoints = result.getResultPoints();
    if (resultPoints == null || resultPoints.length == 0) {
      return;
    }

    int width = image.getWidth();
    int height = image.getHeight();

    float minX = width;
    float minY = height;
    float maxX = 0.0f;
    float maxY = 0.0f;
    for (ResultPoint point : resultPoints) {
      if (point == null) {
        continue;
      }
      float x = point.getX();
      float y = point.getY();
      if (x < minX) {
        minX = x;
      }
      if (y < minY) {
        minY = y;
      }
      if (x > maxX) {
        maxX = x;
      }
      if (y > maxY) {
        maxY = y;
      }
    }

    if (minX > MIN_DIMENSION_TO_RECUR) {
      doDecodeMultiple(image.crop(0, 0, (int) minX, height), hints, results,
                       xOffset, yOffset, currentDepth + 1);
    }
    if (minY > MIN_DIMENSION_TO_RECUR) {
      doDecodeMultiple(image.crop(0, 0, width, (int) minY), hints, results,
                       xOffset, yOffset, currentDepth + 1);
    }
    if (maxX < width - MIN_DIMENSION_TO_RECUR) {
      doDecodeMultiple(image.crop((int) maxX, 0, width - (int) maxX, height),
                       hints, results, xOffset + (int) maxX, yOffset, currentDepth + 1);
    }
    if (maxY < height - MIN_DIMENSION_TO_RECUR) {
      doDecodeMultiple(image.crop(0, (int) maxY, width, height - (int) maxY),
                       hints, results, xOffset, yOffset + (int) maxY,
                       currentDepth + 1);
    }
  }

  private static Result translateResultPoints(Result result, int xOffset, int yOffset) {
    ResultPoint[] oldPoints = result.getResultPoints();
    if (oldPoints == null) {
      return result;
    }
    ResultPoint[] newPoints = new ResultPoint[oldPoints.length];
    for (int i = 0; i < oldPoints.length; i++) {
      ResultPoint oldPoint = oldPoints[i];
      if (oldPoint != null) {
        newPoints[i] = new ResultPoint(oldPoint.getX() + xOffset,
                                       oldPoint.getY() + yOffset);
      }
    }
    return new Result(result.getText(), result.getRawBytes(),
                      newPoints, result.getBarcodeFormat());
  }

}
