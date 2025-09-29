package com.cageylogic.barcodelab;

public class RecognitionResult {

  int codeType = 0;
  String value = "";

  public RecognitionResult(int codeType, String value) {
    this.codeType = codeType;
    this.value = value;
  }
}
