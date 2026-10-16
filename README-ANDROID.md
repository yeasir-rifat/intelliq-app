# IntelliQ Android - Step 9 / Part 1 of 7 (website parity: data model)

Goal of this series: clone every feature of the updated website (update.zip) into the native app. See `README-NEXT-STEPS.md`.

**Part 1 changes only the data layer - the app looks and behaves exactly as before.** Nothing visible changes yet; the
screens come in Part 2 and 3.

- `data/Models.kt`: `QType` (mcq, creative, tf, fill, short, desc), `SubQuestion`, new `Question` fields
  (`type`, `marks`, `tfAnswer`, `answer`, `subs`, `cols`), per-type `isEmpty()` / `hasAnswer()` / `totalMarks()`,
  new `DocSettings` fields (columns per type, section mode / order / titles / instructions / layout / divider / line),
  helpers `parseMarks`, `formatMarks`, `subLabel`, `arrangeSections`, `colsOf`, `sectionTitle`.
- `data/Persist.kt`: saves and loads all new fields. Old saved papers and History entries load unchanged (every new
  field has a default, new keys are written only when used).
- `data/AppStore.kt`: operations `setType`, `setMarks`, `setTfAnswer`, `setAnswer`, `setCols`, `setSubText/Marks/Answer`,
  `addSub`, `deleteSub`, `setTypeCols`, `setAllCols`, `setSectionMode/Layout`, `moveSection`, `setSectionMeta/Divider/Line`,
  and `trailingType` (type of the empty last question).
- `data/Randomizer.kt`: only MCQ options are shuffled.
- The answer-key check (`hasAnswerKey`) now looks at every question type.

# IntelliQ Android - Step 8 (splash screen + speed)

- **Splash**: the app no longer opens on a white screen. The system splash shows the logo on the app background
  (`Theme.IntelliQ.Splash`, `core-splashscreen`), followed by a short logo + "IntelliQ" screen (`SplashScreenView`).
- **Faster start**: saved papers (History) load in the background; saving the document / History is encoded off the main thread.
- **Faster typing**: Compose strong skipping is on, so only the question card you edit is redrawn.
- **Faster app**: the workflow now builds the **release** APK (R8 shrinking, optimised code). It is signed with the debug key,
  so uninstall an older IntelliQ build once before installing it. Artifact name: `IntelliQ-apk`.

# IntelliQ Android (native) - Step 7 (toasts, premium icons, fonts)

## What is new in Step 7
- **Messages (toasts)** now appear at the **bottom** of the screen as a small pill with an icon (green check = success,
  red warning = error) and use very short text, e.g. "Question paper saved", "ZIP saved", "Saved", "PDF failed, try again".
  AI errors are shortened for the toast ("No internet", "AI unavailable now"); the AI dialog still shows the full text.
- **Premium icons**: all Material icons were replaced by Phosphor Icons (Duotone, Bold for tiny arrows/close/plus) in
  `ui/theme/PremiumIcons.kt`. The `material-icons-extended` dependency was removed.
- **App UI fonts**: English = Lato, Bangla = Noto Serif Bengali (real 400/500/600/700 weights from the variable font).
  The "IntelliQ" wordmark in the header keeps Poppins.
- **PDF / paper fonts** (preview and PDF are drawn by the same code), chosen per character:
  Bangla = Kalpurush, English = Times New Roman look (Tinos), fractions / sub- / superscripts / math symbols = Cambria look (Caladea).
  See `ui/PaperFonts.kt`. Settings → Font Family still lets you pick another font.
- `android/FONT-LICENSES.md` lists every font and the icon set with its licence.

## New / changed files
- new: `ui/PaperFonts.kt`, `ui/theme/PremiumIcons.kt`, `res/font/kalpurush.ttf`, `FONT-LICENSES.md`
- changed: `ui/theme/Fonts.kt`, `ui/Components.kt` (ToastHost), `ui/Paper.kt`, `ui/Fraction.kt`, `ui/I18n.kt`, `ui/Strings.kt`,
  `ui/AiViewModel.kt`, `ui/IntelliQApp.kt` and the other UI files (icons), `scripts/fetch_fonts.sh` (+Lato), `app/build.gradle.kts`

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
