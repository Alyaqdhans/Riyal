package com.alyaqdhan.riyal.data

/**
 * Keyword → category mapping tuned for Oman (Lulu, Talabat, Omantel, OOMCO, Mwasalat…)
 * plus the common international merchants. The user's own rules always win, and every
 * match reports which pattern fired so the verbose log can explain itself.
 */
object Categorizer {

    data class Match(val categoryId: String, val pattern: String?, val source: String)

    // Order matters: earlier entries win. Keep specific words before generic ones.
    private val BUILTIN: List<Pair<String, String>> = listOf(
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
        // bills & telecom
        "omantel" to "bills", "ooredoo" to "bills", "awasr" to "bills", "vodafone" to "bills",
        "renna" to "bills", "friendi" to "bills", "recharge" to "bills", "diam" to "bills",
        "haya water" to "bills", "electricity" to "bills", "internet" to "bills",
        "utility" to "bills", "nama " to "bills", "فاتورة" to "bills",
        // shopping
        "amazon" to "shopping", "noon" to "shopping", "aliexpress" to "shopping",
        "shein" to "shopping", "temu" to "shopping", "namshi" to "shopping", "ikea" to "shopping",
        "city centre" to "shopping", "city center" to "shopping", "grand mall" to "shopping",
        "avenues" to "shopping", "zara" to "shopping", "h&m" to "shopping",
        "centrepoint" to "shopping", "sharaf dg" to "shopping", "brands for less" to "shopping",
        // health
        "pharmacy" to "health", "clinic" to "health", "hospital" to "health",
        "medical" to "health", "dental" to "health", "optic" to "health",
        "muscat pharmacy" to "health", "aster" to "health", "badr al samaa" to "health",
        "kims" to "health", "starcare" to "health", "صيدلية" to "health", "مستشفى" to "health",
        // entertainment
        "netflix" to "entertainment", "spotify" to "entertainment", "shahid" to "entertainment",
        "cinema" to "entertainment", "vox" to "entertainment", "novo" to "entertainment",
        "playstation" to "entertainment", "steam" to "entertainment", "xbox" to "entertainment",
        "game" to "entertainment", "osn" to "entertainment", "anghami" to "entertainment",
        "youtube" to "entertainment", "google play" to "entertainment", "app store" to "entertainment",
        "itunes" to "entertainment",
        // travel
        "oman air" to "travel", "salamair" to "travel", "salam air" to "travel",
        "qatar airways" to "travel", "emirates" to "travel", "flydubai" to "travel",
        "wizz" to "travel", "airline" to "travel", "hotel" to "travel",
        "booking.com" to "travel", "airbnb" to "travel", "agoda" to "travel",
        // education
        "school" to "education", "college" to "education", "university" to "education",
        "udemy" to "education", "coursera" to "education", "tuition" to "education",
        // fees
        "fee" to "fees", "fees" to "fees", "charge" to "fees", "charges" to "fees",
        "commission" to "fees", "vat" to "fees", "رسوم" to "fees",
        // cash
        "atm" to "cash", "cdm" to "cash", "cash" to "cash",
        // transfers
        "transfer" to "transfer", "remit" to "transfer", "western union" to "transfer",
        "moneygram" to "transfer", "exchange" to "transfer", "تحويل" to "transfer",
        "حوالة" to "transfer",
        // income
        "salary" to "salary", "payroll" to "salary", "wages" to "salary", "pension" to "salary",
        "راتب" to "salary", "معاش" to "salary",
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
        for (rule in rules) {
            if (contains(hay, rule.pattern.lowercase())) {
                return Match(rule.categoryId, rule.pattern, "your rule")
            }
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
