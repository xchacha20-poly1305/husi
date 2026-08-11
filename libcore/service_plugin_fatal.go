package libcore

// PluginFatalHandler is reverse-bound from Kotlin so a crashing plugin can stop
// the Android service. Desktop hosts use daemonhost's own fatal path instead.
type PluginFatalHandler interface {
	OnPluginFatal(message string)
}
