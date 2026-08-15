package fr.husi.ui

import androidx.lifecycle.ViewModel
import org.koin.core.Koin
import org.koin.core.scope.Scope

object MainScreenScope

internal class MainScreenScopeHolder(koin: Koin) : ViewModel() {

    val scope: Scope = koin.createScope<MainScreenScope>()

    override fun onCleared() {
        scope.close()
        super.onCleared()
    }
}
