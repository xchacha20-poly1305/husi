//go:build windows

package daemonhost

import (
	"crypto/subtle"
	"errors"

	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/windows"
)

func VerifyCorePairSignature(shimPath string) error {
	shimSigner, err := executableAuthenticodeSigner(shimPath)
	if err != nil {
		if errors.Is(err, ErrUnsignedExecutable) {
			log.Warn("core shim is not Authenticode signed, skipping core library signature check")
			return nil
		}
		return E.Cause(err, "authenticate core shim")
	}
	libraryPath := SiblingCoreLibrary(shimPath)
	librarySigner, err := executableAuthenticodeSigner(libraryPath)
	if err != nil {
		return E.Cause(err, "authenticate core library")
	}
	if subtle.ConstantTimeCompare(shimSigner, librarySigner) != 1 {
		return E.New("core shim and core library have different signing certificates")
	}
	return nil
}

func executableAuthenticodeSigner(path string) ([]byte, error) {
	file, err := openLockedExecutable(path)
	if err != nil {
		return nil, E.Cause(err, "open ", path)
	}
	defer windows.CloseHandle(file)
	finalPath, err := finalWindowsPath(file)
	if err != nil {
		return nil, E.Cause(err, "resolve ", path)
	}
	return authenticodeSigner(finalPath, file)
}
