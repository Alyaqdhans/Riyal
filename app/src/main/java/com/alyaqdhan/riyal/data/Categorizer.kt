package com.alyaqdhan.riyal.data

/**
 * Keyword → category mapping tuned for Oman (Lulu, Talabat, Omantel, OOMCO, Mwasalat…)
 * plus the common international merchants. The user's own rules always win, and every
 * match reports which pattern fired so the verbose log can explain itself.
 */
object Categorizer {

    data class Match(val categoryId: String, val pattern: String?, val source: String)

    // Order matters: earlier entries win. Keep specific words before generic ones.
    // Internal rather than private so a test can assert every id here is a real
    // category: [Categories.byId] answers "other" for an unknown one, so a typo
    // would quietly turn a whole group of merchants into Other.
    internal val BUILTIN: List<Pair<String, String>> = listOf(
        // groceries
        "lulu" to "groceries", "carrefour" to "groceries", "nesto" to "groceries",
        "spar" to "groceries", "al meera" to "groceries", "sultan center" to "groceries",
        "al fair" to "groceries", "safeer" to "groceries", "km trading" to "groceries",
        "mars hyper" to "groceries", "baqala" to "groceries",
        "hypermarket" to "groceries", "supermarket" to "groceries", "grocer" to "groceries",
        // food & dining
        "talabat" to "food", "mcdonald" to "food", "kfc" to "food", "burger" to "food",
        "pizza" to "food", "shawarma" to "food", "starbucks" to "food", "cafe" to "food",
        "caffe" to "food", "coffee" to "food", "restaurant" to "food", "bakery" to "food",
        "juice" to "food", "grill" to "food", "krispy" to "food", "dunkin" to "food",
        "tim hortons" to "food", "subway" to "food", "domino" to "food", "papa john" to "food",
        "hardee" to "food", "broasted" to "food", "karak" to "food", "مطعم" to "food",
        // transport & fuel
        "oomco" to "transport", "oman oil" to "transport", "al maha" to "transport",
        "shell" to "transport", "petrol" to "transport", "fuel" to "transport",
        "careem" to "transport", "otaxi" to "transport", "taxi" to "transport",
        "mwasalat" to "transport", "parking" to "transport", "وقود" to "transport",
        "بنزين" to "transport",
        // phone & internet
        "omantel" to "bills", "ooredoo" to "bills", "awasr" to "bills", "vodafone" to "bills",
        "renna" to "bills", "friendi" to "bills", "recharge" to "bills", "internet" to "bills",
        // utilities: power and water, which used to share the "bills" bucket with telecom
        "diam" to "utilities", "haya water" to "utilities", "electricity" to "utilities",
        "utility" to "utilities", "nama " to "utilities", "كهرباء" to "utilities",
        "مياه" to "utilities",
        // A bare "invoice" is only reached once every named provider above has missed,
        // so it is already a bill nothing could identify. Utilities is the larger of
        // the two buckets it could be, and either way it is a guess worth admitting.
        "فاتورة" to "utilities",
        // rent: no English keyword. "rent" is four letters, so it matches as a whole
        // word - safe from "parent" and "current", but not from "car rent", and a car
        // rental filed as housing is the wrong answer that looks right.
        "إيجار" to "rent",
        // shopping
        "amazon" to "shopping", "noon" to "shopping", "aliexpress" to "shopping",
        "shein" to "shopping", "temu" to "shopping", "namshi" to "shopping",
        "city centre" to "shopping", "city center" to "shopping", "grand mall" to "shopping",
        "avenues" to "shopping", "zara" to "shopping", "h&m" to "shopping",
        "centrepoint" to "shopping", "sharaf dg" to "shopping", "brands for less" to "shopping",
        // home & furniture
        "ikea" to "home", "furniture" to "home", "home centre" to "home",
        "home center" to "home", "ace hardware" to "home", "أثاث" to "home",
        // health
        "pharmacy" to "health", "clinic" to "health", "hospital" to "health",
        "medical" to "health", "dental" to "health", "optic" to "health",
        "muscat pharmacy" to "health", "aster" to "health", "badr al samaa" to "health",
        "kims" to "health", "starcare" to "health", "صيدلية" to "health", "مستشفى" to "health",
        // subscriptions: the streaming services only. The storefronts below stay under
        // entertainment because they charge for one-off purchases just as often.
        "netflix" to "subscriptions", "spotify" to "subscriptions", "shahid" to "subscriptions",
        "osn" to "subscriptions", "anghami" to "subscriptions",
        // entertainment
        "cinema" to "entertainment", "vox" to "entertainment", "novo" to "entertainment",
        "playstation" to "entertainment", "steam" to "entertainment", "xbox" to "entertainment",
        "game" to "entertainment",
        "youtube" to "entertainment", "google play" to "entertainment", "app store" to "entertainment",
        "itunes" to "entertainment",
        // insurance
        "insurance" to "insurance", "takaful" to "insurance", "تأمين" to "insurance",
        // charity & zakat
        "zakat" to "charity", "زكاة" to "charity", "charity" to "charity",
        "donation" to "charity", "تبرع" to "charity", "waqf" to "charity",
        // loan & credit repayment
        "installment" to "loan", "instalment" to "loan", "loan" to "loan",
        "emi" to "loan", "قرض" to "loan",
        // government: deliberately thin. The obvious keyword is "visa", which is the
        // card network on a large share of these messages, so it is left well alone.
        "municipality" to "government", "بلدية" to "government",
        // travel
        "oman air" to "travel", "salamair" to "travel", "salam air" to "travel",
        "qatar airways" to "travel", "emirates" to "travel", "flydubai" to "travel",
        "wizz" to "travel", "airline" to "travel", "hotel" to "travel",
        "booking.com" to "travel", "airbnb" to "travel", "agoda" to "travel",
        // personal care. Sits after travel on purpose: "spa" is three letters and so
        // matches as a whole word, which a hotel spa charge would otherwise satisfy
        // before "hotel" ever got a look.
        "salon" to "personalcare", "barber" to "personalcare", "spa" to "personalcare",
        "حلاق" to "personalcare",
        // education
        "school" to "education", "college" to "education", "university" to "education",
        "udemy" to "education", "coursera" to "education", "tuition" to "education",
        // fees
        "fee" to "fees", "fees" to "fees", "charge" to "fees", "charges" to "fees",
        "commission" to "fees", "vat" to "fees", "رسوم" to "fees",
        // cash
        "atm" to "cash", "cdm" to "cash", "cash" to "cash",
        // money sent to someone else: real spending, unlike a TRANSFER between the
        // user's own accounts, which is a record type and carries no category at all
        "transfer" to "sending", "remit" to "sending", "western union" to "sending",
        "moneygram" to "sending", "exchange" to "sending", "تحويل" to "sending",
        "حوالة" to "sending",
        // income
        "salary" to "salary", "payroll" to "salary", "wages" to "salary", "pension" to "salary",
        "راتب" to "salary", "معاش" to "salary",
        "cashback" to "cashback", "cash back" to "cashback", "reward points" to "cashback",
        "loyalty points" to "cashback",
        "reimburse" to "reimbursement", "expense claim" to "reimbursement",
        // The same words as the expense side above: a loan is paid out and paid back,
        // and the direction of the message is what tells the two apart. categorize
        // skips a keyword whose category sits on the other side of the ledger, so the
        // expense entries are simply passed over on an incoming message.
        "loan" to "borrowed", "قرض" to "borrowed",
        "إيجار" to "rental",
    )

