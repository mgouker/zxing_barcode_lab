/*
 * Copyright 2008 ZXing authors
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

package com.google.zxing.datamatrix.detector;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.google.zxing.NotFoundException;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DetectorResult;
import com.google.zxing.common.GridSampler;
import com.google.zxing.common.detector.ExternalRectangleDetector;
import com.google.zxing.common.detector.WhiteRectangleDetector;



/**
 * <p>
 * Encapsulates logic that can detect a Data Matrix Code in an image, even if
 * the Data Matrix Code is rotated or skewed, or partially obscured.
 * </p>
 *
 * @author Sean Owen
 */
public final class Detector {

  private final BitMatrix image;
  private final WhiteRectangleDetector rectangleDetector;

  public Detector(BitMatrix image) throws NotFoundException {
    this.image = image;
    rectangleDetector = new WhiteRectangleDetector(image);
  }

  private void saveBitMatrixForDebugging(BitMatrix matrix, String fileName) throws IOException {
    int width = matrix.getWidth();
    int height = matrix.getHeight();
    BufferedImage bmImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int val = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
        bmImage.setRGB(x, y, val);
      }
    }
    String safeName = new File(fileName).getName().replaceAll("[^a-zA-Z0-9._-]", "_");
    File outFile = new File("debug", String.format("bitmatrix-%s.png", safeName));
    outFile.getParentFile().mkdirs();
    ImageIO.write(bmImage, "png", outFile);
  }


  /**
   * <p>
   * Detects a Data Matrix Code in an image.
   * </p>
   *
   * @return {@link DetectorResult} encapsulating results of detecting a Data
   *         Matrix Code
   * @throws NotFoundException if no Data Matrix Code can be found
   */

  public DetectorResult detect() throws NotFoundException {
    ResultPoint[] cornerPoints;

    try {
      cornerPoints = rectangleDetector.detect();
    } catch (NotFoundException nfe) {
      cornerPoints = tryExternalDetectors(image, "primary");
    }

    ResultPoint[] points = detectSolid1(cornerPoints);
    points = detectSolid2(points);
    points[3] = correctTopRight(points);
    if (points[3] == null) {
      // Try external once more if topology is impossible
      ResultPoint[] ext = tryExternalDetectors(image, "topology");
      points = detectSolid2(detectSolid1(ext));
      points[3] = correctTopRight(points);
      if (points[3] == null) {
        throw NotFoundException.getNotFoundInstance();
      }
    }

    points = shiftToModuleCenter(points);

    ResultPoint topLeft = points[0];
    ResultPoint bottomLeft = points[1];
    ResultPoint bottomRight = points[2];
    ResultPoint topRight = points[3];

    int dimensionTop = transitionsBetween(topLeft, topRight) + 1;
    int dimensionRight = transitionsBetween(bottomRight, topRight) + 1;
    if ((dimensionTop & 0x01) == 1) {
      dimensionTop += 1;
    }
    if ((dimensionRight & 0x01) == 1) {
      dimensionRight += 1;
    }

    if (badDimensions(dimensionTop, dimensionRight)) {
      ResultPoint[] ext = tryExternalDetectors(image, "dims");
      points = shiftToModuleCenter(detectSolid2(detectSolid1(ext)));
      topLeft = points[0];
      bottomLeft = points[1];
      bottomRight = points[2];
      topRight = points[3];

      dimensionTop = transitionsBetween(topLeft, topRight) + 1;
      dimensionRight = transitionsBetween(bottomRight, topRight) + 1;
      if ((dimensionTop & 0x01) == 1) {
        dimensionTop += 1;
      }
      if ((dimensionRight & 0x01) == 1) {
        dimensionRight += 1;
      }
    }

    System.out.printf(
        "Corner points: TL(%.1f,%.1f) BL(%.1f,%.1f) BR(%.1f,%.1f) TR(%.1f,%.1f)\n",
        topLeft.getX(), topLeft.getY(),
        bottomLeft.getX(), bottomLeft.getY(),
        bottomRight.getX(), bottomRight.getY(),
        topRight.getX(), topRight.getY());

    System.out.printf("Detector: ->Computed dimensions: top=%d right=%d\n", dimensionTop, dimensionRight);

    BitMatrix bits;
    try {
      bits = sampleGrid(image, topLeft, bottomLeft, bottomRight, topRight, dimensionTop, dimensionRight, false);
    } catch (NotFoundException e) {
      System.out.println("Detector: standard grid failed, retrying with jiggle sampling…\n");
      bits = sampleGridWithJiggle(image, topLeft, bottomLeft, bottomRight, topRight, dimensionTop, dimensionRight);
    }

    try {
      saveBitMatrixForDebugging(bits, "detector-bm-");
    } catch (IOException e) {
      System.err.println("Cannot write bitmap to debugging file.\n");
      e.printStackTrace();
    }
    return new DetectorResult(bits, new ResultPoint[]{topLeft, bottomLeft, bottomRight, topRight});
  }


  private static ResultPoint[] tryExternalDetectors(BitMatrix image, String reason) throws NotFoundException {
    java.util.ServiceLoader<ExternalRectangleDetector> loader =
        java.util.ServiceLoader.load(ExternalRectangleDetector.class);

    for (ExternalRectangleDetector det : loader) {
      try {
        System.out.println("Detector: trying external rectangle detector (" + det.getName() + ") because: " 
            + reason + "\n");
        ResultPoint[] pts = det.detect(image);
        if (pts != null && pts.length == 4) {
          return pts;
        }
      } catch (Throwable t) {
        System.out.println("Detector: external detector " + det.getName() + " failed: " + t.getMessage() + "\n");
      }
    }
    throw NotFoundException.getNotFoundInstance();
  }

  private static boolean badDimensions(int top, int right) {
    if (top < 8 || right < 8) {
      return true;
    }
    int max = Math.max(top, right);
    int min = Math.min(top, right);
    if (max > 3 * min) {
      return true;
    }
    return false;
  }

  /**
   * 
   * @param delta - how much to shift
   * @return - result
   * @throws NotFoundException - nfe
   */

  public DetectorResult detectWithPerturbation(int delta) throws NotFoundException {
    NotFoundException lastEx = null;

    // Try small ±delta shifts around each corner
    int[] shifts = { 0, -delta, delta };
    for (int dx : shifts) {
      for (int dy : shifts) {
        try {
          ResultPoint[] points = rectangleDetector.detect();

          // Apply perturbation to each corner
          for (ResultPoint p : points) {
            p = new ResultPoint(p.getX() + dx, p.getY() + dy);
          }

          // Then proceed same as detect()
          ResultPoint[] solid1 = detectSolid1(points);
          ResultPoint[] solid2 = detectSolid2(solid1);
          solid2[3] = correctTopRight(solid2);
          if (solid2[3] == null) {
            continue;
          }

          solid2 = shiftToModuleCenter(solid2);

          int dimensionTop = transitionsBetween(solid2[0], solid2[3]) + 1;
          int dimensionRight = transitionsBetween(solid2[2], solid2[3]) + 1;

          if ((dimensionTop & 1) == 1) {
            dimensionTop++;
          }
          if ((dimensionRight & 1) == 1) {
            dimensionRight++;
          }

          if (4 * dimensionTop < 6 * dimensionRight && 4 * dimensionRight < 6 * dimensionTop) {
            dimensionTop = dimensionRight = Math.max(dimensionTop, dimensionRight);
          }

          BitMatrix bits = sampleGrid(image, solid2[0], solid2[1], solid2[2], solid2[3], dimensionTop, dimensionRight,
              true);

          return new DetectorResult(bits, solid2);

        } catch (NotFoundException ex) {
          lastEx = NotFoundException.getNotFoundInstance();
          // continue looping
        }
      }
    }
    throw lastEx != null ? lastEx : NotFoundException.getNotFoundInstance();
  }

  private static ResultPoint shiftPoint(ResultPoint point, ResultPoint to, int div) {
    float x = (to.getX() - point.getX()) / (div + 1);
    float y = (to.getY() - point.getY()) / (div + 1);
    return new ResultPoint(point.getX() + x, point.getY() + y);
  }

  private static ResultPoint moveAway(ResultPoint point, float fromX, float fromY) {
    float x = point.getX();
    float y = point.getY();

    if (x < fromX) {
      x -= 1;
    } else {
      x += 1;
    }

    if (y < fromY) {
      y -= 1;
    } else {
      y += 1;
    }

    return new ResultPoint(x, y);
  }

  /**
   * Detect a solid side which has minimum transition.
   */
  private ResultPoint[] detectSolid1(ResultPoint[] cornerPoints) {
    // 0 2
    // 1 3
    ResultPoint pointA = cornerPoints[0];
    ResultPoint pointB = cornerPoints[1];
    ResultPoint pointC = cornerPoints[3];
    ResultPoint pointD = cornerPoints[2];

    int trAB = transitionsBetween(pointA, pointB);
    int trBC = transitionsBetween(pointB, pointC);
    int trCD = transitionsBetween(pointC, pointD);
    int trDA = transitionsBetween(pointD, pointA);

    // 0..3
    // : :
    // 1--2
    int min = trAB;
    ResultPoint[] points = { pointD, pointA, pointB, pointC };
    if (min > trBC) {
      min = trBC;
      points[0] = pointA;
      points[1] = pointB;
      points[2] = pointC;
      points[3] = pointD;
    }
    if (min > trCD) {
      min = trCD;
      points[0] = pointB;
      points[1] = pointC;
      points[2] = pointD;
      points[3] = pointA;
    }
    if (min > trDA) {
      points[0] = pointC;
      points[1] = pointD;
      points[2] = pointA;
      points[3] = pointB;
    }

    return points;
  }

  /**
   * Detect a second solid side next to first solid side.
   */
  private ResultPoint[] detectSolid2(ResultPoint[] points) {
    // A..D
    // : :
    // B--C
    ResultPoint pointA = points[0];
    ResultPoint pointB = points[1];
    ResultPoint pointC = points[2];
    ResultPoint pointD = points[3];

    // Transition detection on the edge is not stable.
    // To safely detect, shift the points to the module center.
    int tr = transitionsBetween(pointA, pointD);
    ResultPoint pointBs = shiftPoint(pointB, pointC, (tr + 1) * 4);
    ResultPoint pointCs = shiftPoint(pointC, pointB, (tr + 1) * 4);
    int trBA = transitionsBetween(pointBs, pointA);
    int trCD = transitionsBetween(pointCs, pointD);

    // 0..3
    // | :
    // 1--2
    if (trBA < trCD) {
      // solid sides: A-B-C
      points[0] = pointA;
      points[1] = pointB;
      points[2] = pointC;
      points[3] = pointD;
    } else {
      // solid sides: B-C-D
      points[0] = pointB;
      points[1] = pointC;
      points[2] = pointD;
      points[3] = pointA;
    }

    return points;
  }

  /**
   * Calculates the corner position of the white top right module.
   */
  private ResultPoint correctTopRight(ResultPoint[] points) {
    // A..D
    // | :
    // B--C
    ResultPoint pointA = points[0];
    ResultPoint pointB = points[1];
    ResultPoint pointC = points[2];
    ResultPoint pointD = points[3];

    // shift points for safe transition detection.
    int trTop = transitionsBetween(pointA, pointD);
    int trRight = transitionsBetween(pointB, pointD);
    ResultPoint pointAs = shiftPoint(pointA, pointB, (trRight + 1) * 4);
    ResultPoint pointCs = shiftPoint(pointC, pointB, (trTop + 1) * 4);

    trTop = transitionsBetween(pointAs, pointD);
    trRight = transitionsBetween(pointCs, pointD);

    ResultPoint candidate1 = new ResultPoint(pointD.getX() + (pointC.getX() - pointB.getX()) / (trTop + 1),
        pointD.getY() + (pointC.getY() - pointB.getY()) / (trTop + 1));
    ResultPoint candidate2 = new ResultPoint(pointD.getX() + (pointA.getX() - pointB.getX()) / (trRight + 1),
        pointD.getY() + (pointA.getY() - pointB.getY()) / (trRight + 1));

    if (!isValid(candidate1)) {
      if (isValid(candidate2)) {
        return candidate2;
      }
      return null;
    }
    if (!isValid(candidate2)) {
      return candidate1;
    }

    int sumc1 = transitionsBetween(pointAs, candidate1) + transitionsBetween(pointCs, candidate1);
    int sumc2 = transitionsBetween(pointAs, candidate2) + transitionsBetween(pointCs, candidate2);

    if (sumc1 > sumc2) {
      return candidate1;
    } else {
      return candidate2;
    }
  }

  /**
   * Shift the edge points to the module center.
   */
  private ResultPoint[] shiftToModuleCenter(ResultPoint[] points) {
    // A..D
    // | :
    // B--C
    ResultPoint pointA = points[0];
    ResultPoint pointB = points[1];
    ResultPoint pointC = points[2];
    ResultPoint pointD = points[3];

    // calculate pseudo dimensions
    int dimH = transitionsBetween(pointA, pointD) + 1;
    int dimV = transitionsBetween(pointC, pointD) + 1;

    // shift points for safe dimension detection
    ResultPoint pointAs = shiftPoint(pointA, pointB, dimV * 4);
    ResultPoint pointCs = shiftPoint(pointC, pointB, dimH * 4);

    // calculate more precise dimensions
    dimH = transitionsBetween(pointAs, pointD) + 1;
    dimV = transitionsBetween(pointCs, pointD) + 1;
    if ((dimH & 0x01) == 1) {
      dimH += 1;
    }
    if ((dimV & 0x01) == 1) {
      dimV += 1;
    }

    // WhiteRectangleDetector returns points inside of the rectangle.
    // I want points on the edges.
    float centerX = (pointA.getX() + pointB.getX() + pointC.getX() + pointD.getX()) / 4;
    float centerY = (pointA.getY() + pointB.getY() + pointC.getY() + pointD.getY()) / 4;
    pointA = moveAway(pointA, centerX, centerY);
    pointB = moveAway(pointB, centerX, centerY);
    pointC = moveAway(pointC, centerX, centerY);
    pointD = moveAway(pointD, centerX, centerY);

    ResultPoint pointBs;
    ResultPoint pointDs;

    // shift points to the center of each modules
    pointAs = shiftPoint(pointA, pointB, dimV * 4);
    pointAs = shiftPoint(pointAs, pointD, dimH * 4);
    pointBs = shiftPoint(pointB, pointA, dimV * 4);
    pointBs = shiftPoint(pointBs, pointC, dimH * 4);
    pointCs = shiftPoint(pointC, pointD, dimV * 4);
    pointCs = shiftPoint(pointCs, pointB, dimH * 4);
    pointDs = shiftPoint(pointD, pointC, dimV * 4);
    pointDs = shiftPoint(pointDs, pointA, dimH * 4);

    return new ResultPoint[] { pointAs, pointBs, pointCs, pointDs };
  }

  private boolean isValid(ResultPoint p) {
    return p.getX() >= 0 && p.getX() <= image.getWidth() - 1 && p.getY() > 0 && p.getY() <= image.getHeight() - 1;
  }

  private static BitMatrix sampleGridWithJiggle(BitMatrix image, ResultPoint topLeft, ResultPoint bottomLeft,
      ResultPoint bottomRight, ResultPoint topRight, int dimensionX, int dimensionY) throws NotFoundException {
    GridSampler sampler = GridSampler.getInstance();

    float[] offsets = { 0.25f, 0.5f, 0.75f };
    BitMatrix best = null;
    int bestScore = -1;

    for (float ox : offsets) {
      for (float oy : offsets) {
        try {
          BitMatrix candidate = sampler.sampleGrid(image, dimensionX, dimensionY, ox, oy, dimensionX - 1 + ox, oy,
              dimensionX - 1 + ox, dimensionY - 1 + oy, ox, dimensionY - 1 + oy, topLeft.getX(), topLeft.getY(),
              topRight.getX(), topRight.getY(), bottomRight.getX(), bottomRight.getY(), bottomLeft.getX(),
              bottomLeft.getY());

// crude quality score: count balance of black/white modules
          int blacks = 0;
          int total = dimensionX * dimensionY;
          for (int y = 0; y < dimensionY; y++) {
            for (int x = 0; x < dimensionX; x++) {
              if (candidate.get(x, y)) {
                blacks++;
              }
            }
          }
          int score = Math.min(blacks, total - blacks); // prefer balanced
          if (score > bestScore) {
            bestScore = score;
            best = candidate;
          }
        } catch (NotFoundException ignore) {
// try next offset
        }
      }
    }

    if (best == null) {
      throw NotFoundException.getNotFoundInstance();
    }
    return best;
  }

  private static BitMatrix sampleGrid(BitMatrix image, ResultPoint topLeft, ResultPoint bottomLeft,
      ResultPoint bottomRight, ResultPoint topRight, int dimensionX, int dimensionY, boolean jiggle)
      throws NotFoundException {

    GridSampler sampler = GridSampler.getInstance();
    BitMatrix base = sampler.sampleGrid(image, dimensionX, dimensionY, 0.5f, 0.5f, dimensionX - 0.5f, 0.5f,
        dimensionX - 0.5f, dimensionY - 0.5f, 0.5f, dimensionY - 0.5f, topLeft.getX(), topLeft.getY(), topRight.getX(),
        topRight.getY(), bottomRight.getX(), bottomRight.getY(), bottomLeft.getX(), bottomLeft.getY());

    if (!jiggle) {
      return base;
    }

// Oversample/jiggle mode: majority vote over a 3x3 neighborhood
    BitMatrix oversampled = new BitMatrix(dimensionX, dimensionY);
    for (int y = 0; y < dimensionY; y++) {
      for (int x = 0; x < dimensionX; x++) {
        int blackCount = 0;
        int total = 0;
        for (int dy = -1; dy <= 1; dy++) {
          for (int dx = -1; dx <= 1; dx++) {
            int nx = x + dx;
            int ny = y + dy;
            if (nx >= 0 && nx < dimensionX && ny >= 0 && ny < dimensionY) {
              total++;
              if (base.get(nx, ny)) {
                blackCount++;
              }
            }
          }
        }
        if (blackCount > total / 2) {
          oversampled.set(x, y);
        }
      }
    }
    return oversampled;
  }

  /**
   * Counts the number of black/white transitions between two points, using
   * something like Bresenham's algorithm.
   */
  private int transitionsBetween(ResultPoint from, ResultPoint to) {
    // See QR Code Detector, sizeOfBlackWhiteBlackRun()
    int fromX = (int) from.getX();
    int fromY = (int) from.getY();
    int toX = (int) to.getX();
    int toY = Math.min(image.getHeight() - 1, (int) to.getY());

    boolean steep = Math.abs(toY - fromY) > Math.abs(toX - fromX);
    if (steep) {
      int temp = fromX;
      fromX = fromY;
      fromY = temp;
      temp = toX;
      toX = toY;
      toY = temp;
    }

    int dx = Math.abs(toX - fromX);
    int dy = Math.abs(toY - fromY);
    int error = -dx / 2;
    int ystep = fromY < toY ? 1 : -1;
    int xstep = fromX < toX ? 1 : -1;
    int transitions = 0;
    boolean inBlack = image.get(steep ? fromY : fromX, steep ? fromX : fromY);
    for (int x = fromX, y = fromY; x != toX; x += xstep) {
      boolean isBlack = image.get(steep ? y : x, steep ? x : y);
      if (isBlack != inBlack) {
        transitions++;
        inBlack = isBlack;
      }
      error += dy;
      if (error > 0) {
        if (y == toY) {
          break;
        }
        y += ystep;
        error -= dx;
      }
    }
    return transitions;
  }

}
