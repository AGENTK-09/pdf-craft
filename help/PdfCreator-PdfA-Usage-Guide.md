# PdfCreator — PDF/A Usage Guide

**Apache PDFBox 3 · Java 17 · ISO 19005-1**

---

## Overview

PDF/A is an ISO archival standard that guarantees a document can be rendered identically in any future PDF viewer. PdfCreator supports two PDF/A functions:

- **Generate** — produce fully compliant PDF/A-1b documents from templates
- **Validate** — check any existing PDF file against PDF/A-1b or PDF/A-1a rules

Both functions are available from the command line and integrate transparently with merge, split, signing, and batch operations.

---

## 1. Generating PDF/A-1b Compliant Documents

### How it works

Add `--pdfa` to any template or batch command. When active, the pipeline:

1. Loads **Liberation TrueType fonts** (Apache 2.0 licence, metrics-compatible with Helvetica/Times/Courier) and embeds them fully in the PDF
2. Attaches **XMP metadata** with the required `pdfaid:part=1` and `pdfaid:conformance=B` identification schema
3. Embeds an **sRGB ICC output intent** (IEC 61966-2.1) covering all RGB colour spaces used in the document

Without `--pdfa`, the tool uses Standard 14 fonts (never embedded) and writes no XMP — which produces smaller, faster files but fails PDF/A validation.

---

### 1.1 Template mode

```bash
java -jar pdf-creator.jar \
  --template-id bank-statement \
  --data-file data/statements/statement-001.json \
  --output output/statement-001.pdf \
  --pdfa
```

**Console output:**
```
Template : bank-statement — Monthly Account Statement
Data     : JsonFile[data/statements/statement-001.json]
Config   : default
Output   : output/statement-001.pdf
Sections : 8
PDF/A-1b : compliance markers attached
Pages    : 3 → output/statement-001.pdf
```

---

### 1.2 Batch mode

```bash
java -jar pdf-creator.jar \
  --batch \
  --template-id bank-statement \
  --data-dir data/statements/ \
  --output-dir output/statements/ \
  --threads 8 \
  --pdfa
```

The `--pdfa` flag applies to every job in the batch. Each generated PDF gets embedded fonts, XMP, and an output intent.

```bash
# Batch from CSV
java -jar pdf-creator.jar \
  --batch \
  --template-id credit-card-summary \
  --csv-file data/march-accounts.csv \
  --output-dir output/march/ \
  --threads 4 \
  --pdfa
```

---

### 1.3 Batch + sign in one pass

```bash
java -jar pdf-creator.jar \
  --batch \
  --template-id bank-statement \
  --data-dir data/statements/ \
  --output-dir output/signed/ \
  --threads 4 \
  --pdfa \
  --sign \
  --keystore bank.p12 \
  --keystore-password secret \
  --reason "Monthly Account Statement" \
  --tsa-url http://timestamp.digicert.com
```

Each PDF is generated as PDF/A-1b, then digitally signed in the same pipeline run. The unsigned intermediate file is never written to disk.

> **Note:** PDF/A-1b uses the `adbe.pkcs7.detached` signature subfilter, which is fully supported by PdfCreator's signing engine. The digital signature is appended as an incremental revision and does not modify the original content byte ranges.

---

## 2. Validating PDF Files

### How it works

`--validate` uses the **PDFBox Preflight** engine, which performs two independent checks:

1. **Syntax validation** — structural integrity checks during parsing (xref table, stream lengths, header bytes). If the file is too malformed to parse, a syntax error is reported immediately.
2. **Semantic validation** — PDF/A rule checks on the parsed document (fonts, metadata, colour spaces, encryption, actions, annotations).

---

### 2.1 Basic validation (PDF/A-1b, default)

```bash
java -jar pdf-creator.jar \
  --validate \
  --input statement.pdf
```

**Console output — valid document:**
```
Mode     : validate
Input    : statement.pdf
Standard : PDF/A-1b (Basic)
Max errs : unlimited

============================================================
  PDF/A Validation: ✓ PASSED
============================================================
  File     : statement.pdf
  Standard : PDF/A-1b (Basic)
  Pages    : 3
  Duration : 0.84 s

  No conformance issues found.
============================================================
```

