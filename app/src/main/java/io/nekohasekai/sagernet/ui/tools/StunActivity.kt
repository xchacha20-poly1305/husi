package io.nekohasekai.sagernet.ui.tools

import android.os.Bundle
import androidx.core.view.isVisible
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.databinding.LayoutStunBinding
import io.nekohasekai.sagernet.ktx.currentSocks5
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ui.ThemedActivity
import libcore.Libcore

class StunActivity : ThemedActivity() {

    companion object {
        const val STUN_SOFTWARE_NAME = "husi ${BuildConfig.VERSION_NAME}"
    }

    private lateinit var binding: LayoutStunBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutStunBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.stun_test)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.baseline_arrow_back_24)
        }
        currentSocks5()?.string?.let {
            binding.proxyServer.setText(it)
        }
        binding.stunTest.setOnClickListener {
            SagerNet.inputMethod.hideSoftInputFromWindow(binding.root.windowToken, 0)
            doTest()
        }
    }

    private fun doTest() {
        binding.waitLayout.isVisible = true
        val server = binding.natStunServer.text.toString()
        val proxy = binding.proxyServer.text.toString()
        runOnDefaultDispatcher {
            val result = Libcore.stunTest(server, proxy, STUN_SOFTWARE_NAME)
            onMainDispatcher {
                binding.waitLayout.isVisible = false
                binding.natResult.text = result
            }
        }
    }

}