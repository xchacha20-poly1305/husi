package libcore

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
)

func appendSystemRootCAs(roots *rootCABundle, withUserTrust bool) error {
	// Inspired by https://github.com/ExclaveNetwork/LibExclaveCore/blob/a715e817dd2cfc585163084b19fd4bd2614fe058/ca.go#L134
	// Workaround for https://github.com/golang/go/issues/71258

	paths, err := systemCertPaths(withUserTrust)
	if err != nil {
		return err
	}

	for name, path := range paths {
		content, err := os.ReadFile(path)
		if err != nil {
			return E.Cause(err, "load cert")
		}
		if err := roots.Append(content); err != nil {
			log.Warn("add cert ", name, ": ", err)
		}
	}
	if roots.pem.Len() == 0 {
		return E.New("no system root certificates")
	}
	return nil
}

func systemCertPaths(withUserTrust bool) (map[string]string, error) {
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

	return paths, nil
}
