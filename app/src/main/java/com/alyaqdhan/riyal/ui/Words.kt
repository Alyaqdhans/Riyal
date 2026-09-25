package com.alyaqdhan.riyal.ui

import android.content.Context
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.Account
import com.alyaqdhan.riyal.data.Categories

object Words {

    private val CATEGORIES: Map<String, Int> = mapOf(
        "food" to R.string.cat_food,
        "groceries" to R.string.cat_groceries,
        "transport" to R.string.cat_transport,
        "bills" to R.string.cat_bills,
        "utilities" to R.string.cat_utilities,
        "rent" to R.string.cat_rent,
        "shopping" to R.string.cat_shopping,
        "health" to R.string.cat_health,
        "entertainment" to R.string.cat_entertainment,
        "subscriptions" to R.string.cat_subscriptions,
        "travel" to R.string.cat_travel,
        "education" to R.string.cat_education,
        "personalcare" to R.string.cat_personalcare,
        "home" to R.string.cat_home,
        "insurance" to R.string.cat_insurance,
        "loan" to R.string.cat_loan,
        "charity" to R.string.cat_charity,
        "giving" to R.string.cat_giving,
        "government" to R.string.cat_government,
        "fees" to R.string.cat_fees,
        "cash" to R.string.cat_cash,
        "sending" to R.string.cat_sending,
        "transfer" to R.string.cat_transfer,
        "other" to R.string.cat_other,
        "salary" to R.string.cat_salary,
        "business" to R.string.cat_business,
        "investment" to R.string.cat_investment,
        "rental" to R.string.cat_rental,
        "reimbursement" to R.string.cat_reimbursement,
        "cashback" to R.string.cat_cashback,
        "refund" to R.string.cat_refund,
        "gift" to R.string.cat_gift,
        "borrowed" to R.string.cat_borrowed,
        "income" to R.string.cat_income,
    )

    fun apply(context: Context) {
        Categories.setNames(CATEGORIES.mapValues { (_, res) -> context.getString(res) })
        Account.genericName = context.getString(R.string.acct_generic_name)
    }
}
