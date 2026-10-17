# REVIEW-PROGRESS

## ধাপ ০ (আংশিক) — বিল্ড
- পরিবেশ: Android SDK নেই, Maven/Google ব্লকড => gradle build অসম্ভব (অযাচাইকৃত: Compose/Android-নির্ভর ফাইল)।
- বিকল্প: Gradle-এর ভেতরের Kotlin 2.0.21 কম্পাইলার + org.json/android/zxing স্টাব (/home/claude/work/scratch) দিয়ে বিশুদ্ধ data-লেয়ার + ১০টি টেস্ট ক্লাস কম্পাইল হয়েছে।
- ফল: 100 টেস্ট, 89 পাস, 11 ফেল — সবকটি শুধু zxing স্টাবের কারণে (QrCode.encode / decode), কোড বাগ নয়।
- বাকি: Compose ফাইলের স্ট্যাটিক চেক (KeyPdfLoader, OmrEvalViewModel imports, GroupAccent, ToastHost), তারপর ধাপ ১।

## ধাপ ১ — ডেটা/যুক্তি (শেষ)
- [উচ্চ] OmrReport/OmrResultPdf — ৫০+ MCQ (একাধিক OMR শিট) হলে প্রতিটি শিট আলাদা র‍্যাঙ্ক হতো এবং একই roll দুই পেজে থাকায় "duplicate roll" ভুলভাবে দেখাত। ঠিক: OmrReport.mergePages (roll+set অনুযায়ী যোগ, পেজ কম থাকলে review)। টেস্ট যোগ, পাস।
- [মধ্যম] OmrEvalViewModel.add/addKeyImages/addKeyPdf — একটি ছবিতে OOM/অপ্রত্যাশিত exception হলে অ্যাপ ক্র্যাশ, pending/keyBusy আটকে থাকত, ক্যামেরা temp ফাইল মুছত না। ঠিক: try/catch(Throwable)+finally। (অযাচাইকৃত: কম্পাইল করা যায়নি)
- [নিম্ন] KeyPdfLoader.scanPage — এক পেজ রেন্ডার ব্যর্থ হলে পুরো PDF বাতিল; এখন শুধু সেই পেজ None।
- [নিম্ন] AnswerKey.decode — ডুপ্লিকেট printedNumber গ্রহণ করত (itemAt সারি সরে যায়); এখন প্রত্যাখ্যান। টেস্ট (ReviewEdgeCasesTest) পাস।
- টেস্ট: 106 চলল, 95 পাস, 11 ফেল সবই zxing স্টাব।
- বাকি: ধাপ ২–৭।

## ধাপ ২ — সংরক্ষণ/সিংক (শেষ)
- [উচ্চ] CloudSync.onSignedOut: সাইন-আউটের পর স্থানীয় কাগজ মুছে যায়, কিন্তু `migrated.<uid>` ফ্ল্যাগ থেকে যেত; আবার সাইন-ইন করলে অফলাইনে বানানো কাগজ আপলোড হতো না এবং ক্লাউড তালিকা দিয়ে মুছে যেত। ঠিক: সাইন-আউটে সব migrated.* ফ্ল্যাগ মোছা।
- [মধ্যম] CloudSync appPapers: ৯০০KB-এর বেশি কাগজ "এই ডিভাইসে থাকবে" বলা হলেও applyRemote ক্লাউড তালিকা দিয়ে স্থানীয় তালিকা বদলে মুছে দিত। ঠিক: ক্লাউডে নেই + আপলোডের অযোগ্য কাগজ রেখে দেওয়া।
- [মধ্যম] HistoryStore: main ও io থ্রেড একসাথে entries বদলাত (lost update)। ঠিক: মিউটেটরে @Synchronized।
- [নিম্ন] AppStore.load: সেভ-করা ডেটা পড়া না গেলে খালি কাগজ দিয়ে পরের সেভে মুছে যেত; এখন কাঁচা টেক্সট `data.unreadable` কী-তে একবার রাখা হয়।
- [নিম্ন/অ-ঠিক] setIndexOf: সেটের নাম ডুপ্লিকেট (A, A) হলে দুই সেট একই বাবল/স্ন্যাপশট কী পায়। ঠিক করা হয়নি (UI-তে ইউনিক নাম বাধ্য করা দরকার)।
- [মধ্যম/অ-ঠিক] allowBackup=true: Gemini API key ও কাগজ Google Backup-এ যায়। নকশাগত সিদ্ধান্ত, ধাপ ৬ দেখুন।
- সবই অযাচাইকৃত (Android নির্ভর, কম্পাইল করা যায়নি)।

## ধাপ ৩ (আংশিক) / ৫ (শুরু)
- PdfExport/SheetPages/KeySheet পড়া হয়েছে: স্পষ্ট বাগ পাইনি (KeySheet পেজ ১১০/১১৫ হিসাব ঠিক)। অযাচাইকৃত: ছাপা/ফন্ট/মেমরি।
- ধাপ ৫(চ) i18n স্ক্রিপ্ট-যাচাই (EN/BN কী ও {n} প্লেসহোল্ডার) এখনো করা হয়নি।
- বাকি: ধাপ ৩ (বাকি অংশ), ৪, ৫, ৬ (allowBackup, API key সংরক্ষণ), ৭। পরের সেশনে "REVIEW-PROGRESS.md থেকে চালিয়ে যাও"।

