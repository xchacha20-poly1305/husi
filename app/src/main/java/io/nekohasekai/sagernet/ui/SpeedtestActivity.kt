package io.nekohasekai.sagernet.ui

import android.app.AlertDialog
import android.os.Bundle
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SPEED_TEST_URL
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutSpeedTestBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import libcore.CopyCallback
import libcore.Libcore

class SpeedtestActivity : ThemedActivity() {

    private lateinit var binding: LayoutSpeedTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutSpeedTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.speed_test)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }

        binding.speedTestServer.setText(DataStore.speedTestUrl.ifBlank {
            SPEED_TEST_URL
        })
        binding.speedTestTimeout.setText(DataStore.speedTestTimeout.toString())

        binding.speedTest.setOnClickListener {
            doTest()
        }
    }

    // Make sure just one job.
    // FIXME real interrupt
    private var speedtestJob: Job? = null

    override fun onDestroy() {
        speedtestJob?.cancel()
        DataStore.speedTestUrl = binding.speedTestServer.text.toString()
        DataStore.speedTestTimeout = binding.speedTestTimeout.text.toString().toInt()
        super.onDestroy()
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.mainLayout, text, Snackbar.LENGTH_LONG)
    }

    private fun doTest() {
        speedtestJob?.cancel()
        speedtestJob = runOnDefaultDispatcher {
            try {
                Libcore.newHttpClient().apply {
                    trySocks5(
                        DataStore.mixedPort,
                        DataStore.inboundUsername,
                        DataStore.inboundPassword,
                    )
                }.newRequest().apply {
                    setURL(binding.speedTestServer.text.toString())
                    setTimeout(binding.speedTestTimeout.text.toString().toInt())
                }.execute()
                    .writeTo(Libcore.DevNull, CopyCallBack(binding.speedTestResult))
            } catch (_: CancellationException) {
                return@runOnDefaultDispatcher
            } catch (e: Exception) {
                Logs.w(e)
                runOnMainDispatcher {
                    AlertDialog.Builder(binding.root.context)
                        .setTitle(R.string.error_title)
                        .setMessage(e.toString())
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .runCatching { show() }
                }
                return@runOnDefaultDispatcher
            }
            snackbar(R.string.done).show()
        }
    }

    inner class CopyCallBack(val text: TextView) : CopyCallback {

        val start = System.nanoTime()

        private var saved: Long = 0L

        override fun setLength(length: Long) {}

        override fun update(n: Long) {
            saved += n

            val duration = (System.nanoTime() - start) / 1_000_000_000.0
            runOnMainDispatcher {
                val savedDouble = saved.toDouble()
                text.text = getString(
                    R.string.speed,
                    Libcore.formatBytes((savedDouble / duration).toLong()),
                )
            }
        }
    }
}