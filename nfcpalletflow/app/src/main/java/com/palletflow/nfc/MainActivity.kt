package com.palletflow.nfc

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.palletflow.nfc.ui.App
import com.palletflow.nfc.ui.theme.NfcTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var adapter: NfcAdapter? = null
    private val nfcState = mutableStateOf(NfcStatus.UNKNOWN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        adapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            NfcTheme {
                App(vm, nfcState.value)
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshHistory()
        val a = adapter
        nfcState.value = when {
            a == null -> NfcStatus.MISSING
            !a.isEnabled -> NfcStatus.OFF
            else -> NfcStatus.ON
        }
        // Modo lector: la app recibe todas las etiquetas mientras está en primer plano.
        a?.takeIf { it.isEnabled }?.enableReaderMode(
            this,
            { tag -> vm.onTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
            Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) },
        )
    }

    override fun onPause() {
        runCatching { adapter?.disableReaderMode(this) }
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        tag?.let { vm.onTag(it) }
    }
}

enum class NfcStatus { UNKNOWN, ON, OFF, MISSING }
