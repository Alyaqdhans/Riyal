# Riyal

Your bank already texts you every time money moves. Riyal reads those messages
and turns them into a record of where your money went.

It runs on your phone. Nothing is uploaded, no account is linked, and there is
no service to sign up for.

Riyal is built for Omani banks and speaks both English and Arabic.

## Why messages

Most budgeting apps want your bank login, or they want you to type every
purchase in by hand. One of those is a lot to hand over and the other never
lasts more than a week.

Your bank is already telling you everything. Riyal just reads it.

## What it does

- **Learns how your banks write.** Every bank words its messages differently,
  so Riyal doesn't rely on a list of words. It works out each kind of message
  your bank sends, and checks which way the money went against the balance the
  bank quotes. Then it asks you about a few real messages. Each answer settles
  every message written the same way, and it stops asking once its answers
  match yours.
- **Finds your accounts by itself.** The messages name the account and quote a
  balance, so Riyal works out what accounts you have. You confirm them, fix
  them, or tell it a sender isn't your bank.
- **Files your spending.** Fuel, groceries, eating out, bills, rent. When it
  guesses wrong, fix it once and it remembers that shop for next time.
- **Counts purchases abroad in rials.** A Steam game charged in dollars counts
  in your totals and your balance, converted at the fixed peg, and the record
  still shows what the bank actually charged.
- **Notices transfers between your own accounts.** Moving money from savings to
  current is not spending, and counting it as both spending and income makes a
  month look wrong in two directions at once.
- **Asks when it is unsure.** A message it cannot read goes in a Review list
  with whatever it did understand. It never invents a number.
- **Budgets, charts and backups.** Budgets per category, where the money went
  and how months compare, and everything saved to one file you choose.

Amounts are written with the new Omani rial sign, not "OMR".

## Your data stays with you

Riyal asks for three permissions, and that is the whole list:

| Permission | What for |
| --- | --- |
| `READ_SMS` | Reading the bank messages already in your inbox |
| `INTERNET` | Checking GitHub for a new version, and downloading it when you tap Update |
| `REQUEST_INSTALL_PACKAGES` | Opening Android's installer on that download |

Some things worth knowing, because a privacy claim is only worth what you can
check:

- **Messages are read only when you open the app or ask.** There is no SMS
  receiver and no background work.
- **Nothing about your money leaves the phone.** No records, no amounts, no
  account numbers, no message text.
- **All the network code is in one file.** It is
  [`data/Updates.kt`](app/src/main/java/com/alyaqdhan/riyal/data/Updates.kt).
  It asks GitHub's public API for the list of releases, and downloads an APK
  only when you tap Update. Neither request carries anything of yours. You can
  read it in a minute rather than take anyone's word for it.
- **Nothing installs itself.** Android shows its own install screen and you
  confirm it. Android also refuses an update signed with a different key than
  the copy you have.

## Installing it

Download the APK from [Releases](https://github.com/Alyaqdhans/Riyal/releases)
and open it. Android will ask whether to allow installing from your browser or
files app, which is normal for an app that is not on the Play Store.

After that, Riyal updates itself on request. When a new version is out it shows
the release notes once, and **Update** downloads it and hands it to Android to
install. Settings › Updates has the full changelog and a beta channel.

Keep installing over the top to keep your records. Uninstalling deletes them.

## How it reads a message

1. **Is this from a bank?** Senders with a bank-like name, or ones you approved
   during setup. Phone numbers, and senders you said are not your bank, are
   never read.
2. **What does it say?** The amount and its currency, the balance, the account,
   the shop or person. The quoted balance is never mistaken for the amount.
3. **Which way did the money go?** The strongest evidence wins:
   - what you taught Riyal about messages written like this one;
   - the account's balance moving by exactly the amount, which proves it
     whatever the words say;
   - the same kind of message having been proven that way before;
   - and only then the words.
4. **Which account, and which category?**

Adverts, payment requests and declined cards are dropped. A message that gets
most of the way through and then stops making sense goes to Review rather than
being guessed at.

Every one of those decisions is written to a log you can read in Settings, so
when something is missing you can see exactly where it stopped.

## Building it

You need the Android SDK and JDK 21. Then:

```bash
./gradlew assembleDebug
```

The debug build installs alongside a release copy, so you can try a change
without touching your real records.

```bash
./gradlew test
```

The reading engine is plain Kotlin with no Android in it, so it can be tested
properly: 267 tests, including the awkward real messages that once broke it.

## More

- [CHANGELOG.md](CHANGELOG.md): what changed in every version.
- [RELEASE.md](RELEASE.md): the release checklist. Every step on it is there
  because something went wrong without it once.
- [CLAUDE.md](CLAUDE.md): a map of the code and the rules this project follows.
- [docs/ux-plan.md](docs/ux-plan.md): where the interface is going.

## Built with

Kotlin, Jetpack Compose and Material 3. Charts by
[Vico](https://github.com/patrykandpatrick/vico), release notes by
[multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer).
Currency signs from Wikimedia Commons, icons from Material Symbols. Minimum
Android 10.
