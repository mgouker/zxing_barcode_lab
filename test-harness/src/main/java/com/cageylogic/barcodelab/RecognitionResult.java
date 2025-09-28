package com.cageylogic.barcodelab;

/** Represents the result of a barcode recognition attempt. */
public class RecognitionResult {

  private final String documentName;
  private final String text;
  private final String format;
  private final boolean success;
  private final String message;

  public RecognitionResult(String documentName, String text, String format, boolean success, String message) {
    this.documentName = documentName;
    this.text = text;
    this.format = format;
    this.success = success;
    this.message = message;
  }

  public String getDocumentName() {
    return documentName;
  }

  public String getText() {
    return text;
  }

  public String getFormat() {
    return format;
  }

  public boolean isSuccess() {
    return success;
  }

  public String getMessage() {
    return message;
  }
}
