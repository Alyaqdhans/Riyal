package com.alyaqdhan.riyal.ui.compose

import com.alyaqdhan.riyal.data.Txn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val monthTitleFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM uuuu")
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uu")

/**
 * One selected period of time, shared by Analysis, the budget section and the category
 * pages so "the period I'm looking at" means the same thing everywhere.
 *
 * Chevrons shift it by its own length; a slice that is exactly one calendar month keeps
 * stepping through calendar months rather than drifting by 30 days at a time.
 */
data class TimeSlice(
    val start: Long,
    val endExclusive: Long,
    val label: String,
    val month: YearMonth? = null,
) {
    val lengthDays: Long
        get() {
            val zone = ZoneId.systemDefault()
            val s = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
            val e = Instant.ofEpochMilli(endExclusive - 1).atZone(zone).toLocalDate()
            return e.toEpochDay() - s.toEpochDay() + 1
        }

    fun contains(millis: Long): Boolean = millis in start until endExclusive


    fun shifted(back: Boolean): TimeSlice {
        if (month != null) return ofMonth(if (back) month.minusMonths(1) else month.plusMonths(1))
        val zone = ZoneId.systemDefault()
        val startDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val endDay = Instant.ofEpochMilli(endExclusive - 1).atZone(zone).toLocalDate()
        val length = endDay.toEpochDay() - startDay.toEpochDay() + 1
        val shift = if (back) -length else length
        return ofDays(startDay.plusDays(shift), endDay.plusDays(shift))
    }

    companion object {
        private val zone: ZoneId get() = ZoneId.systemDefault()

        private fun dayStart(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

        fun ofMonth(m: YearMonth) = TimeSlice(
            start = dayStart(m.atDay(1)),
            endExclusive = dayStart(m.plusMonths(1).atDay(1)),
            label = m.format(monthTitleFmt),
            month = m,
        )

        fun thisMonth() = ofMonth(YearMonth.now())

        fun ofDays(startDay: LocalDate, endDay: LocalDate) = TimeSlice(
            start = dayStart(startDay),
            endExclusive = dayStart(endDay.plusDays(1)),
            label = "${dayFmt.format(startDay)} to ${dayFmt.format(endDay)}",
        )

        fun thisWeek(): TimeSlice {
            val today = LocalDate.now()
            val start = today.minusDays((today.dayOfWeek.value % 7).toLong())
            return ofDays(start, start.plusDays(6))
        }

        fun lastMonths(n: Int): TimeSlice {
            val end = YearMonth.now()
            return TimeSlice(
                start = dayStart(end.minusMonths(n - 1L).atDay(1)),
                endExclusive = dayStart(end.plusMonths(1).atDay(1)),
                label = "Last $n months",
            )
        }

        fun thisYear(): TimeSlice {
            val year = LocalDate.now().year
            return TimeSlice(
                start = dayStart(LocalDate.of(year, 1, 1)),
                endExclusive = dayStart(LocalDate.of(year + 1, 1, 1)),
                label = "$year",
            )
        }

        fun allTime(txns: List<Txn>): TimeSlice {
            val oldest = txns.minOfOrNull { it.atMillis } ?: System.currentTimeMillis()
            return TimeSlice(
                start = dayStart(Instant.ofEpochMilli(oldest).atZone(zone).toLocalDate()),
                endExclusive = dayStart(LocalDate.now().plusDays(1)),
                label = "All time",
            )
        }

        /** The date-range picker hands back UTC-midnight millis for a calendar day. */
        fun utcDay(millis: Long): LocalDate =
            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    }
}