## ধাপ ৩–৭ (শেষ, স্ট্যাটিক রিভিউ)
- [মধ্যম] AndroidManifest: allowBackup=true হলেও Gemini API key (intelliq_state) ও ক্লাউড ফ্ল্যাগ Google Backup/ডিভাইস-ট্রান্সফারে যেত। ঠিক: backup_rules.xml + data_extraction_rules.xml (intelliq_state, intelliq_cloud বাদ)। XML পার্স OK; Android বিল্ড অযাচাইকৃত।
- ধাপ ৫(চ) i18n স্ক্রিপ্টে যাচাই: EN 638 = BN 638 কী, কোনো একপেশে কী নেই, {n}/{f} প্লেসহোল্ডার মিলেছে, ডাইনামিক কী (eval.flag/qs/state) enum-এর সাথে মিলেছে। খালি মান (fig.note, ai.subtitle, sec.one ইত্যাদি) ইচ্ছাকৃত।
- ধাপ ৩: MediaStore সেভ SDK>=29 গার্ডেড, ৮/৯-এ share sheet (ঠিক আছে)। ধাপ ৪: ছবি লোডার সাবস্যাম্পল+recycle+EXIF আছে। ধাপ ৬: API key হেডারে (URL-এ নয়), লগে ছাপে না, শুধু INTERNET পারমিশন, FileProvider শুধু cache পাথ।
- ধাপ ৭: HeightCache কী-ভিত্তিক (env বদলালে ক্লিয়ার); স্পষ্ট মূল-থ্রেড ভারী কাজ পাইনি।
- অ-ঠিক: intelliq-shared.keystore রিপোতে (পাসওয়ার্ড "android") — Firebase SHA-1 স্থির রাখার জন্য ইচ্ছাকৃত, রিলিজে আলাদা কী দরকার। Gemini key ক্লাউডে (Firestore) সিংক হয় — নকশাগত, ব্যবহারকারীকে জানানো ভালো। InfoTip বড় টেক্সটে স্ক্রল নেই (নিম্ন)।

## নতুন ফিচার: লাইভ ক্যামেরা ইন্ডিকেটর (অযাচাইকৃত — কম্পাইল/চালানো হয়নি)
- data/OmrLiveCheck.kt (বিশুদ্ধ Kotlin): প্রিভিউ ফ্রেমে OmrAlign.align (শিথিল সীমা) + অন্ধকার যাচাই -> লাল/হলুদ/সবুজ ও বার্তা। টেস্ট: OmrLiveCheckTest।
- ui/OmrLiveCamera.kt: CameraX প্রিভিউ+ImageAnalysis(RGBA, লাল চ্যানেল)+ImageCapture; ৩টানা ভালো ফ্রেমে সবুজ; প্রতিটি ছবির পর কড়া (আসল) যাচাই, ব্যর্থ হলে "আবার তুলুন/তবুও ব্যবহার"; ব্যাচে একের পর এক ছবি।
- ম্যানিফেস্টে CAMERA পারমিশন; তাই সিস্টেম-ক্যামেরা (TakePicture) সাইটগুলো (OmrEval, AiModal, FigureViews) rememberCameraGate দিয়ে মোড়ানো (নইলে SecurityException)।
- build.gradle.kts: camera-core/camera2/lifecycle/view 1.3.4। স্ট্রিং live.* (EN+BN ১৮টি করে)।
- সীমা: লাইভ চেক শিথিল (১২৮০x৯৬০ ফ্রেম), চূড়ান্ত নিশ্চয়তা শট-পরবর্তী কড়া চেক; QR পড়া যায় কি না এই চেকে নেই।

## লাইভ ক্যামেরা — দ্বিতীয় রাউন্ড (অযাচাইকৃত, কম্পাইল হয়নি)
- data/OmrShotQuality.kt: ছবি তোলার পর (১) ঝাপসা: চার কোণের কালো বর্গের কিনারার প্রস্থ (১০%→৯০%, mm এককে, মিডিয়ান) > 0.55mm হলে BLUR; (২) QR না পড়া/উত্তরমালার পাতা/অন্য QR হলে সতর্কবার্তা। সবই উপদেশমূলক: "আবার তুলুন" বা "তবুও ব্যবহার"।
- OmrLiveCamera.strictCheck এ সংযুক্ত; স্ট্রিং live.blur/noQr/keyPage/foreignQr (EN+BN)।
- টেস্ট: OmrShotQualityTest (৮টি), OmrLiveCheckTest (৪টি) লেখা, চালানো হয়নি। ০.৫৫mm সীমা গাণিতিক অনুমান — আসল ফোনে মেলাতে হবে।
- পরের ধাপ: ইউজার ./gradlew testDebugUnitTest assembleDebug চালিয়ে ব্যর্থ টেস্ট/কম্পাইল ত্রুটি জানাবেন।
