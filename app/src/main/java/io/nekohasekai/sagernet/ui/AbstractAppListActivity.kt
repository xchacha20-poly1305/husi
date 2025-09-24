package io.nekohasekai.sagernet.ui

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.databinding.LayoutAppsItemBinding

internal data class ProxiedApp(
    private val appInfo: ApplicationInfo,
    val packageName: String,
    var isProxied: Boolean,
    val name: String, // cached for sorting
) {
    val uid get() = appInfo.uid

    fun loadIcon(pm: PackageManager): Drawable {
        return appInfo.loadIcon(pm)
    }
}

internal class AppsAdapter(
    val packageManager: PackageManager,
    val onItemClick: (ProxiedApp) -> Unit,
) : ListAdapter<ProxiedApp, AppViewHolder>(ProxiedAppDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, packageManager, onItemClick)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: List<Any?>) {
        var mask = 0
        for (payload in payloads) {
            mask = mask or payload as Int
        }
        if (mask == 0) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            if (mask and ProxiedAppDiffCallback.PAYLOAD_CHECKED != 0) {
                holder.isChecked = getItem(position).isProxied
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        return AppViewHolder(
            LayoutAppsItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        )
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).packageName.hashCode().toLong()
    }

}

internal class ProxiedAppDiffCallback() : DiffUtil.ItemCallback<ProxiedApp>() {
    companion object {
        const val PAYLOAD_CHECKED = 1 shl 0
    }

    override fun areItemsTheSame(old: ProxiedApp, new: ProxiedApp): Boolean {
        return old.packageName == new.packageName
    }

    override fun areContentsTheSame(old: ProxiedApp, new: ProxiedApp): Boolean {
        return old.isProxied == new.isProxied
    }

    override fun getChangePayload(old: ProxiedApp, new: ProxiedApp): Any? {
        var mask = 0
        if (old.isProxied != new.isProxied) {
            mask = mask or PAYLOAD_CHECKED
        }
        return if (mask != 0) {
            mask
        } else {
            super.getChangePayload(old, new)
        }
    }
}

internal class AppViewHolder(private val binding: LayoutAppsItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    var uid = 0

    fun bind(app: ProxiedApp, packageManager: PackageManager, onClick: (ProxiedApp) -> Unit) {
        uid = app.uid

        binding.itemicon.setImageDrawable(app.loadIcon(packageManager))
        binding.title.text = app.name
        binding.desc.text = "${app.packageName} (${uid})"
        binding.itemcheck.isChecked = app.isProxied
        binding.root.setOnClickListener {
            onClick(app)
        }
    }

    var isChecked
        get() = binding.itemcheck.isChecked
        set(value) {
            binding.itemcheck.isChecked = value
        }
}
