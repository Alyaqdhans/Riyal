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

- **Finds your accounts by itself.** The messages name the account and quote a
  balance, so Riyal works out what accounts you have without being told.
- **Files your spending.** Fuel, groceries, eating out, bills, rent. When it
  guesses wrong, fix it once and it remembers that shop for next time.
- **Notices transfers between your own accounts.** Moving money from savings to
  current is not spending, and counting it as both spending and income makes a
  month look wrong in two directions at once.
- **Asks when it is unsure.** A message it cannot read goes in a Review list
  with whatever it did understand. It never invents a number.
- **Budgets.** Per category, per month, with how much is left.
- **Charts.** Where the month went, and how months compare.
- **Backup.** Everything Riyal knows, written to one file you choose. You can
  leave the message text out of it if you would rather not carry that around.

## Your data stays with you

Riyal asks for two permissions, and that is the whole list:

| Permission | What for |
| --- | --- |
| `READ_SMS` | Reading the bank messages already in your inbox |
| `INTERNET` | Checking GitHub for a new version, and nothing else |

Some things worth knowing, because a privacy claim is only worth what you can
check:

- **Messages are read only when you ask.** There is no SMS receiver and no
  background work. Scanning happens when you open the app or pull to refresh.
- **Nothing about your money leaves the phone.** No records, no amounts, no
  account numbers, no message text.
- **All the network code is in one file.** It is
  [`data/Updates.kt`](app/src/main/java/com/alyaqdhan/riyal/data/Updates.kt),
  it makes one request, and that request is a GET to a public GitHub URL with
  nothing of yours in it. You can read it in a minute rather than take anyone's
  word for it.
- **Nothing installs itself.** When a new version exists Riyal shows a card.
  Downloading and installing are your decision, by hand.

## Installing it

Download the APK from [Releases](https://github.com/Alyaqdhans/Riyal/releases)
and open it. Android will ask whether to allow installing from your browser or
files app, which is normal for an app that is not on the Play Store.

Riyal tells you on the home screen when a newer version is out.

Keep installing over the top to keep your records. Uninstalling deletes them.

## How it reads a message

In order, and it stops at the first step that says no:

1. **Is this from a bank?** By default only senders whose name contains "bank",
   "بنك" or "مصرف" are read. Anything else you approve by name.
2. **Does it say money moved?** The message has to contain one of the words you
   have given it, like "debited", "purchase", "credited" or "خصم". No word, no
   record.
3. **Is it an advert?** Banks market to you from the same number they use for
   real alerts, and "win a prize worth OMR 2,250" has both a money word and an
   amount in it. Adverts are dropped.
4. **How much?** Currency tagged numbers first, and the balance the message
   quotes is deliberately not treated as the amount.
5. **Who to, and from which account?**

If a message gets most of the way through and then stops making sense, it goes
to Review rather than being thrown away or guessed at.

Every one of those decisions is written to a log you can read in Settings, so
when something is missing you can see exactly where it stopped.

## Building it

You need the Android SDK and a JDK. Then:

```bash
./gradlew assembleDebug
```

The debug build installs alongside a release copy, so you can try a change
without touching your real records.

```bash
./gradlew test
```

The message parser is plain Kotlin with no Android in it, which is why it can
be tested properly. 214 tests at the moment, and the awkward real messages that
once broke it are all in there.

## How the code is laid out

| Folder | What lives there |
| --- | --- |
| [`core/`](app/src/main/java/com/alyaqdhan/riyal/core/) | Money, dates, numerals, preferences, the log |
| [`data/`](app/src/main/java/com/alyaqdhan/riyal/data/) | Reading messages, parsing them, accounts, categories, transfers, storage |
| [`ui/`](app/src/main/java/com/alyaqdhan/riyal/ui/) | Compose screens and the bits they share |

Records are kept in one JSON file in the app's private storage. It is written
whole, to a temporary file that is then renamed, so a crash halfway through
cannot leave you with half a file.

## Releasing

See [RELEASE.md](RELEASE.md). It is a checklist, and every step on it is there
because something went wrong without it once.

## Built with

Kotlin, Jetpack Compose and Material 3. Charts by
[Vico](https://github.com/patrykandpatrick/vico). Minimum Android 10.
