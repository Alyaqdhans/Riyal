# Changelog

Every release of Riyal, newest first. Released versions are condensed from their
[GitHub release notes](https://github.com/Alyaqdhans/Riyal/releases); the APK for each
is attached there.

## 1.8.0 — unreleased (branch `1.8.0`)

### Before you install

- **New permission: install updates.** `REQUEST_INSTALL_PACKAGES` lets Riyal open
  Android's installer on an update you chose to download. Android still shows its own
  install screen and asks you to confirm.
- **The first scan after updating reads your whole inbox again.** How messages are read
  changed (parser version 11), so records are rebuilt. Your categories, rules, accounts
  and budgets are kept; nothing is wiped.
- **What you taught a test build is asked again.** Templates are recognised differently
  now (skeleton version 2), so answers given to an earlier 1.8.0 build no longer apply.

### Riyal learns how your banks write

- Messages are no longer read with fixed word lists. Each message is reduced to the
  template the bank fills in, and its direction comes from the strongest evidence
  available:
  1. what you taught Riyal about that kind of message;
  2. the account's balance moving by exactly the amount, which proves money in or out
     with no words needed;
  3. the template's balances having proven its direction before;
  4. only then the words.
- Purchases that fixed rules used to misread, like money *received* through payment
  services read as spending, or a fuel station called TWIN dropped as a prize draw, are
  now settled by arithmetic.
- **Setup.** After you allow SMS access, Riyal reads your inbox, then asks:
  - **Are these your accounts?** Banks and accounts grouped, each one editable: name,
    bank, type (current, savings, card, wallet), balance, digits and senders. "Not my
    bank" drops a sender for good, and senders it missed can be added.
  - **Is this read correctly?** Real messages, each with what Riyal made of it. Yes or
    No. No keeps money in or out as it was and asks for the right category, because
    that is what is usually wrong; the other kinds are a tap away. Every answer decides
    every message written the same way. Answering correctly ends the questions sooner,
    while getting money in or out wrong makes Riyal check its other readings of that
    bank. The back arrow at the bottom, or the back gesture, steps through earlier
    answers to change them. **Skip**, at the top, ends the questions there.
- Run the setup again any time from Settings › Teach Riyal.

### One account is one account

- **A bank's other numbers no longer become accounts.** Messages also quote the other
  party's account on a transfer, or a reference where the account usually goes, and
  each one used to become an account of its own: one Bank Nizwa account showed as
  seven. Riyal now follows the balance. Messages whose balances continue one another
  are one account, whatever digits they quote, and an account made from such a number
  is folded back into the real one on the next full read.
- Accounts masked with "#", with or without spaces ("00210 ###### 001"), read their
  real last digits, however the spaces fall and whatever invisible marks the message
  carries around them; a number whose end is hidden names no account.
- **A masked account number is settled by all of the bank's messages, not by its
  order.** Bank Nizwa writes one account "002########001", "001 ###### 00210" and
  "001######00210", so no reading order is right for all of them. Each number is taken
  as its groups of digits; numbers whose balances run on from one another are one
  account, and so are runs that share a group and never overlap in time (the account
  renumbered). The account is the group all its numbers share - and two accounts at one
  branch are told apart by their ends, never by the branch they share. A number on an
  account's balance line that shares no group with it, such as the other party's on a
  transfer, is left to itself; on test builds it stopped every scan with "Unknown
  error".
- **The majority decides.** Within one bank, the account most messages name is the main
  one. Any other is folded into it - named or not - unless it shows a balance of its own,
  never shared with the main account, over the same months: the other party's number on
  transfers, a refund repeating the branch and an old numbering of the same account all
  fold.
- **Refunds no longer make an account.** Bank Nizwa's refunds write the account as
  "00210 ###### 00210", the branch twice and the account's digits nowhere, so every
  refund said "0210" - and enough of them made it an account. A masked number whose end
  repeats its start names no account; the refund goes where its balance says.
- Merging two accounts keeps the balance quoted last, instead of adding every record
  on top of an older or missing one.
- An account made from where a masked number starts is put right on every scan, not
  only on a full read: folded into the account its number ends with, or, when it is the
  bank's only one, renumbered to that end. A bank's sender is one sender however it is
  spelled ("BANK NIZWA", "BankNizwa"). An account an earlier build made from
  where the number starts ("0210") is folded into the one its end names on the next
  full read, even if it was given a name since.
- Bank Nizwa purchases read the shop, not the date after it.
- **Same account as…** in an account's editor merges it into another account of the same
  bank. Its records move across, and its number keeps routing there.
- Review's "money out / money in" teaches the whole kind of message instead of adopting
  a keyword, and it gains "Not a transaction".
- The keyword lists and sender lists are gone from Settings; the setup replaces them.

### Currencies

- Amounts show the currency's own sign instead of its code: the **new Omani rial sign**,
  the new riyal and dirham signs, €, £, ₹, ₺ and ¥.
- The currency picker is a searchable list with flags and names in both languages.
- **Purchases abroad count in rials.** A Steam game charged "USD 9.99" counts in your
  totals and your balance, converted at the fixed peg. What the bank charged is kept on
  the record and shown under the amount. Gulf currencies convert at their pegs, and
  others at reference rates you can correct in Settings › Exchange rates.
- A message quoting both figures, like "USD 9.99 (OMR 3.912)", takes the rial one.

### Updates

- A new updater, modelled on ytdlnis's. A new version opens a dialog once, with its
  notes. **Update** downloads it with progress shown on the button, then **Install**
  hands it to Android's installer. **Skip this version** skips that version only.
- Settings › Updates has a status card, "check on start", beta releases and skipped
  versions. A check you ask for shows that it is checking for a second at least.
- **Changelog** lists every release, with its notes and its APK.

### A layout that is easy to get around

- Material 3 Expressive's floating toolbar: five tabs (Home, Transactions, Analysis,
  Accounts and Settings, named on a long press) and the Add button attached at its end.
  Tabs slide sideways into one another in the toolbar's order, starting the moment you
  tap: each tab stays built once open (the rest are built while the app is idle), so a
  switch only moves it into view, and every tab keeps its place.
  The tab you are on widens into a filled pill as you switch, and the one you left
  narrows back, instead of the highlight jumping.