    fun categorize(
        direction: Direction,
        merchant: String?,
        body: String,
        rules: List<UserRule>,
        sender: String = "",
    ): Match {
        // The sender is part of the haystack too, "Talabat" or "OmanOil" as a sender
        // name is often the only merchant signal the message carries.
        val hay = ((merchant ?: "") + " " + sender + " " + body).lowercase()
        val wantIncome = direction == Direction.INCOME
        for (rule in rules) {
            if (!contains(hay, rule.pattern.lowercase())) continue
            // A rule must match the side of the ledger it was made on, exactly as the
            // built-ins do. A counterparty can be on both sides - the same person you
            // pay can pay you - and filing the outgoing half under an expense category
            // must not drag the incoming half in with it. Nothing enforced that, and an
            // expense category on an income record is a wrong answer that looks right.
            if (Categories.byId(rule.categoryId).income != wantIncome) continue
            return Match(rule.categoryId, rule.pattern, "your rule")
        }
        for ((keyword, categoryId) in BUILTIN) {
            if (!contains(hay, keyword)) continue
            val category = Categories.byId(categoryId)
            if (category.income == (direction == Direction.INCOME)) {
                return Match(categoryId, keyword, "built-in")
            }
        }
        val fallback = if (direction == Direction.INCOME) Categories.DEFAULT_INCOME else Categories.DEFAULT_EXPENSE
        return Match(fallback, null, "default")
    }

    // Short keywords ("fee", "vat", "atm"…) must match whole words so "coffee" or
    // "private" never trip them; longer ones can match as substrings. The boundary
    // check only applies to ASCII words: \b in Java regex treats Arabic letters as
    // non-word characters, so \bراتب\b would never match, Arabic goes substring.
    private fun contains(hay: String, keyword: String): Boolean =
        if (keyword.length <= 4 && keyword.all { it in 'a'..'z' }) {
            Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(hay)
        } else {
            keyword in hay
        }
}
