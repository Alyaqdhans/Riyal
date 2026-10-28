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
  changed (parser version 4), so records are rebuilt. Your categories, rules, accounts
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
    bank. The back arrow, or the back gesture, steps through earlier answers to change
    them.
- Run the setup again any time from Settings › Teach Riyal.

### One account is one account

- **A bank's other numbers no longer become accounts.** Messages also quote the other
  party's account on a transfer, or a reference where the account usually goes, and
  each one used to become an account of its own: one Bank Nizwa account showed as
  seven. Riyal now follows the balance. Messages whose balances continue one another
  are one account, whatever digits they quote, and an account made from such a number
  is folded back into the real one on the next full read.
- Accounts masked with "#" ("00210######001") read their real last digits.
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
  versions.
- **Changelog** lists every release, with its notes and its APK.

### A layout that is easy to get around

- Material 3 Expressive's floating toolbar: five tabs (Home, Transactions, Analysis,
  Accounts and Settings, named on a long press) and the Add button attached at its end.
- **Pages open the way your phone opens screens.** Setup, Review, categories, updates
  and the rest are screens of their own, so your phone's own transition plays going in
  and coming back.
- A new icon: the Omani rial sign on teal.
- **Home is a dashboard:** expense, income and net for the period; one "needs your
  attention" list; your accounts and their balances; top spending; recent records.
- Records are standard list rows with circular category icons. Swiping archives, with
  undo. Removing for good is in the record's sheet, behind a question.
- Settings is grouped rows with icons; choices open radio-button dialogs.
- **No more motion for its own sake.** Cards no longer pop in, buttons no longer
  bounce, charts draw in place, and the coin mascot no longer blinks. The category wheel
  is back on Material's own wavy indicator, held flat; the bar a scan shows keeps its
  wave.

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
