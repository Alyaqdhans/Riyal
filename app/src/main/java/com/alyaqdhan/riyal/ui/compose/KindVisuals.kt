package com.alyaqdhan.riyal.ui.compose

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.alyaqdhan.riyal.R
import com.alyaqdhan.riyal.data.AccountKind
import com.alyaqdhan.riyal.data.MessageKind

/** How each sort of account is drawn and named. */
@DrawableRes
fun iconFor(kind: AccountKind): Int = when (kind) {
    AccountKind.CURRENT -> R.drawable.ic_sym_account_balance
    AccountKind.SAVINGS -> R.drawable.ic_sym_savings
    AccountKind.CARD -> R.drawable.ic_sym_credit_card
    AccountKind.WALLET -> R.drawable.ic_sym_wallet
}

@StringRes
fun labelFor(kind: AccountKind): Int = when (kind) {
    AccountKind.CURRENT -> R.string.acct_kind_current
    AccountKind.SAVINGS -> R.string.acct_kind_savings
    AccountKind.CARD -> R.string.acct_kind_card
    AccountKind.WALLET -> R.string.acct_kind_wallet
}

/** How each kind of message is drawn and named where the user is asked about one. */
@DrawableRes
fun iconFor(kind: MessageKind): Int = when (kind) {
    MessageKind.OUT -> R.drawable.ic_sym_arrow_up
    MessageKind.IN -> R.drawable.ic_sym_arrow_down
    MessageKind.TRANSFER -> R.drawable.ic_sym_swap
    MessageKind.NONE -> R.drawable.ic_sym_block
}

@StringRes
fun labelFor(kind: MessageKind): Int = when (kind) {
    MessageKind.OUT -> R.string.label_expense
    MessageKind.IN -> R.string.label_income
    MessageKind.TRANSFER -> R.string.setup_kind_transfer
    MessageKind.NONE -> R.string.setup_kind_none
}
