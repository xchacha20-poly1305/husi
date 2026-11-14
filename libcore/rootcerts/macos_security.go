package rootcerts

import (
	"bytes"
	"context"
	"crypto/sha1"
	"encoding/hex"
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
	"golang.org/x/exp/collection/set"

	"howett.net/plist"
)

// Keep the command loader platform-independent so its trust filtering can be
// tested without a macOS keychain. Only darwin && !cgo selects it at runtime.
func appendMacOSCommandRootCAs(roots *Bundle, withUserTrust bool, run func(...string) ([]byte, error)) error {
	directory, err := os.MkdirTemp("", "husi-trust-settings-")
	if err != nil {
		return E.Cause(err, "create trust settings directory")
	}
	defer func() {
		if err := os.RemoveAll(directory); err != nil {
			log.Warn(E.Cause(err, "remove trust settings directory"))
		}
	}()
	denied := make(set.Set[[sha1.Size]byte])
	for _, domain := range []struct {
		name, flag string
	}{
		{"system", "-s"},
		{"admin", "-d"},
		{"user", ""},
	} {
		path := filepath.Join(directory, domain.name+".plist")
		args := []string{"trust-settings-export"}
		if domain.flag != "" {
			args = append(args, domain.flag)
		}
		args = append(args, path)
		if _, err := run(args...); err != nil {
			// security reports errSecNoTrustSettings as text and exits with status 1.
			if exitError, isExit := errors.AsType[*exec.ExitError](err); isExit && strings.Contains(string(exitError.Stderr), "SecTrustSettingsCreateExternalRepresentation: No Trust Settings were found.") {
				continue
			}
			return E.Cause(err, "export ", domain.name, " trust settings")
		}
		content, err := os.ReadFile(path)
		if err != nil {
			return E.Cause(err, "read ", domain.name, " trust settings")
		}
		fingerprints, err := macOSDeniedFingerprints(content)
		if err != nil {
			return E.Cause(err, "parse ", domain.name, " trust settings")
		}
		denied = denied.Union(fingerprints)
	}
	keychains := []string{"/System/Library/Keychains/SystemRootCertificates.keychain"}
	if withUserTrust {
		keychains = append(keychains, "/Library/Keychains/System.keychain")
	}
	for _, keychain := range keychains {
		content, err := run("find-certificate", "-a", "-p", keychain)
		if err != nil {
			return E.Cause(err, "export certificates from ", keychain)
		}
		certificates, err := splitCertificates(content)
		if err != nil {
			return E.Cause(err, "split certificates from ", keychain)
		}
		for _, der := range certificates {
			// SHA-1 is the identifier in Apple's trust-settings format, not a
			// certificate signature or the fingerprint used by Bundle.
			if denied.Contains(sha1.Sum(der)) {
				if err := roots.distrust(der); err != nil {
					return err
				}
				continue
			}
			if err := roots.Append(der); err != nil {
				log.Warn(E.Cause(err, "load certificate from ", keychain))
			}
		}
	}
	if roots.pem.Len() == 0 {
		return E.New("no certificate found")
	}
	return nil
}

func macOSDeniedFingerprints(content []byte) (set.Set[[sha1.Size]byte], error) {
	// Apple's TrustSettingsSchema.h defines the export format. Each trustList
	// key is the certificate's hexadecimal SHA-1 digest.
	var settings struct {
		Version      int `plist:"trustVersion"`
		Certificates map[string]struct {
			Settings []struct {
				Result int `plist:"kSecTrustSettingsResult"`
			} `plist:"trustSettings"`
		} `plist:"trustList"`
	}
	if _, err := plist.Unmarshal(content, &settings); err != nil {
		return nil, err
	}
	if settings.Version != 1 || settings.Certificates == nil {
		return nil, E.New("unsupported macOS trust settings format")
	}
	denied := make(set.Set[[sha1.Size]byte])
	const trustResultDeny = 3
	for fingerprint, certificate := range settings.Certificates {
		for _, setting := range certificate.Settings {
			// Match the cgo loader: any explicit deny excludes the certificate,
			// including denials constrained to a host, policy, or application.
			if setting.Result != trustResultDeny {
				continue
			}
			decoded, err := hex.DecodeString(fingerprint)
			if err != nil || len(decoded) != sha1.Size {
				return nil, E.New("invalid denied certificate fingerprint: ", fingerprint)
			}
			denied.Insert([sha1.Size]byte(decoded))
			break
		}
	}
	return denied, nil
}

func runMacOSSecurity(args ...string) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	command := exec.CommandContext(ctx, "/usr/bin/security", args...)
	command.Env = append(os.Environ(), "LC_ALL=C", "LANG=C")
	output, err := command.Output()
	if err != nil {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		if exitError, isExitError := errors.AsType[*exec.ExitError](err); isExitError {
			return nil, E.Cause(err, "security: ", string(bytes.TrimSpace(exitError.Stderr)))
		}
	}
	return output, err
}
