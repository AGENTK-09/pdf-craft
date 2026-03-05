#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# run-tests.sh  —  Build and run all PdfCreator integration tests
#
# Usage:
#   chmod +x run-tests.sh
#   ./run-tests.sh
#
# Prerequisites:
#   - Maven installed (mvn on PATH)
#   - Java 17+
#   - Run from the project root (where pom.xml lives)
# ─────────────────────────────────────────────────────────────────
set -e

echo "Building project..."
mvn package -q -DskipTests

echo "Running tests..."
java -cp target/pdf-creator-1.0-SNAPSHOT.jar com.pdfcreator.PdfCreatorTest

echo ""
echo "Output PDFs written to: test-output/"
ls -lh test-output/ 2>/dev/null | grep ".pdf" || true
