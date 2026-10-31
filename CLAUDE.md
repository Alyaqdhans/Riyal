# Riyal — context for working on this repo

Riyal is an Android expense tracker for **Oman** that builds its records from the bank
SMS already in the inbox. Kotlin + Jetpack Compose (Material 3), bilingual (English and
Arabic, RTL), light and dark. Package `com.alyaqdhan.riyal`. GitHub: Alyaqdhans/Riyal.

## Ground rules from the owner

- **Branch per version.** Work for a release goes on a branch named after it (`1.8.0`),
  cut from `master`. The owner merges it. Don't push or open PRs unless asked.
- **No attribution in commits.** No `Co-Authored-By` or similar trailers. Commit style:
  `area: a plain-English sentence` subject, and a body that explains *why*.
- **No real data in commits, changelogs or code.** The repo is public. Nothing from the
  owner's or testers' messages, exports or screenshots goes into a commit message,
  CHANGELOG.md, a comment or a test: no message text, masked names, amounts, balances,
  account digits, references or shop names. Explain with made-up data in the same shape
  ("accepted for AAAA####BBBB", "OMR 10.000", "007 ###### 00470", "at Example Cafe"). SMS
  exports and screenshots are never committed.
- **Release notes are a summary, shaped like YTDLnis's.** The GitHub release body (which
  the app's Changelog page shows) is not CHANGELOG.md: `# What's Changed`, then a `##`
  heading per headline feature with a short paragraph in plain words, then `## Other
  stuff` with one line per smaller change or fix, and last `**Full Changelog**:
  https://github.com/Alyaqdhans/Riyal/compare/<previous tag>...<new tag>` with the real
  tag names (`gh release list`; recent ones are `V1.8.1`). Anything that costs the user
  something - a wipe, a permission, an uninstall - gets the first section. A hotfix is
  `# Hotfix` and its bullets. Written to `release/Riyal-vX.Y.Z-notes.md` (gitignored).
- **Keep release notes short.** The owner wants them read at a glance: a few headline
  sections of one or two sentences each, and Other stuff bullets of a few words. Say what
  changed for the user, never how it was built; library names, versions and causes stay
  in CHANGELOG.md and the commits. Leave out what a user would not notice.
- **Never run an emulator.** Verify with `assembleDebug` + unit tests. The owner tests on
  a real phone from Android Studio, where the debug build installs beside the real app.
- **Native components only.** Use stock Material 3 parts (`ListItem`,
  `HorizontalFloatingToolbar`, `SwipeToDismissBox`, `ModalBottomSheet`, `AlertDialog`,
  chips, `FloatingActionButton`) and real libraries (Vico for charts, Markwon for
  release notes, as YTDLnis draws its own, LazyColumnScrollbar for the scroll thumb).
  Every vertical scroll - a tab, a page, a sheet or a dialog - sits in `ScrollThumb`
  (`ui/compose/ScrollThumb.kt`), with `bottom` set to whatever floats over its foot
  (`toolbarSpace` on a tab), and size it through the wrapper, not the list. Don't
  hand-draw widgets. Wavy progress indicators stay
  wavy components with `amplitude = 0` (`FlatWheel`/`FlatBar` in
  `ui/compose/Indicators.kt`), except `ScanBar` and `SiftBar`: the owner wants a scan's
  bar, and the bar over Needs review and Needs a category that fills as the pile is
  worked through, to keep their wave.
