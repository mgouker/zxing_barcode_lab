# ZXing Test Harness

This module provides a standalone harness for testing and comparing ZXing’s decoding of common barcodes.  
It was adapted from earlier OmniPage-based recognition code and refactored to run directly against ZXing’s `core` and `javase` modules.

## Supported Barcode Formats
- Code 128 (1D)
- Data Matrix (2D)

The framework is extensible — additional formats can be added to `RecognitionEngine`.

## Maven Configuration

`test-harness/pom.xml` includes:
- Dependencies on ZXing `core` and `javase`
- JUnit 5 for optional unit testing
- `maven-surefire-plugin` for test execution
- `spotless-maven-plugin` to enforce formatting (AOSP/2-space style)

Formatting can be applied via:

```bash
mvn spotless:apply
```

Checkstyle validation can be run via:

```bash
mvn checkstyle:check
```

## Project Structure
- `DocumentRecognition` – main entry point; scans a specified directory of images and attempts to decode each file.  
- `RecognitionEngine` – wraps ZXing decoders and controls which formats are attempted.  
- `RecognitionEngineUtils` – helpers for image I/O and setup.  
- `RecognitionResult`, `RecognitionEntry`, `RecognitionDictionary` – data model for recording outcomes.  
- `SortReportsForAllBarcodeTypes` – organizes CSV results into per-format folders (optional).

## Running the Harness

From the `test-harness` directory:

```bash
mvn compile
mvn exec:java -Dexec.mainClass="com.cageylogic.barcodelab.DocumentRecognition" -Dexec.args="path/to/images"
```

Where `path/to/images` is any directory containing barcode image files (`.png`, `.jpg`, `.tif`, `.pdf` if supported).

The harness will:
1. Walk the directory recursively
2. Attempt to decode each file using supported ZXing formats
3. Print results to the console
4. Optionally export structured results for later analysis

## Notes
- Code formatting follows ZXing’s Checkstyle rules (2-space indentation, 120 line length).  
- Public classes and methods include Javadoc for clarity and portfolio readability.  
- This harness is designed for experimentation and debugging — decoding results depend on input image quality and supported formats.
