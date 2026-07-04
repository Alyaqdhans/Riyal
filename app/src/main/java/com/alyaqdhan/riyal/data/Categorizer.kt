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
        "hypermarket" to "groceries", "supermarket" to "groceries", "grocer" to "groceries",
        // food & dining
        "talabat" to "food", "mcdonald" to "food", "kfc" to "food", "burger" to "food",
        "pizza" to "food", "shawarma" to "food", "starbucks" to "food", "cafe" to "food",
        "caffe" to "food", "coffee" to "food", "restaurant" to "food", "bakery" to "food",
        "juice" to "food", "grill" to "food",
        // transport & fuel
        "oomco" to "transport", "oman oil" to "transport", "al maha" to "transport",
        "shell" to "transport", "petrol" to "transport", "fuel" to "transport",
        "careem" to "transport", "otaxi" to "transport", "taxi" to "transport",
        "mwasalat" to "transport", "parking" to "transport",
        // bills & telecom
        "omantel" to "bills", "ooredoo" to "bills", "awasr" to "bills", "vodafone" to "bills",
        "renna" to "bills", "friendi" to "bills", "recharge" to "bills", "diam" to "bills",
        "electricity" to "bills", "internet" to "bills", "utility" to "bills", "nama " to "bills",
        // shopping
        "amazon" to "shopping", "noon" to "shopping", "aliexpress" to "shopping",
        "shein" to "shopping", "namshi" to "shopping", "ikea" to "shopping",
        "city centre" to "shopping", "city center" to "shopping", "grand mall" to "shopping",
        "avenues" to "shopping", "zara" to "shopping", "h&m" to "shopping",
        "centrepoint" to "shopping", "sharaf dg" to "shopping",
        // health
        "pharmacy" to "health", "clinic" to "health", "hospital" to "health",
        "medical" to "health", "dental" to "health", "optic" to "health",
        // entertainment
        "netflix" to "entertainment", "spotify" to "entertainment", "shahid" to "entertainment",
        "cinema" to "entertainment", "vox" to "entertainment", "playstation" to "entertainment",
        "steam" to "entertainment", "xbox" to "entertainment", "game" to "entertainment",
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
        "commission" to "fees", "vat" to "fees",
        // cash
        "atm" to "cash", "cash" to "cash",
        // transfers
        "transfer" to "transfer", "remit" to "transfer", "western union" to "transfer",
        "moneygram" to "transfer", "exchange" to "transfer",
        // income
        "salary" to "salary", "payroll" to "salary", "wages" to "salary", "pension" to "salary",
    )

    fun categorize(
        direction: Direction,
        merchant: String?,
        body: String,
        rules: List<UserRule>,
    ): Match {
        val hay = ((merchant ?: "") + " " + body).lowercase()
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
    // "private" never trip them; longer ones can match as substrings.
    private fun contains(hay: String, keyword: String): Boolean =
        if (keyword.length <= 4) {
            Regex("\\b${Regex.escape(keyword)}\\b").containsMatchIn(hay)
        } else {
            keyword in hay
        }
}
