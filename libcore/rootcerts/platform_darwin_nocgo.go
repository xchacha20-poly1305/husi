//go:build darwin && !cgo

package rootcerts

// macOS has no default SSL_CERT_{FILE,DIR} paths.
var certFiles, certDirectories []string

func appendPlatformRootCAs(roots *Bundle, withUserTrust bool) error {
	return appendMacOSCommandRootCAs(roots, withUserTrust, runMacOSSecurity)
}