- **Pages open the way your phone opens screens.** Setup, Review, categories, updates
  and the rest are screens of their own, so your phone's own transition plays going in
  and coming back.
- **Opening the app** draws the rial sign as a line, the way firePOS's splash does, for as
  long as your records are loading; then it fills in and lifts, and Home's figures roll
  in on a phone with nothing else to do. The scan on launch starts after they have, so
  the roll is smooth on a cold start.
- **Colours you can read in the light theme.** Category colours were picked on white, and
  on light cards most of them faded - amber and lime icons and bars under 1.5:1. Each is
  now darkened just enough, its hue kept, to stand 3.5:1 on the cards (and lightened in
  the dark theme where the deep ones sank), and badge icons are fitted to their own tinted
  circle. Money-in green is dark enough to read as text on cards. Ticks on colour swatches
  are black or white, whichever shows. The dark theme met the same bars already; the splash's
  name is now lightened there to read as text does.
- A new icon: the Omani rial sign. On Android 12 and later it takes the wallpaper's
  colours, as the app does; on older phones it is teal.
- **Home is a dashboard:** net for the period, large, with expense and income as pills
  under it; one "needs your attention" list; top spending; and the period's latest
  records, which change with the dates above them.
- **The budget sits under the net** (when switched on in Settings), as one card: what is
  left to spend, large, of the whole plan; a bar with a mark where spending should be by
  now, and whether it is on pace, with the days left; then the categories closest to
  their caps, laid out like top spending, the rest a tap away. A period without one
  offers last period's caps in one tap. Its editor sets every cap at once: tap the
  categories to cap, type what each may spend, with what went on it the period before as
  a guide, and Save; deleting the plan is at the end of the form. Tapping a category
  leaves the keyboard closed, and the keyboard's Next steps down the amounts, Done on
  the last.
- **Dates are tiles.** Every period choice - Home, Analysis, How far back, the welcome
  screen - is a sheet of equal tiles, each with an icon and the dates it covers.
