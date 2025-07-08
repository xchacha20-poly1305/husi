package io.nekohasekai.sagernet.widget

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.preference.Preference
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.profile.ConfigEditActivity

class EditConfigPreference : Preference {

    constructor(
        context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    init {
        intent = Intent(context, ConfigEditActivity::class.java)
    }

    override fun getSummary(): CharSequence {
        val config = DataStore.serverConfig
        return if (DataStore.serverConfig.isBlank()) {
            return context.getString(androidx.preference.R.string.not_set)
        } else {
            context.getString(R.string.lines, config.count { it == 'n' } + 1)
        }
    }

    public override fun notifyChanged() {
        super.notifyChanged()
    }

}