package fr.husi.bg

sealed interface ServiceAlert {
    data class Common(val message: String) : ServiceAlert
    data class MissingPlugin(val pluginName: String) : ServiceAlert
    data object NeedWifiPermission : ServiceAlert
}