- Records are standard list rows with circular category icons. Swipe one open and tap
  the action it shows: Archive one way, with undo, Remove the other.
- Settings is grouped rows with icons. Switches no longer fill their row in when on.
- The currency picker's signs sit in matching round slots.
- The welcome screen shows the app's mark instead of the coin face, and its buttons,
  like setup's, float over the page.
- Dialogs pop in, and leave quickly.
- Headline figures - Home's net and its pills, the budget left, Analysis totals, account balances - roll
  like an odometer, the way firePOS's figures do: in from zero when the app starts and
  when a scan or a refresh lands, and to each new value while you watch. A tab switch or
  a scroll shows them as they are. Each digit turns through the
  ones between, and a figure that gains digits grows leftwards from its decimals. The digits
  are drawn, not rebuilt, as they turn, so a screen of rolling figures stays smooth.
- Analysis's category wheel fills in clockwise, slice by slice, each time you open the
  tab, when a scan or a refresh lands, and when you change the period.
- Pull to refresh shows Material 3 Expressive's loading indicator, a shape that morphs
  while the scan runs, instead of a spinning arc.
- **Needs a category** opens one name at a time, sliding open in place with its
  categories right under the name. A tap picks a category and **Save** files the records,
  so nothing disappears on a stray tap. The records are behind "Choose records" (five at
  first, then all), and saving a whole name opens the next one. "Always ask for this name"
  is a switch.
- **Review**'s cards: the message quoted in a box that slides open, "Remember for
  similar messages" as a switch, and Add manually as the filled answer. A message that
  only lacked its direction offers Expense and Income in Home's colours and arrows, with
  Not a transaction on its own row under them, and **Save** sends the answer, rather than the first tap. A proposed transfer shows its two
  sides with Home's arrows. Answered cards slide out, and the rest close up.
- Settings opens on a status card: up to date, reading, or what went wrong, when it last
  read, and what it holds.
- Category choices are every category in three level rows that scroll sideways, Other
  last, instead of a handful and "More". The rows sit close, so the Add form fits
  without scrolling. Tapping a category keeps the rows still; only a
  category already chosen when they open is scrolled to.
- The Add form names its kinds as Home does - Expense, Income, Transfer - on Material 3
  Expressive's connected buttons, in Home's colours and arrows, and writes the amount
  large. Currency fields and Settings show the currency's sign instead of its flag.
- A record's sheet asks one question about the name's other records - just this one,
  same category, or ask each time - instead of a switch and a button that overlapped.
  An account's "closed" is a plain switch row. Delete in the account and category
  editors is a red button at the end of the form, no longer beside Cancel.
- Save is a filled button in every dialog that has one - Add, accounts, categories,
  keywords, exchange rates - instead of a word.
- Buttons answer a press: Material 3 Expressive's shapes, whose corners square off
  under the finger and spring back - every button, and the toolbar's tabs. Add account,
  New category and Add keyword are its taller buttons, with a + icon.
- Currency signs are drawn as heavy as the text beside them, whatever font the phone
  uses: the font's strokes and the sign's are measured, and the sign thinned or
  thickened to match. Home's net is bold.
