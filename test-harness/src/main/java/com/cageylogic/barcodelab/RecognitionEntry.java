package com.cageylogic.barcodelab;

/** Represents a single recognition entry (one document). */
public class RecognitionEntry {

  private static int counter = 0;

  private final int id;
  private final String documentName;

  public RecognitionEntry(String documentName) {
    this.id = ++counter;
    this.documentName = documentName;
  }

  public int getId() {
    return id;
  }

  public String getDocumentName() {
    return documentName;
  }
}
