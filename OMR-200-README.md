# 200-answer OMR sheet (geometry version 2)

- 5 blocks x 40 answers (A4), header grids: subject/exam code (2 digits), centre (1), roll (8), set; name, district,
  candidate signature, invigilator boxes, QR, two "do not mark" boxes. No third-party branding.
- Scanner furniture: 4 black squares (8.0/197.2 x 88.0/283.4 mm) + 40 timing dashes between the two left squares.
- All numbers live in data/OmrGeometry.kt (VERSION = 2). Old 50-answer sheets are reported as "other version".
- Build/tests could not be run in the authoring sandbox: run `gradle :app:testDebugUnitTest` once on GitHub Actions
  and send me the log if anything fails.

## Sheet sizes: 50 / 100 / 200 answers (selectable)

- Settings > OMR sheet > "Questions per sheet": 50, 100 or 200 (works for the automatic and the custom sheet; saved in `OmrSettings.sheetKind`, default 200).
- 200 = 5 blocks x 40 (unchanged, geometry version 2). 100 = 4 blocks x 25 (version 3). 50 = 2 blocks x 25 (version 4).
  Blocks of 50/100 are centred; all three keep the same answer-table height, corner squares, timing dashes, QR box and header,
  so the aligner is untouched. Row pitch is 6.976 mm for 50/100 (4.36 mm for 200).
- The QR code carries the version, so the scanner knows the size of every printed sheet by itself (the exam's setting is only
  the fallback when the QR code cannot be read). Answer keys store the same version; a key of another size is not used to
  grade a sheet (the sheet is flagged "other version").
- Code: `OmrSheetKind` + `rowPitch / blockX / firstBubbleX / ansY / answerBubble(kind, ...)` in data/OmrGeometry.kt.
  Old no-kind functions still mean the 200 sheet.
- New tests: `OmrSheetKindTest`. Not run in the authoring sandbox either: run `gradle :app:testDebugUnitTest` on GitHub Actions.

## Improvements, round 2 (items 1 to 5 and 8 done; 6 and 7 are proposals waiting for approval)

No geometry number was changed in this round, so the geometry versions stay 2 (200), 3 (100) and 4 (50). The corner squares, timing
dashes and QR box did not move.

1. **Extra rows of the last sheet are pale.** The rows after the last MCQ are still numbered on (`omrRows`), but their number and bubbles are
   drawn at 28 % opacity (`UNUSED_ALPHA` in `ui/OmrSheet.kt`). `omrLiveRows(mcqCount, sheetRows, page)` in `data/AnswerKey.kt` says how many
   rows of a page are real; `OmrPage` got a `liveRows` parameter, `SheetPages` passes it. The scanner is untouched (`itemAt` is still null there).
2. **Own message when the sheet size and the answer key size differ.** New `OmrFlag.KEY_SIZE_MISMATCH`. `sizeMismatchOf(qr, snapshot)` in
   `data/OmrGrade.kt` gives both sizes; `OmrEvalViewModel.evaluate` stores them in `EvalSheet.sizeMismatch`; `evalFlagText` (`ui/OmrMessages.kt`)
   fills `{sheet}` and `{key}` in the language's digits. A key of an unknown old version (for example version 1) still gives `QR_OTHER_VERSION`.
   Flag texts exist only in `I18n.kt` and on the result screen (the report PDF and the CSV only carry a "needs review" mark, no flag text).
3. **Quick size chips (50 / 100 / 200)** in the toolbar of the OMR preview (`ui/OmrSizeBar.kt`, used by `PreviewPanel`). They write
   `settings.omr.sheetKind`, so the preview and its page count follow at once. The selector in Settings stays.
4. **Size hint** under the toolbar of the OMR preview, for example "45 MCQs: a 50-question sheet is enough" (`OmrSheetKind.suggest`,
   `omrSizeHint`). It only shows text and never changes the size. No MCQ: nothing is shown.
5. **Size printed in the sheet header**: "৫০ প্রশ্ন" / "50 Questions" at the top-left (x 15 mm, y 8.25 mm, 7.5 pt), clear of the title lines
   (they start at x 60), the grids (y 29 and below) and the scanner marks. New setting `OmrSettings.showSizeTag` (default on, saved in
   `Persist.kt`), switchable in custom mode only; the automatic sheet always prints it.
8. **Old tests fixed**: QR texts that assumed geometry version 1 now use version 2 (`QrPayloadTest`, `KeyQrPayloadTest`, `AnswerKeyImportTest`,
   `OmrReaderTest`, `OmrGradeTest`). `KeyQrPayloadTest` expected 5 sheets for 220 answers (the old 50-row idea); it is 2 sheets of 200 now, and a
   new test checks 50 / 100 / 200.

New tests: `OmrSheetKindTest` (live rows, size hint, size mismatch, size tag setting) and `ui/OmrMessagesTest` (texts in both languages).

Not done yet: item 6 (wider blocks for 50 and 100) and item 7 (bigger bubbles for 50 and 100). They change geometry, so they wait for approval of the numbers.

Build and tests were NOT run in the authoring sandbox (no Gradle / Android SDK). Run `gradle :app:testDebugUnitTest` and a debug build on GitHub Actions.
