package libcore

import (
	"crypto/x509"
	"fmt"
	"os"
	"path/filepath"

	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
)

func loadSystemCertWithUserTrust(sysRoot *x509.CertPool, withUserTrust bool) (*x509.CertPool, error) {
	// Inspired by https://github.com/ExclaveNetwork/LibExclaveCore/blob/a715e817dd2cfc585163084b19fd4bd2614fe058/ca.go#L134
	// Workaround for https://github.com/golang/go/issues/71258

	var paths map[string]string // name:fullPath

	systemDir := "/apex/com.android.conscrypt/cacerts" // Android 14+
	entries, err1 := os.ReadDir(systemDir)
	if err1 != nil {
		systemDir = "/system/etc/security/cacerts"
		var err2 error
		entries, err2 = os.ReadDir(systemDir)
		if err2 != nil {
			return nil, E.Cause(E.Errors(err1, err2), "read system cert dir")
		}
	}
	paths = make(map[string]string, len(entries))
	for _, entry := range entries {
		paths[entry.Name()] = filepath.Join(systemDir, entry.Name())
	}

	userId := os.Getuid() / 100000
	if withUserTrust {
		userDir := fmt.Sprintf("/data/misc/user/%d/cacerts-added", userId)
		entries, err := os.ReadDir(userDir)
		if err == nil {
			for _, entry := range entries {
				paths[entry.Name()] = filepath.Join(userDir, entry.Name())
			}
		} else {
			log.Warn("read user added cert dir: ", err)
		}
	}
	entries, err := os.ReadDir(fmt.Sprintf("/data/misc/user/%d/cacerts-removed", userId))
	if err == nil {
		for _, entry := range entries {
			delete(paths, entry.Name())
		}
	} else {
		log.Warn("read user removed cert dir: ", err)
	}

	roots := x509.NewCertPool()
	for name, path := range paths {
		content, err := os.ReadFile(path)
		if err != nil {
			return nil, E.Cause(err, "load cert")
		}
		if !tryAddCert(roots, content) {
			// SHA1WithRSA like CatCert is unsupported since Go 1.24
			log.Warn("add cert ", name, ": ", string(content))
			continue
		}
	}
	return roots, nil
}
