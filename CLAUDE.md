# Riyal — context for working on this repo

Riyal is an Android expense tracker for **Oman** that builds its records from the bank
SMS already in the inbox. Kotlin + Jetpack Compose (Material 3), bilingual (English and
Arabic, RTL), light and dark. Package `com.alyaqdhan.riyal`. GitHub: Alyaqdhans/Riyal.

## Ground rules from the owner

- **Branch per version.** Work for a release goes on a branch named after it (`1.8.0`),
  cut from `master`. The owner merges it. Don't push or open PRs unless asked.
- **No attribution in commits.** No `Co-Authored-By` or similar trailers. Commit style:
  `area: a plain-English sentence` subject, and a body that explains *why*.
- **Never run an emulator.** Verify with `assembleDebug` + unit tests. The owner tests on
  a real phone from Android Studio, where the debug build installs beside the real app.
- **Native components only.** Use stock Material 3 parts (`ListItem`,
  `HorizontalFloatingToolbar`, `SwipeToDismissBox`, `ModalBottomSheet`, `AlertDialog`,
  chips, `FloatingActionButton`) and real libraries (Vico for charts, the markdown
  renderer for release notes). Don't hand-draw widgets. Wavy progress indicators stay
  wavy components with `amplitude = 0` (`FlatWheel`/`FlatBar` in
  `ui/compose/Indicators.kt`), except `ScanBar`: the owner wants a scan's bar to keep
  its wave.