**Console output — invalid document:**
```
============================================================
  PDF/A Validation: ✗ FAILED
============================================================
  File     : statement.pdf
  Standard : PDF/A-1b (Basic)
  Pages    : 3
  Duration : 0.61 s

  Issues found: 7

  By category:
    Fonts:                    4
    Metadata / XMP:           2
    Graphics / Colour:        1

  Detail:
    [3.1.3] Fonts [page 1] — font Helvetica is not embedded
    [3.1.3] Fonts [page 1] — font Helvetica-Bold is not embedded
    [3.1.3] Fonts [page 2] — font Helvetica is not embedded
    [3.1.3] Fonts [page 2] — font Times-Roman is not embedded
    [7.11]  Metadata / XMP — PDF/A identification schema missing
    [7.1]   Metadata / XMP — XMP metadata stream is absent
    [2.4]   Graphics / Colour — missing OutputIntent for DeviceRGB
============================================================
```

---

### 2.2 Validate against PDF/A-1a

```bash
java -jar pdf-creator.jar \
  --validate \
  --input report.pdf \
  --standard pdf-a-1a
```

> **Note:** PDFBox Preflight does not fully enforce the accessibility-specific checks of PDF/A-1a (structure tree, alt text, reading order) beyond what level B already covers. For strict PDF/A-1a validation, use VeraPDF.

---

### 2.3 Limit the number of errors reported

Useful for large documents with many violations — prevents flooding the terminal.

```bash
java -jar pdf-creator.jar \
  --validate \
  --input large-report.pdf \
  --max-errors 20
```

When truncated, the summary indicates `(truncated — more errors may exist)`.

---

### 2.4 Validate a password-protected PDF

```bash
java -jar pdf-creator.jar \
  --validate \
  --input encrypted.pdf \
  --password userpass
```

> **Important:** Encryption is **forbidden by PDF/A-1b** (ISO 19005-1 §6.1.3). An encrypted PDF will always fail validation regardless of any other properties — Preflight reports this as error code `8.1` or a syntax parse failure.

---

### 2.5 Shell scripting with exit codes

The validator exits with a standard code, making it easy to use in CI/CD pipelines or shell scripts:

| Exit code | Meaning |
|---|---|
| `0` | Document is valid — no conformance issues |
| `1` | Document is invalid — one or more issues found |
| `2` | Error — file not found, unreadable, or unknown `--standard` value |

```bash
# CI/CD example — fail the build if the PDF is not PDF/A compliant
java -jar pdf-creator.jar --validate --input output/statement.pdf
if [ $? -ne 0 ]; then
  echo "PDF/A validation failed — aborting release"
  exit 1
fi
```

```bash
# Validate all PDFs in a directory
for f in output/*.pdf; do
  echo "Checking $f..."
  java -jar pdf-creator.jar --validate --input "$f" --max-errors 5
done
```

---

## 3. PDF/A Compliance and Other Operations

### 3.1 Merge — compliance is preserved automatically

When merging PDFs where at least one input is PDF/A, the merged output automatically receives PDF/A compliance markers.

```bash
java -jar pdf-creator.jar \
  --merge \
  --input cover.pdf body.pdf appendix.pdf \
  --output merged.pdf
```

If `body.pdf` was PDF/A-1b, `merged.pdf` will also carry PDF/A-1b XMP identification and an sRGB output intent. You can then validate the merged result:

```bash
java -jar pdf-creator.jar --validate --input merged.pdf
```

---

### 3.2 Split — compliance is preserved automatically

Each part produced by `--split` inherits PDF/A markers from the source document.

```bash
# Split a PDF/A document every 5 pages
java -jar pdf-creator.jar \
  --split \
  --input annual-report-pdfa.pdf \
  --output-dir parts/ \
  --split-mode every-n \
  --pages-per-part 5

# Validate any part
java -jar pdf-creator.jar --validate --input parts/part-001.pdf
```

---

### 3.3 Encryption — incompatible with PDF/A

Attempting to encrypt a PDF/A document prints a warning:

```
Warning: statement.pdf is a PDF/A document.
Encryption is forbidden by ISO 19005-1 — the output will NOT be PDF/A compliant.
```

