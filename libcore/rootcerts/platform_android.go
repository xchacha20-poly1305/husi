package rootcerts

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
)

// Android has no default SSL_CERT_{FILE,DIR} paths.
var certFiles, certDirectories []string

func appendPlatformRootCAs(roots *Bundle, withUserTrust bool) error {
	// Inspired by https://github.com/ExclaveNetwork/LibExclaveCore/blob/a715e817dd2cfc585163084b19fd4bd2614fe058/ca.go#L134
	// Workaround for https://github.com/golang/go/issues/71258

	removedDir := fmt.Sprintf("/data/misc/user/%d/cacerts-removed", os.Getuid()/100000)
	if err := distrustDirectory(roots, removedDir); err != nil {
		log.Warn("read user removed certificates: ", err)
	}
	paths, err := systemCertPaths(withUserTrust)
	if err != nil {
		return err
	}

	for _, path := range paths {
		if err := appendRootCAFile(roots, path); err != nil {
			log.Warn("add cert ", path, ": ", err)
		}
	}
	if roots.pem.Len() == 0 {
		return E.New("no system root certificates")
	}
	return nil
}

func systemCertPaths(withUserTrust bool) ([]string, error) {
	var paths []string

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
	paths = make([]string, 0, len(entries))
	for _, entry := range entries {
		if !entry.IsDir() {
			paths = append(paths, filepath.Join(systemDir, entry.Name()))
		}
	}

	userId := os.Getuid() / 100000
	if withUserTrust {
		userDir := fmt.Sprintf("/data/misc/user/%d/cacerts-added", userId)
		entries, err := os.ReadDir(userDir)
		if err == nil {
			for _, entry := range entries {
				if !entry.IsDir() {
					paths = append(paths, filepath.Join(userDir, entry.Name()))
				}
			}
		} else {
			log.Warn("read user added cert dir: ", err)
		}
	}

	return paths, nil
}
