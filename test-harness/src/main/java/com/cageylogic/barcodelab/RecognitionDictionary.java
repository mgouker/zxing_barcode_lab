package com.cageylogic.barcodelab;

import java.util.ArrayList;
import java.util.List;

/** Dictionary of recognition entries. */
public class RecognitionDictionary {

  private final List<RecognitionEntry> entries = new ArrayList<>();

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

  public List<RecognitionEntry> getEntries() {
    return entries;
  }
}
