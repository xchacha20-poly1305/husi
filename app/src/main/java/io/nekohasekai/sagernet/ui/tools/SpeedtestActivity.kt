package io.nekohasekai.sagernet.ui.tools

import android.app.AlertDialog
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SPEED_TEST_URL
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutSpeedTestBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.USER_AGENT
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ui.ThemedActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import libcore.CopyCallback
import libcore.HTTPResponse
import libcore.Libcore

class SpeedtestActivity : ThemedActivity() {

    private lateinit var binding: LayoutSpeedTestBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutSpeedTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setDecorFitsSystemWindowsForParticularAPIs()
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                top = bars.top,
                left = bars.left,
                right = bars.right,
            )
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainLayout) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }

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
    private var speedtestJob: Job? = null
    private var currentResponse: HTTPResponse? = null

    override fun onDestroy() {
        speedtestJob?.cancel()
        runCatching {
            currentResponse?.close()
        }.onFailure { e ->
            Logs.w(e)
        }
        DataStore.speedTestUrl = binding.speedTestServer.text.toString()
        DataStore.speedTestTimeout = binding.speedTestTimeout.text.toString().toInt()
        super.onDestroy()
    }

    override fun snackbarInternal(text: CharSequence): Snackbar {
        return Snackbar.make(binding.mainLayout, text, Snackbar.LENGTH_LONG)
    }

    private fun doTest() {
        binding.speedTest.isEnabled = false
        speedtestJob?.cancel()
        runCatching {
            currentResponse?.close()
        }
        speedtestJob = runOnDefaultDispatcher {
            try {
                Libcore.newHttpClient()
                    .apply {
                        if (DataStore.serviceState.started) {
                            useSocks5(
                                DataStore.mixedPort,
                                DataStore.inboundUsername,
                                DataStore.inboundPassword,
                            )
                        }
                    }
                    .newRequest()
                    .apply {
                        setURL(binding.speedTestServer.text.toString())
                        setUserAgent(USER_AGENT)
                        setTimeout(binding.speedTestTimeout.text.toString().toInt())
                    }
                    .execute()
                    .also {
                        currentResponse = it
                    }
                    .writeTo(Libcore.DevNull, CopyCallBack(binding.speedTestResult))
            } catch (_: CancellationException) {
                return@runOnDefaultDispatcher
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher {
                    AlertDialog.Builder(binding.root.context)
                        .setTitle(R.string.error_title)
                        .setMessage(e.toString())
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .runCatching { show() }
                }
                return@runOnDefaultDispatcher
            } finally {
                onMainDispatcher {
                    binding.speedTest.isEnabled = true
                }
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