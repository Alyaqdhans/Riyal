<p align="center">
  <img src="docs/images/icon.svg" width="112" height="112" alt="">
</p>

<h1 align="center">Riyal</h1>

<p align="center">
  An expense tracker for Oman that builds itself from the bank messages already on your phone.<br>
  No bank login, no typing, nothing uploaded. In English and Arabic.
</p>

<p align="center">
  <a href="https://github.com/Alyaqdhans/Riyal/releases/latest"><img src="https://img.shields.io/github/v/release/Alyaqdhans/Riyal?label=download&color=00897B&cacheSeconds=600" alt="Latest release"></a>
  <a href="https://github.com/Alyaqdhans/Riyal/releases"><img src="https://img.shields.io/github/downloads/Alyaqdhans/Riyal/total?color=004D47" alt="Downloads"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white" alt="Android 10 and later">
  <img src="https://img.shields.io/badge/language-English%20%7C%20Arabic-00897B" alt="English and Arabic">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin and Jetpack Compose">
</p>

Your bank already texts you every time money moves. Riyal reads those messages, works out
which accounts you have, and turns them into a record of where your money went: what came in,
what went out, and where it was spent. Everything happens on your phone.

## Download

Get the latest `Riyal-vX.Y.Z.apk` from [Releases](https://github.com/Alyaqdhans/Riyal/releases/latest).
It runs on Android 10 and later.

- **Installing:** open the APK. Android asks whether your browser or files app may install apps,
  which is normal for an app that is not on the Play Store.
- **Updating:** Riyal checks GitHub for a new version each time it opens and shows what changed,
  in your language. **Update** downloads it and hands it to Android's installer. Settings ›
  Updates has the full changelog and a beta channel.
- **Keeping your records:** install each version over the last. Your records live only on the
  phone, so uninstalling deletes them; make a backup first (Settings › Backup).

## Features

### Reading your messages

- **Learns how each bank writes.** Banks word their messages differently, so Riyal does not rely
  on a word list. It works out each kind of message a bank sends, checks which way the money went
  against the balance the bank quotes, and asks you about a few real messages. One answer settles
  every message written the same way, and it stops asking once its answers match yours.
- **Keeps learning.** New wording is read from what your own settled messages taught it, and a
  wallet or card whose name looks like no bank's is found by its balance and offered to you.
- **Finds your accounts.** The messages name the account and quote a balance, so the accounts are
  worked out for you to confirm, fix, or tell it a sender is not your bank.
- **Asks when unsure.** A message it cannot read goes to Needs review with whatever it did
  understand. Show it once which figure is the amount, which is the balance and who was paid,
  and every message like it is read that way. It never invents a number.
- **Leaves out what is not money.** Adverts, payment requests, declined cards and card checks of
  nothing are dropped, and amounts under a riyal (".500") or in Arabic digits read correctly.

### Your money

- **Home:** the period's net, money in and out, what is left of the budget, top spending, recent
  records.
- **Transactions:** search by place, category, account or amount, filter, sort, and swipe a record
  to archive it (an archived record counts towards nothing) or remove it.
- **Analysis:** where the money went by category, how the period compares with the one before,
  trends and cash flow, and a Places tab with spending by shop, where branches of one chain can be
  merged into one place.
- **Categories** filled in for you; correct one and Riyal remembers that shop.
- **Budgets** per category, for any period.
- **Transfers between your own accounts** are paired, so moving money is not counted as both
  spending and income.
- **Purchases abroad** are counted in rials, at the fixed peg for the Gulf currencies and the
  dollar and at rates you can correct for the rest, and keep what the bank actually charged.

### Yours to keep

- **Backups** to a file you choose, by parts (records and accounts, categories, learned categories,
  lists, budgets), with message text optional; and a CSV export.
- **A log** of every decision a scan made, so you can see exactly why a message was read
  the way it was.
- **English and Arabic** with a full right-to-left layout, light and dark themes, Material 3
  Expressive, and amounts written with the new Omani rial sign.

<details dir="rtl">
<summary><b>بالعربي</b></summary>

**ريال** تطبيق لتتبّع المصاريف في عُمان، يبني سجلاتك من رسائل البنك الموجودة أصلاً في هاتفك: بلا ربط حساب بنكي، وبلا إدخال يدوي، ولا يخرج شيء من هاتفك.

- **يتعلّم طريقة كتابة بنكك:** يتعرّف على أنواع رسائله، ويتحقّق من اتجاه المال من الرصيد الذي يذكره البنك، ويسألك عن بضع رسائل فقط. إجابة واحدة تكفي لكل رسالة مكتوبة بالطريقة نفسها.
- **يجد حساباتك بنفسه،** ويتعرّف على المحافظ والبطاقات التي لا تشبه أسماؤها أسماء البنوك من رصيدها، ثم يسألك عنها.
- **يصنّف مصاريفك** ويتذكّر المتجر إذا صحّحته مرة واحدة.
- **تحليل وميزانيات:** إلى أين ذهب المال، والإنفاق حسب المتجر، وميزانية لكل فئة.
- **التحويلات بين حساباتك** لا تُحسب مصروفاً ودخلاً معاً، والمشتريات بعملة أخرى تُحسب بالريال.
- **خصوصيتك:** تُقرأ الرسائل عند فتح التطبيق أو عند طلبك فقط، والاتصال الوحيد بالإنترنت للتحقّق من التحديثات على GitHub.
- **واجهة عربية كاملة** من اليمين لليسار، بوضع فاتح وداكن، ورمز الريال العُماني الجديد.

نزّله من صفحة [الإصدارات](https://github.com/Alyaqdhans/Riyal/releases/latest). يعمل على أندرويد 10 وما بعده.

</details>

## Privacy

Riyal asks for three permissions, and that is the whole list:

| Permission | What for |
|---|---|
| `READ_SMS` | Reading the bank messages already in your inbox |
| `INTERNET` | Asking GitHub for a new version, and downloading it when you tap Update |
| `REQUEST_INSTALL_PACKAGES` | Handing that download to Android's installer |

A privacy claim is only worth what you can check, so here is where to check each one:

| What Riyal promises | Where it shows |
|---|---|
| Messages are read only when you open the app or ask, never in the background | There is no SMS receiver and no background job in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml); the one inbox query is in [`SmsReader.kt`](app/src/main/java/com/alyaqdhan/riyal/data/SmsReader.kt) |
| Nothing about your money leaves the phone: no records, amounts, account numbers or message text | All the network code is in [`Updates.kt`](app/src/main/java/com/alyaqdhan/riyal/data/Updates.kt): the public list of releases, and the APK when you tap Update. Neither request carries anything of yours |
| Only your banks are read | Phone numbers never are. A sender that does not look like a bank is looked at only for whether it holds money, and nothing from it is recorded until you say so |
| Android's own cloud backup does not copy your data | `allowBackup="false"`, with [backup](app/src/main/res/xml/backup_rules.xml) and [data extraction](app/src/main/res/xml/data_extraction_rules.xml) rules that leave everything out |
| Nothing installs itself | Android shows its own install screen for you to confirm, and refuses an update signed with a different key than the copy you have |

## How it reads a message

1. **Is it from a bank?** Senders named like a bank, ones that hold one of your accounts, and ones
   you added. Phone numbers and senders you ignored are never read.
2. **What does it say?** The amount and its currency, the balance, the account, and the shop or
   person, or wherever this kind of message keeps them, if you showed Riyal or the balances
   worked it out. The quoted balance is never mistaken for the amount.
3. **Which way did the money go?** The strongest evidence wins:
   - what you taught Riyal about messages written like this one;
   - the account's balance moving by exactly the amount, which proves it whatever the words say;
   - this kind of message having been proven that way before;
   - the words, the built-in ones first and then the ones learned from your own messages.
4. **Which account, and which category?**

A message that gets most of the way and then stops making sense goes to Needs review rather than
being guessed at. Every decision is written to Settings › Verbose log.

## Built with

- [Kotlin](https://kotlinlang.org) 2.4 and [Jetpack Compose](https://developer.android.com/compose)
  with [Material 3](https://m3.material.io) 1.5 (Expressive)
- [Vico](https://github.com/patrykandpatrick/vico) 3.3 for the charts
- [Markwon](https://github.com/noties/Markwon) 4.6 for the release notes
- [LazyColumnScrollbar](https://github.com/nanihadesuka/LazyColumnScrollbar) 3.0 for the scroll thumb
- [Noto Sans](https://fonts.google.com/noto/specimen/Noto+Sans) and
  [Noto Sans Arabic UI](https://fonts.google.com/noto/specimen/Noto+Sans+Arabic) for the type
- Currency signs from public-domain path data on Wikimedia Commons, icons from
  [Material Symbols](https://fonts.google.com/icons)
- Android Gradle Plugin 9.5 and Gradle 9.8; targets Android 17, runs on Android 10 and later

## Project layout

```
app/src/main/java/com/alyaqdhan/riyal/
  core/                 money in baisa, currencies and their signs, preferences, the activity log
  data/
    SmsReader.kt        the one inbox query
    SmsParser.kt        amount, currency, balance, account and shop out of one message
    Learning.kt         kinds of message, balance proofs, the setup's questions
    WordLearner.kt      which words mean money out or in, learned from settled messages
    TemplateFields.kt   where a kind of message keeps its amount, balance and shop
    MoneySenders.kt     wallets and cards found by what they send
    AccountDiscovery.kt banks, and the accounts their messages describe
    TransferMatcher.kt  money moved between your own accounts
    ScanEngine.kt       one pass over the inbox: read, decide, accounts, records, transfers
    Store.kt            everything Riyal keeps, in one JSON file written atomically
    Updates.kt          all of the network code
  ui/                   the view model, the five tabs and every page
app/src/test/           tests for the reading engine, with made-up messages
docs/                   design notes and the README's images
```

## Building

You need Android Studio (or JDK 21) and the Android SDK for API 37.

```bash
./gradlew :app:assembleDebug        # a debug build, which installs beside the release copy
./gradlew :app:testDebugUnitTest    # the reading engine's tests, about 400 of them
./gradlew :app:lintDebug            # Android lint, kept at zero issues
```

The debug build has its own application id, so you can try a change without touching your real
records. The reading engine is plain Kotlin, so its tests run without a phone, including the
awkward messages that once broke it, rewritten with made-up names and figures. The release
checklist, signing included, is in [RELEASE.md](RELEASE.md).

## Contributing

The most useful report is a message that was read wrong. The repository is public, so before
sharing one, replace names, amounts, account digits and references with made-up ones in the
same shape: "OMR 10.000 at Example Cafe", "a/c ####1234". Riyal's log (Settings › Verbose
log) shows where reading stopped, which says more than a screenshot.

- [CHANGELOG.md](CHANGELOG.md): what changed in every version
- [RELEASE.md](RELEASE.md): how a release is made
- [CLAUDE.md](CLAUDE.md): a map of the code and the rules the project follows
- [docs/ux-plan.md](docs/ux-plan.md): where the interface is going