- Expense, Income and Transfer are named the same everywhere - the Transactions filter
  (with Home's arrows and colours), Review, setup, keywords - instead of "Out", "Money
  out" and the like. The Add form's kinds wrap to two rows on a narrow phone.
- The new-category and keyword dialogs choose Expense or Income on the same connected
  buttons as the Add form, instead of the older segmented row.
- The category editor shows the category as it will look beside its name, and picks
  its icon and colour on Material 3 Expressive toggle buttons: the one picked fills with
  the colour and turns from a circle into a rounded square. Icons sit in three rows that
  scroll sideways.
- Transactions' bar is a filter icon that counts the filters on, without the export
  button (export is in Settings). The filter sheet shows accounts with their icons and
  categories in the same level rows as everywhere else.
- Analysis puts net, expense and income on one compact card - each tinted, each with a
  pill comparing it to the period before - so the category wheel leads the screen. What
  moved between your own accounts has a line of its own, marked "not counted". Its switches are Material 3 Expressive's connected
  buttons.
- Menus - sorting, accounts in the Add form, "Same account as…", bank names - are
  Material 3 Expressive's menus, each choice with its icon and the current one ticked.
- Setup ends on a tick that draws itself, and the app opens two seconds later; there is
  no button to press.
- An account a merge left under the digits its messages never end with (merging "001"
  into "0210") takes the digits they do end with on the next full read.
- **No more motion for its own sake.** Cards no longer pop in, buttons no longer
  bounce, charts draw in place, and the coin mascot no longer blinks. What moves is
  movement between places - tabs, pages - dialogs, and figures that change. The category wheel is back
  on Material's own wavy indicator, held flat; the bar a scan shows keeps its wave.

## 1.7.1 — 2026-10-27

### Fixed

- Purchases at a fuel station whose name ends in "TWIN" were never recorded. The advert
  filter matched "win" inside the name and threw every purchase there away, which in one
  real inbox was 49 purchases. English advert words now have to start a word. The first
  scan after updating re-reads your messages, so the missing purchases come back.

### Added

- When a new release is out, a popup on Home says so, once.

## 1.7.0 — 2026-10-01

### Riyal speaks Arabic

- Arabic throughout: every screen, the help, the scan log and the parser's trace.
- Choose the language in Settings, or follow the phone.
- Right-to-left layout on every screen.
- Dates, times and month names in the app's language. Numbers stay 1234567890, matching
  what banks write.
- Omani bank names in Arabic, and amounts typed on an Arabic keyboard are understood.

### Backup

- Save everything Riyal knows to a file you choose, with or without the bank message
  text. Restore from a backup, behind a confirmation. A backup from a different version
  is refused rather than half read.

### Fixed

- Pull to refresh works on every screen that shows scanned data.

## 1.6.1 — 2026-09-17

### Much faster

- Scanning reads only what is new. On a 50,000-message inbox that went from 35 seconds
  on every launch to under a quarter of a second. "Rescan everything" still reads it all.

### New

- Messages with an amount but no keyword reach Review: one question, money out or in.
- Home says when a new version is out, and has the same date picker as other screens.
- The update check says how it went, and the changelog is behind the (i).

### Fixed

- One message could become two records.
- Every launch rediscovered accounts it already had.
- The startup log's claim about permissions.
- A stale scan summary popping up later.

## 1.6.0 — 2026-09-05

### Before you install

- New permission: internet, only to check GitHub for a new version and download it.

### New

- Update checking, with the APK downloaded to Downloads for you to install.
- Filing grouped by shop: one decision covers all of a shop's records.
- Read the bank message behind any record.
- "Leave under Other" for records you don't want to decide now.

### Better and fixed

- The same shop written in Arabic and English is one shop.
- Swiping a record shows Remove or Archive and waits for a tap.
- Less text; explanations moved behind (i).
- Fixes for duplicate accounts from one bank spelled two ways, an empty screen after
  un-archiving, the undo bar's position, Analysis forgetting the month, and rotation.

## 1.5 — 2026-09-01

**Upgrading from 1.1 cleared saved data.** Accounts, transfers and dated budgets changed
the shape of every stored record.

- **Accounts and transfers.** Accounts worked out from the inbox with running balances.
  Moves between your own accounts are detected and counted as neither spending nor
  income.
- **Budgets** for any period, with progress on Home.
- **Categories:** a screen of their own, twelve more built-ins, custom icons, learned
  keywords, filing a whole merchant at once, "ask me every time".
- **Analysis** rebuilt around income and net, compared with the previous period.
- **Home** became one hero card; rows are swiped to act.
- Fixes: income counted as spending, rules crossing sides of the ledger, Arabic
  merchants, non-bank senders spending your money.
- Speed: a full scan about 30% faster; logging no longer mirrored to logcat in release.
- Build: release signing from `local.properties`, versionCode that rises, Gradle caches.

## 1.1 — 2026-07-05

- Follow-up fixes to 1.0, released the same day.

## 1.0 — 2026-07-05

- First release. Reads bank SMS on demand (`READ_SMS` only), with a keyword gate, a
  verbose log of every decision, and a Review list for what it can't read. OMR-first
  with three-decimal amounts, Omani merchant categories, custom categories and
  per-category budgets.