The encryption proceeds (the tool does not block it), but the resulting file will fail PDF/A validation with error `8.1`. If you need both encryption and archival compliance, use PDF/A-3 (requires VeraPDF for validation) or distribute unencrypted PDF/A files with separate access controls at the file-system or portal level.

---

### 3.4 Signing — compatible with PDF/A-1b

Digital signatures are compatible with PDF/A-1b. PdfCreator uses the `adbe.pkcs7.detached` subfilter, which is the approved subfilter for PDF/A-1 signatures.

```bash
# Generate PDF/A, then validate signature integrity
java -jar pdf-creator.jar \
  --sign \
  --input statement-pdfa.pdf \
  --output statement-pdfa-signed.pdf \
  --keystore bank.p12 \
  --keystore-password secret

java -jar pdf-creator.jar --validate --input statement-pdfa-signed.pdf
```

> After signing, Preflight may still report some issues because the XMP metadata embedded during generation is in the pre-signature revision. The signature itself is valid and the document is considered conformant by most validators when the signature does not modify the content byte ranges.

---

## 4. Error Code Reference

| Code | Category | Common cause |
|---|---|---|
| `1.x` | Syntax | Malformed PDF structure, corrupt xref table, bad stream length |
| `2.x` | Graphics / Colour | DeviceRGB/CMYK used without an output intent ICC profile |
| `3.1.3` | Fonts | Non-embedded font (most common failure in standard PDFBox output) |
| `3.1.x` | Fonts | Missing FontDescriptor, missing ToUnicode map |
| `4.x` | Transparency | SMask, blend modes, or alpha compositing (forbidden in PDF/A-1) |
| `5.x` | Annotations | Forbidden annotation types (e.g. FileAttachment, Movie) |
| `6.x` | Actions | JavaScript, GoToRemote, Launch, or URI actions |
| `7.1` | Metadata / XMP | XMP metadata stream absent or structurally invalid |
| `7.11` | Metadata / XMP | Missing `pdfaid:part` / `pdfaid:conformance` identification schema |
| `8.1` | Document Structure | Encryption present (always forbidden by PDF/A-1b) |
| `8.x` | Document Structure | Optional content (layers) present |

---

## 5. Flag Reference

### `--validate` flags

| Flag | Required | Default | Description |
|---|---|---|---|
| `--validate` | Yes | — | Activates validate mode |
| `--input <path>` | Yes | — | PDF file to validate |
| `--standard <name>` | No | `pdf-a-1b` | Conformance level: `pdf-a-1b` or `pdf-a-1a` |
| `--max-errors <n>` | No | `0` (unlimited) | Stop collecting after N errors |
| `--password <pwd>` | No | — | Password for encrypted input PDFs |

### `--pdfa` flag (generation)

| Flag | Works with | Description |
|---|---|---|
| `--pdfa` | `--template-id`, `--batch` | Enables PDF/A-1b compliant generation: embedded Liberation fonts, XMP pdfaid metadata, sRGB output intent |

---

## 6. Quick Reference Card

```
# Simplest validation
java -jar pdf-creator.jar --validate --input file.pdf

# Generate PDF/A compliant
java -jar pdf-creator.jar --template-id <id> --data-file <json> --output <pdf> --pdfa

# Validate with error cap
java -jar pdf-creator.jar --validate --input file.pdf --max-errors 25

# Validate PDF/A-1a
java -jar pdf-creator.jar --validate --input file.pdf --standard pdf-a-1a

# Validate encrypted PDF (will always fail — encryption forbidden)
java -jar pdf-creator.jar --validate --input file.pdf --password <pwd>

# Generate PDF/A + sign in one batch pass
java -jar pdf-creator.jar --batch --template-id <id> --data-dir <dir> \
  --output-dir <out> --threads 4 --pdfa \
  --sign --keystore <p12> --keystore-password <pwd>

# Merge and validate result
java -jar pdf-creator.jar --merge --input a.pdf b.pdf --output merged.pdf
java -jar pdf-creator.jar --validate --input merged.pdf

# Shell: use exit code in CI
java -jar pdf-creator.jar --validate --input output.pdf && echo "PASS" || echo "FAIL"
```
