package com.alyaqdhan.riyal.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alyaqdhan.riyal.RiyalApp
import com.alyaqdhan.riyal.core.Money
import com.alyaqdhan.riyal.core.Verbose
import com.alyaqdhan.riyal.data.Categories
import com.alyaqdhan.riyal.data.Direction
import com.alyaqdhan.riyal.data.ReviewItem
import com.alyaqdhan.riyal.data.ScanEngine
import com.alyaqdhan.riyal.data.ScanSummary
import com.alyaqdhan.riyal.data.Txn
import com.alyaqdhan.riyal.data.UserRule
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val riyal = app as RiyalApp
    val prefs get() = riyal.prefs
    val store get() = riyal.store

    val txns = store.txns
    val reviews = store.reviews
    val rules = store.rules
    val senders = store.senders
    val lastSummary = store.lastSummary

    val pendingReviewCount: StateFlow<Int> = store.reviews
        .map { list -> list.count { it.state == ReviewItem.STATE_PENDING } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // ─────────────────────────── scanning ───────────────────────────

    sealed interface ScanState {
        data object Idle : ScanState
        data class Running(val processed: Int, val total: Int) : ScanState
        data class Done(val summary: ScanSummary) : ScanState
        data class Failed(val message: String) : ScanState
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    val scanSheetVisible = MutableStateFlow(false)

    private val _hasSmsPermission = MutableStateFlow(false)
    val hasSmsPermission: StateFlow<Boolean> = _hasSmsPermission
    private var lastLoggedPermission: Boolean? = null

    init {
        refreshPermission()
    }

    fun refreshPermission() {
        val granted = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.READ_SMS,
        ) == PackageManager.PERMISSION_GRANTED
        if (lastLoggedPermission != granted) {
            Verbose.info(
                if (granted) "READ_SMS permission: granted — you can revoke it anytime in system settings"
                else "READ_SMS permission: not granted — the app cannot and will not read anything"
            )
            Verbose.flush()
            lastLoggedPermission = granted
        }
        _hasSmsPermission.value = granted
    }

    fun startScan() {
        if (_scanState.value is ScanState.Running) {
            scanSheetVisible.value = true
            return
        }
        refreshPermission()
        if (!_hasSmsPermission.value) {
            Verbose.fail("scan requested, but READ_SMS is not granted — nothing was read")
            Verbose.flush()
            _scanState.value = ScanState.Failed("SMS reading permission is not granted")
            scanSheetVisible.value = true
            return
        }
        _scanState.value = ScanState.Running(0, 0)
        scanSheetVisible.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val summary = ScanEngine(getApplication(), prefs, store).run { p ->
                    _scanState.value = ScanState.Running(p.processed, p.total)
                }
                prefs.lastScanAt = summary.at
                _scanState.value = ScanState.Done(summary)
            } catch (e: SecurityException) {
                Verbose.fail("scan aborted: the system refused the SMS read (permission revoked?)")
                Verbose.flush()
                _scanState.value = ScanState.Failed("The system refused the SMS read — check the permission")
            } catch (e: Exception) {
                Verbose.fail("scan failed: ${e.javaClass.simpleName}: ${e.message}")
                Verbose.flush()
                _scanState.value = ScanState.Failed(e.message ?: "Unknown error")
            }
        }
    }

    fun closeScanSheet() {
        scanSheetVisible.value = false
        if (_scanState.value !is ScanState.Running) _scanState.value = ScanState.Idle
    }

    // ─────────────────────────── user edits ───────────────────────────

    fun setCategory(txn: Txn, categoryId: String, alsoRulePattern: String?) =
        viewModelScope.launch(Dispatchers.IO) {
            store.setCategory(txn.id, categoryId)
            Verbose.ok(
                "category set by you: \"${txn.merchant ?: txn.sender}\" → ${Categories.byId(categoryId).name}"
            )
            if (!alsoRulePattern.isNullOrBlank()) {
                val changed = store.addRule(UserRule(alsoRulePattern.trim().lowercase(), categoryId))
                Verbose.ok(
                    "rule saved: \"${alsoRulePattern.trim().lowercase()}\" → ${Categories.byId(categoryId).name}" +
                        " · re-categorized $changed past transaction(s)"
                )
            }
            Verbose.flush()
        }

    fun addRule(pattern: String, categoryId: String) = viewModelScope.launch(Dispatchers.IO) {
        val changed = store.addRule(UserRule(pattern.trim().lowercase(), categoryId))
        Verbose.ok("rule saved: \"${pattern.trim().lowercase()}\" → ${Categories.byId(categoryId).name} · re-categorized $changed transaction(s)")
        Verbose.flush()
    }

    fun removeRule(pattern: String) = viewModelScope.launch(Dispatchers.IO) {
        store.removeRule(pattern)
        Verbose.info("rule removed: \"$pattern\"")
        Verbose.flush()
    }

    fun dismissReview(item: ReviewItem) = viewModelScope.launch(Dispatchers.IO) {
        store.setReviewState(item.id, ReviewItem.STATE_DISMISSED)
        Verbose.info("review item dismissed by you (${item.sender}, ${item.reason})")
        Verbose.flush()
    }

    fun undoDismissReview(item: ReviewItem) = viewModelScope.launch(Dispatchers.IO) {
        store.setReviewState(item.id, ReviewItem.STATE_PENDING)
        Verbose.info("dismiss undone — review item restored")
        Verbose.flush()
    }

    fun resolveReview(
        item: ReviewItem,
        amountMinor: Long,
        currency: String,
        direction: Direction,
        merchant: String?,
        categoryId: String,
    ) = viewModelScope.launch(Dispatchers.IO) {
        val txn = Txn(
            id = "man-${item.id}",
            atMillis = item.atMillis,
            amountMinor = amountMinor,
            currency = currency,
            direction = direction,
            merchant = merchant,
            sender = item.sender,
            body = item.body,
            categoryId = categoryId,
            categorySource = "user",
            confidence = 100,
            manual = true,
        )
        store.resolveReview(item.id, txn)
        Verbose.ok("review resolved by you: recorded ${Money.format(amountMinor, currency)} (${Categories.byId(categoryId).name})")
        Verbose.flush()
    }

    fun addManual(
        amountMinor: Long,
        currency: String,
        direction: Direction,
        merchant: String?,
        categoryId: String,
        atMillis: Long = System.currentTimeMillis(),
    ) = viewModelScope.launch(Dispatchers.IO) {
        val txn = Txn(
            id = "man-${UUID.randomUUID().toString().take(8)}",
            atMillis = atMillis,
            amountMinor = amountMinor,
            currency = currency,
            direction = direction,
            merchant = merchant,
            sender = "manual entry",
            body = "Added by you",
            categoryId = categoryId,
            categorySource = "user",
            confidence = 100,
            manual = true,
        )
        store.addManual(txn)
        Verbose.ok("manual transaction added: ${Money.format(amountMinor, currency)} (${Categories.byId(categoryId).name})")
        Verbose.flush()
    }

    fun wipeAll() = viewModelScope.launch(Dispatchers.IO) {
        store.wipe()
        prefs.wipe()
        Verbose.info("all data and settings wiped by you")
        Verbose.flush()
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val list = txns.value
            getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                w.appendLine("id,datetime,direction,amount,currency,merchant,category,sender,confidence,manual")
                val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                for (t in list) {
                    val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(t.atMillis), ZoneId.systemDefault()).format(fmt)
                    w.appendLine(
                        listOf(
                            t.id, dt, t.direction.name,
                            Money.toMajor(t.amountMinor, t.currency).toPlainString(), t.currency,
                            csv(t.merchant ?: ""), csv(Categories.byId(t.categoryId).name),
                            csv(t.sender), t.confidence.toString(), t.manual.toString(),
                        ).joinToString(",")
                    )
                }
            }
            Verbose.ok("exported ${list.size} transaction(s) to the CSV file you picked — stayed on your device")
        } catch (e: Exception) {
            Verbose.fail("CSV export failed: ${e.message}")
        }
        Verbose.flush()
    }

    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
}
