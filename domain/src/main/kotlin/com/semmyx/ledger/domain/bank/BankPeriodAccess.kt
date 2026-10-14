package com.semmyx.ledger.domain.bank

/** Enforced before any bank statistics query, including after entitlement expires. */
object BankPeriodAccess {
    val periods = setOf("DAY", "WEEK", "MONTH", "ALL")
    fun effective(requested: String, premium: Boolean): String =
        if (requested in periods && (requested == "DAY" || premium)) requested else "DAY"
}
