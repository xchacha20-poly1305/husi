package io.nekohasekai.sagernet.ui

import android.os.Bundle
import androidx.core.view.isVisible
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutStunBinding
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import libcore.Libcore

class StunActivity : ThemedActivity() {

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
        binding.stunTest.setOnClickListener {
            doTest()
        }
    }

    private fun doTest() {
        binding.waitLayout.isVisible = true
        runOnDefaultDispatcher {
            val result = Libcore.stunTest(binding.natStunServer.text.toString())
            onMainDispatcher {
                binding.waitLayout.isVisible = false
                binding.natResult.text = result
            }
        }
    }

}