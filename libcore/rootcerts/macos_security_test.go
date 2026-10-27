package rootcerts

import (
	"crypto/sha1"
	"encoding/hex"
	"encoding/pem"
	"errors"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"

	aTLS "github.com/sagernet/sing-box/common/tls"

	"github.com/stretchr/testify/require"
	"howett.net/plist"
)

func macOSTrustSettingsFixture(t *testing.T, fingerprint string, format int) []byte {
	t.Helper()
	content, err := plist.Marshal(map[string]any{
		"trustVersion": 1,
		"trustList": map[string]any{
			fingerprint: map[string]any{
				"issuerName": []byte{1, 2, 3},
				"trustSettings": []any{
					map[string]any{"kSecTrustSettingsResult": 1},
					map[string]any{"kSecTrustSettingsResult": 3, "kSecTrustSettingsPolicyString": "example.com"},
				},
			},
			strings.Repeat("0", 40): map[string]any{"trustSettings": []any{}},
			strings.Repeat("1", 40): map[string]any{"trustSettings": []any{map[string]any{"kSecTrustSettingsResult": 4}}},
		},
	}, format)
	require.NoError(t, err)
	return content
}

func TestMacOSDeniedFingerprints(t *testing.T) {
	fingerprint := sha1.Sum([]byte("certificate"))
	for _, format := range []int{plist.XMLFormat, plist.BinaryFormat} {
		denied, err := macOSDeniedFingerprints(macOSTrustSettingsFixture(t, hex.EncodeToString(fingerprint[:]), format))
		require.NoError(t, err)
		require.Equal(t, map[[sha1.Size]byte]struct{}{fingerprint: {}}, denied)
	}
	for _, content := range [][]byte{
		[]byte("not a plist"),
		[]byte(`<plist version="1.0"><dict><key>trustVersion</key><integer>2</integer><key>trustList</key><dict/></dict></plist>`),
		[]byte(`<plist version="1.0"><dict><key>trustVersion</key><integer>1</integer></dict></plist>`),
		macOSTrustSettingsFixture(t, "invalid", plist.XMLFormat),
	} {
		_, err := macOSDeniedFingerprints(content)
		require.Error(t, err)
	}
}

func TestMacOSCommandRootCAs(t *testing.T) {
	_, rejected, err := aTLS.GenerateCertificate(nil, nil, time.Now, "rejected.example", time.Now().Add(time.Hour))
	require.NoError(t, err)
	_, allowed, err := aTLS.GenerateCertificate(nil, nil, time.Now, "allowed.example", time.Now().Add(time.Hour))
	require.NoError(t, err)
	block, _ := pem.Decode(rejected)
	fingerprint := sha1.Sum(block.Bytes)
	settings := macOSTrustSettingsFixture(t, strings.ToUpper(hex.EncodeToString(fingerprint[:])), plist.XMLFormat)
	for _, withUserTrust := range []bool{false, true} {
		for _, deniedDomain := range []string{"system", "admin", "user"} {
			t.Run(deniedDomain+map[bool]string{false: "/system", true: "/with-user"}[withUserTrust], func(t *testing.T) {
				roots := NewBundle()
				var domains, keychains []string
				var directory string
				err := appendMacOSCommandRootCAs(roots, withUserTrust, func(args ...string) ([]byte, error) {
					if args[0] == "trust-settings-export" {
						path := args[len(args)-1]
						directory = filepath.Dir(path)
						domain := strings.TrimSuffix(filepath.Base(path), ".plist")
						domains = append(domains, domain)
						expectedFlags := map[string]string{"system": "-s", "admin": "-d"}
						if flag, ok := expectedFlags[domain]; ok {
							require.Equal(t, flag, args[1])
						} else {
							require.Len(t, args, 2)
						}
						if domain != deniedDomain {
							return nil, &exec.ExitError{Stderr: []byte("SecTrustSettingsCreateExternalRepresentation: No Trust Settings were found.\n")}
						}
						return nil, os.WriteFile(path, settings, 0o600)
					}
					require.Equal(t, []string{"find-certificate", "-a", "-p"}, args[:3])
					keychains = append(keychains, args[3])
					return append(append([]byte{}, rejected...), allowed...), nil
				})
				require.NoError(t, err)
				require.Equal(t, []string{"system", "admin", "user"}, domains)
				expectedKeychains := []string{"/System/Library/Keychains/SystemRootCertificates.keychain"}
				if withUserTrust {
					expectedKeychains = append(expectedKeychains, "/Library/Keychains/System.keychain")
				}
				require.Equal(t, expectedKeychains, keychains)
				require.Equal(t, allowed, roots.PEM())
				assertPoolTrustsOnly(t, roots.pool, allowed)
				require.NoError(t, roots.Append(rejected))
				require.Equal(t, allowed, roots.PEM())
				_, err = os.Stat(directory)
				require.ErrorIs(t, err, os.ErrNotExist)
			})
		}
	}
}

func TestMacOSCommandRootCAsRejectsExportFailures(t *testing.T) {
	for _, failure := range []error{
		errors.New("security unavailable"),
		&exec.ExitError{Stderr: []byte("SecTrustSettingsCreateExternalRepresentation: authorization denied")},
	} {
		roots := NewBundle()
		err := appendMacOSCommandRootCAs(roots, false, func(args ...string) ([]byte, error) {
			require.Equal(t, "trust-settings-export", args[0])
			return nil, failure
		})
		require.ErrorIs(t, err, failure)
		require.Empty(t, roots.PEM())
	}
}
