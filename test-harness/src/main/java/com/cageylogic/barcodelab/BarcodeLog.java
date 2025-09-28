package com.cageylogic.barcodelab;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * BarcodeLog - Lightweight multi-level logging utility.
 *
 * Provides console and file logging with bitmask-based level filtering.
 * Inspired by Python-style logging but adapted for Java best practices.
 */
public final class BarcodeLog {

  /** Info logging level. */
  public static final int INFO = 1;

  /** Execution flow logging level. */
  public static final int EXECUTION_FLOW = 2;

  /** Analysis/debug logging level. */
  public static final int ANALYSIS = 4;

  /** Warning logging level. */
  public static final int WARNING = 8;

  /** Error logging level. */
  public static final int ERROR = 16;

  /** Debug logging level. */
  public static final int DEBUG = 32;

  /** Enabled levels bitmask. */
  private static int enabledLevels = ERROR | WARNING | EXECUTION_FLOW;

  private final boolean toFile;
  private final boolean echoToConsole;
  private final File logFile;
  private PrintWriter writer;

  // --- Singleton instances ---
  private static BarcodeLog sharedLogger;
  private static BarcodeLog consoleLogger;
  private static BarcodeLog fileLogger;

  /**
   * Private constructor for logger instances.
   */
  private BarcodeLog(boolean toFile, boolean echoToConsole) {
    this.toFile = toFile;
    this.echoToConsole = echoToConsole;

    if (toFile) {
      this.logFile = defaultLogFile();
      try {
        this.writer = new PrintWriter(new BufferedWriter(new FileWriter(this.logFile, true)));
        writeInitialMessage();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to open log file: " + e.getMessage(), e);
      }
    } else {
      this.logFile = null;
    }
  }

  // === Public API ===

  /**
   * Get a shared logger (logs to file + console).
   * 
   * @return shared BarcodeLog instance
   */
  public static synchronized BarcodeLog getSharedLogger() {
    if (sharedLogger == null) {
      sharedLogger = getFileLogger();
    }
    return sharedLogger;
  }

  /**
   * Get a console-only logger.
   * 
   * @return BarcodeLog that logs to console
   */
  public static synchronized BarcodeLog getConsoleLogger() {
    if (consoleLogger == null) {
      consoleLogger = new BarcodeLog(false, true);
    }
    return consoleLogger;
  }

  /**
   * Get a file logger (also echoes to console).
   * 
   * @return BarcodeLog that logs to file + console
   */
  public static synchronized BarcodeLog getFileLogger() {
    if (fileLogger == null) {
      fileLogger = new BarcodeLog(true, true);
    }
    return fileLogger;
  }

  /**
   * Log a message with optional source info.
   *
   * @param level    log level bitmask
   * @param message  message text
   * @param file     file name (optional)
   * @param function function name (optional)
   * @param line     line number (optional, pass -1 if unknown)
   */
  public void log(int level, String message, String file, String function, int line) {
    if ((level & enabledLevels) == 0) {
      return; // not enabled
    }

    String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    Thread t = Thread.currentThread();
    String threadDesc = t.getName() + " (#" + t.hashCode() + ")";

    String sourceInfo = (file != null && function != null && line > 0)
        ? String.format("[%s:%d %s]", file, line, function)
        : "";

    String formatted = String.format("[%s] [%s] %s %s", ts, threadDesc, sourceInfo, message);

    if (toFile && writer != null) {
      writer.println(formatted);
      writer.flush();
    }

    if (!toFile || echoToConsole) {
      System.out.println(formatted);
    }
  }

  // === Helpers ===

  private void writeInitialMessage() {
    String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
    String msg = String.format("[%s] Log file created: %s", ts, logFile.getAbsolutePath());
    writer.println(msg);
    writer.flush();
    if (echoToConsole) {
      System.out.println(msg);
    }
  }

  private File defaultLogFile() {
    String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
    File dir = new File("logs");
    if (!dir.exists()) {
      dir.mkdirs();
    }
    return new File(dir, "barcode_log_" + ts + ".log");
  }

  // === Control enabled levels ===

  /**
   * Enable a specific log level.
   * 
   * @param level log level to enable
   */
  public static void enableLevel(int level) {
    enabledLevels |= level;
  }

  /**
   * Disable a specific log level.
   * 
   * @param level log level to disable
   */
  public static void disableLevel(int level) {
    enabledLevels &= ~level;
  }

  public static void useConsoleOnly() {
    sharedLogger = getConsoleLogger();
  }

  public static void useFileLogger() {
    sharedLogger = getFileLogger(); // default path, echoes to console
  }

  public static void debug(String tag, String fmt, Object... args) {
    getSharedLogger().log(DEBUG, String.format("[%s] %s", tag, String.format(fmt, args)), null, null, -1);
  }

  public static void info(String tag, String fmt, Object... args) {
    getSharedLogger().log(INFO, String.format("[%s] %s", tag, String.format(fmt, args)), null, null, -1);
  }

  public static void warn(String tag, String fmt, Object... args) {
    getSharedLogger().log(WARNING, String.format("[%s] %s", tag, String.format(fmt, args)), null, null, -1);
  }

  public static void error(String tag, String fmt, Object... args) {
    getSharedLogger().log(ERROR, String.format("[%s] %s", tag, String.format(fmt, args)), null, null, -1);
  }

  @SuppressWarnings("unused")
  private static void log(int level, String message) {
    // forward to the instance method so we can use instance fields safely
    getSharedLogger().log(level, message, null, null, -1);
  }

}