- **Pages are activities, each with its own `res/layout`.** The phone's own transition
  plays between them (the owner's MyBattery app is the reference). Never set a window
  animation or `overridePendingTransition`. The five tabs are fragments that stay built
  once made (the rest are built while the app is idle, after launch); a switch only
  slides two already-drawn views sideways in the toolbar's order
  (`MainActivity.openTab`), so the highlight and the slide start on the same frame.
- **No decorative motion.** No pop-in or bounce on content, and `MotionScheme.standard()`.
  Charts use `animationSpec = null, animateIn = false`. The owner's exceptions: the tab
  slide, Lists' tabs (a `HorizontalPager`: a tab slides to its list, a finger drags
  between them), the swipe tours (the first time Transactions or Lists is seen, one row
  opens by itself under a tooltip saying what its swipe uncovers: `SwipeTour`, shown only
  while the tab is in view, `LocalTabInView`), and every dialog's pop-in with a quick close (`ui/compose/DialogPop.kt`: put
  `Modifier.popIn(pop)` on the dialog and send every way of closing it through
  `pop.leave { … }`). Bottom sheets keep their own slide. And headline figures roll their
  digits (`MoneyText(roll = true)`: odometer wheels after firePOS's NumberFlow with its
  `continuous` plugin, places paired from the right so a figure grows leftwards): in from zero only
  on app start and when a scan or refresh lands (`Rolls`), never on a tab switch or scroll;
  and to every new value while on screen. Home's net,
  pills and budget left, Analysis totals, Accounts balances. List rows never roll.
  Analysis's category wheel fills clockwise with the same timing each time its tab is
  shown (`LocalTabShows`), on a scan or refresh, and when its slices change.
  Setup's last step: a tick that draws itself, then the app opens on its own after 2 s.
  The app opens on `LaunchSplash` (after firePOS's splash): the rial sign traced as a
  line, looping until the store has loaded (`vm.storeReady`), then filled, then lifted;
  the system splash has no icon so the two read as one. Home's figures roll as it lifts
  (`Rolls.replay`), and the launch scan and building the other tabs wait for the roll.
- **Choices of dates are tiles** (`ui/compose/Presets.kt`: `PresetSheet`/`PresetGrid`),
  never a plain list of words. Each tile says what it covers, and all tiles are one size.
- **Exact versions.** `androidx.compose.material3:material3:1.5.0-alpha29` with
  `@OptIn(ExperimentalMaterial3Api / ExperimentalMaterial3ExpressiveApi)`. Use its
  current APIs: `ListItem(onClick | checked/onCheckedChange, …) { headline }`,
  `rememberBottomSheetState(initialValue = SheetValue.Hidden)`,
  `SelectableDropdownMenuItem` for a menu choice, and `ToggleButtonDefaults.shapesFor` /
  `colors` instead of the deprecated forms. The owner raises the alpha when asked; it
  sits on Compose 1.12 (foundation 1.13 alpha).
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

- AGP 9.2.1 with built-in Kotlin 2.4.21 and the Compose compiler plugin; Gradle 9.8.2;
  compileSdk 37, minSdk 29. AGP only asks for KGP 2.2.10; the Compose compiler plugin's
  Kotlin BOM raises it, so `kotlinComposePlugin` is the Kotlin the app compiles with.
- Libraries must be built for that Kotlin or older (2.4.x). A library on a newer Kotlin
  means raising `kotlinComposePlugin` first.
- Count the tests in `app/build/test-results` rather than trusting the exit code alone.
- A debug build is never behind a release, so its update prompt never opens by itself:
  long-press a release in Settings › Updates › Changelog to open the prompt for it.
- JitPack is a repository only for LazyColumnScrollbar, which publishes nowhere else;
  `settings.gradle.kts` keeps it to that group.
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
| `data/SenderLists.kt` | Whether each sender is read, as the scan's sender gate decides (Settings › Lists) |
| `data/AccountDiscovery.kt` | Banks list, accounts proposed from messages, routing a message to an account |
| `data/TransferMatcher.kt` | Pairs an expense and an income across your own accounts |
| `data/Updates.kt` | **All network code**: GitHub releases list + APK download. `ApkInstaller.kt` hands the file to Android via FileProvider |
| `ui/MainViewModel.kt` | One VM for every screen, owned by `RiyalApp` (`Context.riyalViewModel`): scanning, setup flow, updates, edits |
| `ui/nav/Fragments.kt` | The five tabs (`Tab` in `MainActivity.kt`): fragments, each with its `fragment_*.xml` layout; hidden ones stay laid out but `INVISIBLE` |
| `ui/nav/Pages.kt` | `PageActivity` and every page opened from a tab (onboarding, setup, review, categories, lists, updates, changelog), each with its `activity_*.xml` layout |
| `MainActivity.kt` | The tabs, the floating toolbar (`HorizontalFloatingToolbar` + Add FAB), global dialogs (update prompt, manual add); sends a first run to `OnboardingActivity` |
| `ui/screens/*` | Home, Transactions, Analysis, Accounts, Settings (tabs); Setup, Review, Updates, Changelog, Categories… (pages) |
| `ui/compose/*` | Shared pieces: `MoneyText`, `CurrencySheets`, `Indicators` (`CategoryWheel`, `ScanBar`), `Presets` (date tiles), `MenuChoice`/`ChoiceMenu` (Expressive menus with icons), `ConnectedChoice` (connected button groups), `SignGlyph` (currency signs at the text's measured stroke weight), `ScrollThumb` (the thumb every scrolling list carries; it can be held and dragged, and its size follows how many items are on screen), `SwipeRow` (a swipe uncovers an action - a record's Archive/Remove, a list entry's Forget/Restore - and a tap on it confirms; `SwipeTour` shows it once; on `anchoredDraggable`), `UpdateUi`, `Sheets` (record sheet, manual entry, category chips), `BudgetSection` (Home's budget card under the net, and its editor) |

## How a message is read (1.8.0+)

1. Sender gate: phone numbers, senders the user excluded, and non-bank senders are
   skipped (unless approved during setup).
2. `SmsParser.parse` extracts the facts. Messages with an amount but no direction words
   still get their facts through `parseAs`.
3. `Skeleton.of(sender, body)` gives the template key: masks, amounts, numbers and names
   set aside, including the shop after "at" whatever its case (ATM, POS and the like
   kept). Labels the user taught (stored in `Store.labels`) apply to every message with
   that key, and Review's "remember for similar" dismissals (`Store.muted`) use it too.
   A label can also carry an account (`TemplateLabel.accountId`), asked in Review when the
   sender holds none of the user's accounts: its messages route there, pass the "not your
   bank" check, and found no account for their sender (their observations carry it).
4. `BalanceChain` proves directions; `TemplateInference` marks templates with two or
   more agreeing proofs and none against as *proven*, and spreads their direction.
5. Accounts follow the balance, not the digits. `BalanceChain` joins each message to the
   earlier one from the same bank whose balance it continues, and the joins make lines:
   one line is one account. A number is an account only when a line mostly quotes it and
   it recurs; other digits (the other party's account, references) route by their line.
   An incremental scan reads back the newest stored records (`ANCHORS_PER_SENDER`) so a
   new message still finds its line. A full scan folds accounts proven to be such
   numbers into the real one (`AccountDiscovery.foreignAccounts`), and their digits stay
   as `Account.aliases`. No reading order is guessed for a masked number:
   banks write one account several ways (Bank Nizwa: "002########001", "001 ###### 00210",
   "001######00210"). The parser reports its digit groups, and
   `AccountDiscovery.resolveMaskedTails` settles which is the account across the bank's
   messages - numbers on one balance line, or runs sharing a group that never overlap in
   time, are one account, known by the group they all share (else the one no other account
   has, else the shorter). A number whose end repeats its start (refunds:
   "00210 ###### 00210") names no account, and within one bank an account quoted by under
   the main account folds into it unless it tops a balance line the main account never
   appears on, over the same months (`minorityFolds`). The account is always the end of a masked number; the digits
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
- Bumping `Skeleton.VERSION` changes every template key. Labels are keyed by it, so
  `Store.loadLocked` carries the previous shape's labels over (`Skeleton.carryOver`,
  through stored samples, records and Review items) and muted kinds are re-keyed from
  their sample; keep `legacyOf` producing the previous shape exactly.
- Draw category and account colours through `categoryColor(id)`, `rememberBadgeColours`
  or `legible(...)` (ui/theme/Contrast.kt), never `Color(Categories.colorFor(id))`: the raw
  palette falls under 3:1 on light cards. `ContrastTest` holds every colour to it.
- Never read an animated value during composition in something that animates often.
  `DigitWheel` reads its wheel only in `drawBehind` and draws ten digits measured once;
  reading it in composition recomposed and re-laid-out text every frame for every digit,
  and the app stuttered whenever figures rolled.
