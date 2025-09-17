package io.nekohasekai.sagernet.ui

import android.view.KeyEvent
import androidx.fragment.app.Fragment

open class OnKeyDownFragment : Fragment {

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    open fun onKeyDown(ketCode: Int, event: KeyEvent) = false
}