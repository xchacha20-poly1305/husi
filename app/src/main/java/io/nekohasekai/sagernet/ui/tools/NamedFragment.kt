package io.nekohasekai.sagernet.ui.tools

import android.content.Context
import androidx.fragment.app.Fragment

abstract class NamedFragment : Fragment {

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    abstract fun getName(context: Context): String
}