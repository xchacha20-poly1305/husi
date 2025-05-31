package io.nekohasekai.sagernet.ui.tools

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutNetworkBinding
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.setStatusBar
import io.nekohasekai.sagernet.ui.MainActivity
import io.nekohasekai.sagernet.ui.NamedFragment

class NetworkFragment : NamedFragment(R.layout.layout_network) {

    override fun name0() = app.getString(R.string.tools_network)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = LayoutNetworkBinding.bind(view)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
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

        binding.stunTest.setOnClickListener {
            startActivity(Intent(requireContext(), StunActivity::class.java))
        }

        binding.getCert.setOnClickListener {
            startActivity(Intent(requireContext(), GetCertActivity::class.java))
        }

        binding.scanVPN.setOnClickListener {
            startActivity(Intent(requireContext(), VPNScannerActivity::class.java))
        }

        binding.speedTest.setOnClickListener {
            startActivity(Intent(requireContext(), SpeedtestActivity::class.java))
        }

        binding.ruleSetMatch.setOnClickListener {
            startActivity(Intent(requireContext(), RuleSetMatchActivity::class.java))
        }

        if (DataStore.showBottomBar) (requireActivity() as? MainActivity)?.let {
            binding.root.setStatusBar(it.binding.fab)
        }
    }

}