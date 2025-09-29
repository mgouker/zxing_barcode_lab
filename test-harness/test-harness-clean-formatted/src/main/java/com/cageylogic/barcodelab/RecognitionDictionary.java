package com.cageylogic.barcodelab;

import java.util.ArrayList;


public class RecognitionDictionary {
  ArrayList<RecognitionEntry> entries = new ArrayList<>();

  public RecognitionEntry getEntry(String documentName) {
    for (RecognitionEntry entry : entries) {
      if (entry.getDocumentName().equals(documentName)) {
        return entry;
      }
    }
    return null;
  }

  public boolean addEntry(String documentName) {
    if (getEntry(documentName) != null) {
      return false;
    }
    RecognitionEntry entry = new RecognitionEntry(documentName);
    entries.add(entry);
    return true;
  }
}