- **Pages are activities, each with its own `res/layout`.** The phone's own transition
  plays between them (the owner's MyBattery app is the reference). Never set a window
  animation or `overridePendingTransition`. The five tabs are fragments that slide
  sideways in the toolbar's order (`TabSlide`).
- **No decorative motion.** No pop-in or bounce on content, and `MotionScheme.standard()`.
  Charts use `animationSpec = null, animateIn = false`. The owner's exceptions: the tab
  slide, and every dialog's pop-in with a quick close (`ui/compose/DialogPop.kt`: put
  `Modifier.popIn(pop)` on the dialog and send every way of closing it through
  `pop.leave { … }`). Bottom sheets keep their own slide. And headline figures roll their
  digits (`MoneyText(roll = true)`, like firePOS's NumberFlow): in from zero when they
  appear, again when a scan lands (`Rolls.replay()`), and on every change. Home's net and
  pills, Analysis totals, Accounts balances. List rows never roll.
- **Choices of dates are tiles** (`ui/compose/Presets.kt`: `PresetSheet`/`PresetGrid`),
  never a plain list of words. Each tile says what it covers, and all tiles are one size.
- **Exact versions.** `androidx.compose.material3:material3:1.5.0-alpha23` with
  `@OptIn(ExperimentalMaterial3Api / ExperimentalMaterial3ExpressiveApi)`. Use its
  current APIs: `ListItem(onClick | checked/onCheckedChange, …) { headline }`, and
  `rememberBottomSheetState(initialValue = SheetValue.Hidden)` instead of the deprecated
  forms.
- **Every string in both languages.** `res/values/strings.xml` and
  `res/values-ar/strings.xml` must keep the same names and placeholders, with Arabic
  plurals given all six forms. Never hard-code user-visible text.
- **Money shows signs, not codes.** Show amounts with `MoneyText` (one amount) or
  `MoneyAwareText` (a sentence containing `Money.format` output). These draw the
  currency's own sign; the new Omani rial sign comes from firePOS's path data.

## Build and test

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio1\jbr'   # java on PATH is Java 8
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
```

- AGP 9.2.1 with built-in Kotlin 2.3.10 and the Compose compiler plugin; Gradle 9.6;
  compileSdk 37, minSdk 29.
- Libraries must be built for Kotlin ≤ 2.3.x. That is why the markdown renderer is
  pinned at 0.41.0; later versions pull kotlin-stdlib 2.4.
- Count the tests in `app/build/test-results` rather than trusting the exit code alone.
- Release steps, signing and the keystore history are in [RELEASE.md](RELEASE.md).
  Changes go in [CHANGELOG.md](CHANGELOG.md).

## Map

| Where | What |
| --- | --- |
| `core/Money.kt` | Integer minor units (baisa); `format` writes "OMR 12.500", which `MoneyAwareText` turns into signs |
| `core/Currencies.kt` | Currency registry: flag, name, sign vector, placement, rial value (pegged or editable), `convert` |
| `core/Prefs.kt` | SharedPreferences: scan range, setup state, update channel, FX overrides, excluded senders |
| `core/Verbose.kt` | In-memory log of every decision the scan makes (Settings › Activity log) |
| `data/SmsReader.kt` | The one `READ_SMS` query; scans are manual or on launch, never in the background |
| `data/SmsParser.kt` | Pure extraction: amount, currency, balance, account tail, merchant. `parse` uses word cues; `parseAs` reads a message as a known direction. **Bump `VERSION` when parsing output changes** |
| `data/Learning.kt` | `Skeleton` (message → template key), `BalanceChain` (direction proven by balance deltas), `TemplateInference`, `Tutor` (setup questions + stopping rule) |
| `data/ScanEngine.kt` | One pass: read → decide (taught label > balance proof > proven template > words) → accounts → records (with FX conversion) → transfers → store |
| `data/Store.kt` | One JSON file, written atomically. Records, accounts, rules, labels, template stats. **Bumping `SCHEMA_VERSION` wipes user data** |
| `data/AccountDiscovery.kt` | Banks list, accounts proposed from messages, routing a message to an account |
| `data/TransferMatcher.kt` | Pairs an expense and an income across your own accounts |
| `data/Updates.kt` | **All network code**: GitHub releases list + APK download. `ApkInstaller.kt` hands the file to Android via FileProvider |
| `ui/MainViewModel.kt` | One VM for every screen, owned by `RiyalApp` (`Context.riyalViewModel`): scanning, setup flow, updates, edits |
| `ui/nav/Fragments.kt` + `res/navigation/nav_graph.xml` | The five tabs: fragments of the nav graph, each with its `fragment_*.xml` layout |
| `ui/nav/Pages.kt` | `PageActivity` and every page opened from a tab (onboarding, setup, review, categories, updates, changelog), each with its `activity_*.xml` layout |
| `ui/nav/TabSlide.kt` | The tabs' sideways slide; `MainActivity`'s destination listener sets its direction (`TabMotion`) |
| `MainActivity.kt` | The tabs, the floating toolbar (`HorizontalFloatingToolbar` + Add FAB), global dialogs (update prompt, manual add); sends a first run to `OnboardingActivity` |
| `ui/screens/*` | Home, Transactions, Analysis, Accounts, Settings (tabs); Setup, Review, Updates, Changelog, Categories… (pages) |
| `ui/compose/*` | Shared pieces: `MoneyText`, `CurrencySheets`, `Indicators` (`CategoryWheel`, `ScanBar`), `Presets` (date tiles), `SwipeRow` (swipe to reveal Archive/Remove, on `anchoredDraggable`), `UpdateUi`, `Sheets` (record sheet, manual entry, category chips) |

## How a message is read (1.8.0+)

1. Sender gate: phone numbers, senders the user excluded, and non-bank senders are
   skipped (unless approved during setup).
2. `SmsParser.parse` extracts the facts. Messages with an amount but no direction words
   still get their facts through `parseAs`.
3. `Skeleton.of(sender, body)` gives the template key. Labels the user taught (stored in
   `Store.labels`) apply to every message with that key.
4. `BalanceChain` proves directions; `TemplateInference` marks templates with two or
   more agreeing proofs and none against as *proven*, and spreads their direction.
5. Accounts follow the balance, not the digits. `BalanceChain` joins each message to the
   earlier one from the same bank whose balance it continues, and the joins make lines:
   one line is one account. A number is an account only when a line mostly quotes it and
   it recurs; other digits (the other party's account, references) route by their line.
   An incremental scan reads back the newest stored records (`ANCHORS_PER_SENDER`) so a
   new message still finds its line. A full scan folds accounts proven to be such
   numbers into the real one (`AccountDiscovery.foreignAccounts`), and their digits stay
   as `Account.aliases`. The account is always the end of a masked number; the digits
   before the mask (`accountHead`, "00210" of "00210 ###### 001") are the branch, and an
   account made from them folds into the one the end names.
6. Records are converted into the account's currency when the bank charged another, and
   the original is kept on the record (`originalAmountMinor/Currency`).
7. Each scan stores `TemplateStat`s, which the setup's `Tutor` turns into questions. Only a
   wrong kind counts against the reading; a category fix does not. Answers are kept, and
   stepping back to one replays the tutor from the answers as they now stand.

The scan fingerprint includes the parser version, skeleton version, labels, excluded
senders and FX overrides, so changing any of them makes the next scan re-read
everything.

## Gotchas

- `Txn.id` is a hash of the message, which is how user edits survive rescans. Manual
  records are `man-…`, merged transfers `trf-…`.
- Store JSON fields added after schema 2 are optional (`oamt`, `ocur`, `kind`, `labels`,
  `templates`) so old files still load. Keep new fields optional rather than bumping
  the schema.
- Shell heredocs on this machine collapse doubled backslashes. Edit Kotlin regex source
  with an editor tool or a script file, not inline `sed`/heredoc replacements.
- Each tab screen leaves `toolbarSpace` at the end of its list for the floating toolbar
  drawn over it. Pages are activities and have no toolbar.
- The view model lives as long as the app, so `autoScanOnLaunch(fresh)` is told when
  `MainActivity` starts fresh, and a page restored after process death checks
  `vm.setupActive` before trusting its saved state.
- Store JSON field `alias` (account aliases) is optional like the others.
