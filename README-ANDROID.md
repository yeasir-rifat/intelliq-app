# IntelliQ Android (native) - Step 6 (PDF download, History, Folders)

Replace the `android/` and `.github/` folders in your repository root with these, push to `main`,
then download the APK from GitHub -> Actions -> "Build Android APK" -> Artifacts.

## What is new in Step 6
- **PDF download**: Preview toolbar -> download button. With an answer key it opens "Question Paper Only" /
  "Answer Key Only"; without one it downloads the question paper directly (same as the website).
  With several generated sets you get one ZIP (`..._All_Sets_Question.zip`) with a PDF per set.
  Files go to `Downloads/IntelliQ` (Android 10+). On Android 8/9 the share sheet opens instead.
  The PDF is drawn by the same composable as the preview (`PaperPages` print mode), so Bangla fonts, fractions,
  sub/superscripts, set-code box and "Page 1/3" numbers match the preview. Text stays real vector text.
- **History** (header button): saved papers, grouped by folder, with Download (question / answer), Edit (loads the
  paper back into the editor) and Delete. Max 200 entries, same dedupe rule as the website (title + subtitle + folder).
  A paper is recorded on Save and on every download. The Gemini API key is never stored inside history.
- **Folders**: the Folder / Group field opens a menu (Ungrouped, your folders, New folder). Folders can be renamed
  from the History screen. `AppData.folderId` is saved with the document.

## New / changed files
- data: `History.kt` (new), `Models.kt` (+folderId, +hasAnswerKey), `Persist.kt`, `AppStore.kt` (+setFolder, +loadSnapshot)
- ui: `PdfExport.kt` (new), `HistoryDialog.kt` (new), `Paper.kt` (print mode), `IntelliQApp.kt`, `I18n.kt` (new messages, EN + BN)
- `res/xml/file_paths.xml` (exports cache path for the Android 8/9 share fallback)

## Not done yet: Google sign-in + cloud sync
Needs your Firebase project on the Android side (`google-services.json` with this app's SHA-1 registered, Google
Sign-In + Firestore dependencies). The website already uses Firestore: `users/{uid}`, `users/{uid}/papers/{id}`,
`users/{uid}/folders/{id}`; the app should use the same paths so both stay in sync.

## What is new in Step 5
- **AI Mode** button (top of the Input tab) opens the same dialog as the website: Capture (camera) / Upload
  (images, PDF, DOCX, PPTX, up to 10 files), "Custom Prompt" switch (topic only, no file), instruction box,
  live status with upload percentage, Cancel / Generate MCQs.
- Same Gemini flow as the website: same two prompts, `generateContent` with JSON output, model fallback
  (gemini-3-flash-preview, 3.1-flash-lite, 2.5-flash-lite, 2.0-flash), "Append / Replace" from Settings,
  automatic switch to Bangla/English document language and font when the questions are in the other language.
- **Math and chemistry**: Gemini is told to write with the editor's own tools: `<sub>`, `<sup>`, stacked
  fractions `{{frac|n|d}}`, math symbols, chemical formulas (H2SO4 with subscripts, Fe3+ and SO4 2- with
  superscripts, reactions with arrows). `data/AiMarkup.kt` converts that into the app's rich text, so the
  questions look exactly like ones typed with the toolbar. LaTeX, `x^2`, `H_2O` and `**bold**` are converted
  too if a model writes them anyway. With "Chemistry auto-format" on (Settings), plain formulas like H2O are
  subscripted as well.
- The Gemini request keeps running if you close the dialog or rotate the phone; a toast reports the result.

## New / changed files
- data: `GeminiPrompts.kt`, `GeminiClient.kt`, `AiMarkup.kt`, `AiFiles.kt`, `ChemFormat.kt` (+`subscriptFormulaWord`),
  `AppStore.kt` (+`applyAiQuestions`, process-wide `AppStore.get`)
- ui: `AiModal.kt`, `AiViewModel.kt`, `IntelliQApp.kt` (AI Mode wiring), `I18n.kt` (new messages, EN + BN)
- `AndroidManifest.xml` (FileProvider for the camera), `res/xml/file_paths.xml`, `app/build.gradle.kts` (2 small dependencies)
