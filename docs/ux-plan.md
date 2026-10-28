# UX overhaul plan

Riyal worked, but it was complicated to use. This plan says what was wrong, what the
best finance apps do instead, what 1.8.0 changed, and what comes next.

## What was wrong (1.7.1)

- **Getting around.** Four unlabelled icons sat in a floating pill, with Add as a
  separate island. Accounts were three taps deep, inside Settings.
- **Home.** A mascot face and a stack of differently coloured banners competed for
  attention, while the numbers people open the app for were small.
- **Motion.** Pages faded and slid, cards popped in on springs, buttons bounced, and the
  mascot blinked. Every tap waited on an animation.
- **Hand-made parts.** Rows, settings lines, the swipe gesture and the donut were all
  drawn from scratch, so nothing behaved quite like the rest of the phone.
- **Rules the user had to keep up.** Settings held lists of keywords and senders that
  decided how messages were read. Only someone who understood the parser could fix a
  misread.
- **Currency.** Every amount said "OMR". Purchases abroad were left in dollars and out
  of every total.

## What top finance apps do

These are the patterns shared by MyMoney (Raha) and other well-reviewed trackers:

- **Five bottom tabs, labelled:** records, analysis, budgets or accounts, and settings.
  A + button adds a record.
- **A period header** (this month, with arrows), then **expense / income / balance**
  side by side.
- **Records grouped by day**, each row a coloured category icon, the name, the account,
  and the amount coloured by direction.
- **Accounts as a first-class list** of wallets, cards and savings, each with its
  balance, plus a total.
- **Analysis** as a category donut with a legend of amounts and shares, and bar or line
  charts of income against expense.
- **Budgets** as progress bars per category.
- **Settings** as a plain grouped list: currency (with symbol or flag), backup, theme,
  language.
- **Quiet motion:** standard transitions, no decoration.

## What 1.8.0 did

| Area | Change |
| --- | --- |
| Navigation | Material 3 `ShortNavigationBar`: Home, Transactions, Analysis, Accounts, Settings, labelled, with badges. Add is a FAB on Home and Transactions. |
| Home | Period, then expense/income/net; one "needs your attention" list; accounts with balances; top spending; recent records by day. |
| Rows | Stock `ListItem` with circular category icons; native `SwipeToDismissBox` archives; removal moved to the record sheet behind a confirmation. |
| Settings | Grouped `ListItem` rows with icons; toggles on the whole row; radio-button dialogs; Updates as a page of its own. |
| Motion | No fragment transitions, no pop-ins or bounces, `MotionScheme.standard()`, charts drawn in place. Wavy indicators kept as components but flat. |
| Currency | The new Omani rial sign (and other signs) in place of codes; a flag picker; purchases abroad converted into rials with the original kept. |
| Reading | Fixed keyword and sender rules replaced by a setup that learns each bank's templates from balances and a few yes/no answers. |

## Next

1. **Add-record screen.** A full screen in place of the dialog: a large amount field,
   type tabs (expense, income, transfer), account and category pickers with icons, date
   and time pickers (M3 `DatePicker` / `TimePicker`), and a note.
2. **Transactions.** A search field (M3 `SearchBar`), filter chips for type and account
   with a sheet for categories, and a day total on each day header.
3. **Calendar view.** A month grid with each day's net, for "where did that week go".
4. **Analysis.** Expense/income tabs on the wheel; a month-by-month bar chart (Vico)
   with the selected month highlighted; tap a category for its trend.
5. **Remaining pushed pages on `ListItem`:** Categories, Needs category, Review and
   Category detail still mix custom rows with stock ones.
6. **Accounts.** Per-account detail with its records and a balance line chart; account
   colours chosen in the editor.
7. **Home widget.** This month's net and an Add button (Glance).
8. **Theming.** A theme setting (system, light, dark), and the brand colour as the
   fallback scheme where dynamic colour is unavailable.
9. **Accessibility.** Content descriptions for every icon-only control, and checks with
   TalkBack and at the largest font scale.

Each of these should use stock Material 3 components and keep to the no-decorative-motion
rule; see [CLAUDE.md](../CLAUDE.md).
