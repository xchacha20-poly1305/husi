package fr.husi.ui

import kotlinx.serialization.Serializable

@Serializable
sealed class NavRoutes {

    @Serializable
    data object Configuration : NavRoutes()

    @Serializable
    data object Groups : NavRoutes()

    @Serializable
    data object Route : NavRoutes()

    @Serializable
    data object Settings : NavRoutes()

    @Serializable
    data object Plugin : NavRoutes()

    @Serializable
    data object Log : NavRoutes()

    @Serializable
    data object Dashboard : NavRoutes()

    @Serializable
    data object Tools : NavRoutes()

    @Serializable
    sealed class ToolsPage : NavRoutes() {
        @Serializable
        data object Stun : ToolsPage()

        @Serializable
        data object GetCert : ToolsPage()

        @Serializable
        data object VPNScanner : ToolsPage()

        @Serializable
        data object SpeedTest : ToolsPage()

        @Serializable
        data object RuleSetMatch : ToolsPage()

    }

    @Serializable
    data object About : NavRoutes()

    @Serializable
    data object Libraries : NavRoutes()

    @Serializable
    data class ProfileEditor(
        val type: Int,
        val id: Long = -1L,
        val subscription: Boolean = false,
    ) : NavRoutes()

    @Serializable
    data class ConnectionsDetail(
        val uuid: String,
    ) : NavRoutes()

    @Serializable
    data object AppManager : NavRoutes()

    @Serializable
    data object Assets : NavRoutes()

    @Serializable
    data object AppList : NavRoutes()

    @Serializable
    data object ConfigEditor : NavRoutes()

    @Serializable
    data class AssetEdit(
        val assetName: String = "",
    ) : NavRoutes()

    @Serializable
    data class GroupSettings(
        val groupId: Long = 0L,
    ) : NavRoutes()

    @Serializable
    data class RouteSettings(
        val routeId: Long = -1L,
        val useDraft: Boolean = false,
    ) : NavRoutes()
}
