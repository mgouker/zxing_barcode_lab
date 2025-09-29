package com.cageylogic.barcodelab;

import java.util.ArrayList;


public class RecognitionEntry {
  String documentName = "";
  ArrayList<RecognitionResult> results = new ArrayList<>();

  public RecognitionEntry(String documentName) {
    this.documentName = documentName;
  }

  public String getDocumentName() {
    return documentName;
  }

  public void setDocumentName(String documentName) {
    this.documentName = documentName;
  }

  public void addResult(int codeType, String value) {
    RecognitionResult result = new RecognitionResult(codeType, value);
    results.add(result);
  }
}
