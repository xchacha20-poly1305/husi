//go:build windows

package daemonhost

import (
	"strings"
	"time"
	"unsafe"

	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/windows"
)

var (
	winTrustLibrary                               = windows.NewLazySystemDLL("wintrust.dll")
	winTrustProviderDataFromStateDataProcedure    = winTrustLibrary.NewProc("WTHelperProvDataFromStateData")
	winTrustProviderSignerFromChainProcedure      = winTrustLibrary.NewProc("WTHelperGetProvSignerFromChain")
	winTrustProviderCertificateFromChainProcedure = winTrustLibrary.NewProc("WTHelperGetProvCertFromChain")
)

var ErrUnsignedExecutable = E.New("file is not Authenticode signed")

type cryptProviderCertificate struct {
	structureSize      uint32
	certificateContext *windows.CertContext
}

func authenticodeSigner(path string, file windows.Handle) ([]byte, error) {
	pathPointer, err := windows.UTF16PtrFromString(path)
	if err != nil {
		return nil, err
	}
	fileInformation := windows.WinTrustFileInfo{
		Size:     uint32(unsafe.Sizeof(windows.WinTrustFileInfo{})),
		FilePath: pathPointer,
		File:     file,
	}
	trustData := windows.WinTrustData{
		Size:                            uint32(unsafe.Sizeof(windows.WinTrustData{})),
		UIChoice:                        windows.WTD_UI_NONE,
		RevocationChecks:                windows.WTD_REVOKE_NONE,
		UnionChoice:                     windows.WTD_CHOICE_FILE,
		StateAction:                     windows.WTD_STATEACTION_VERIFY,
		FileOrCatalogOrBlobOrSgnrOrCert: unsafe.Pointer(&fileInformation),
		ProvFlags: windows.WTD_CACHE_ONLY_URL_RETRIEVAL |
			windows.WTD_REVOCATION_CHECK_NONE |
			windows.WTD_DISABLE_MD2_MD4,
		UIContext: windows.WTD_UICONTEXT_EXECUTE,
	}
	trustError := windows.WinVerifyTrustEx(windows.InvalidHWND, &windows.WINTRUST_ACTION_GENERIC_VERIFY_V2, &trustData)
	// An untrusted root is expected: husi signs with a self-signed certificate.
	untrustedChain := trustError != nil && E.IsMulti(
		trustError,
		windows.Errno(windows.CERT_E_UNTRUSTEDROOT),
		windows.Errno(windows.CERT_E_CHAINING),
	)
	if trustError != nil && !untrustedChain {
		_ = closeVerification(&trustData)
		if isMissingSignature(trustError) {
			return nil, ErrUnsignedExecutable
		}
		return nil, E.Cause(trustError, "verify Authenticode signature")
	}
	signer, signerError := verifiedSignerCertificate(trustData.StateData)
	closeError := closeVerification(&trustData)
	if signerError != nil {
		return nil, signerError
	}
	if closeError != nil {
		return nil, E.Cause(closeError, "close Authenticode verification")
	}
	certificate, err := validateCodeSigningCertificate(signer)
	if err != nil {
		return nil, err
	}
	if untrustedChain {
		err = validateUntrustedSelfSignedCertificate(certificate, time.Now())
		if err != nil {
			return nil, err
		}
	}
	return signer, nil
}

func closeVerification(trustData *windows.WinTrustData) error {
	trustData.StateAction = windows.WTD_STATEACTION_CLOSE
	return windows.WinVerifyTrustEx(windows.InvalidHWND, &windows.WINTRUST_ACTION_GENERIC_VERIFY_V2, trustData)
}

func isMissingSignature(trustError error) bool {
	return E.IsMulti(
		trustError,
		windows.Errno(windows.TRUST_E_NOSIGNATURE),
		windows.Errno(windows.TRUST_E_SUBJECT_FORM_UNKNOWN),
		windows.Errno(windows.TRUST_E_PROVIDER_UNKNOWN),
	)
}

func verifiedSignerCertificate(stateData windows.Handle) ([]byte, error) {
	providerData, _, _ := winTrustProviderDataFromStateDataProcedure.Call(uintptr(stateData))
	if providerData == 0 {
		return nil, E.New("missing Authenticode provider data")
	}
	providerSigner, _, _ := winTrustProviderSignerFromChainProcedure.Call(providerData, 0, 0, 0)
	if providerSigner == 0 {
		return nil, E.New("missing Authenticode provider signer")
	}
	providerCertificate, _, _ := winTrustProviderCertificateFromChainProcedure.Call(providerSigner, 0)
	if providerCertificate == 0 {
		return nil, E.New("missing Authenticode provider certificate")
	}
	//goland:noinspection GoVetUnsafePointer
	//nolint:govet // Win32 hands the chain back as a uintptr, there is no typed form to take.
	certificateContext := (*cryptProviderCertificate)(unsafe.Pointer(providerCertificate)).certificateContext
	if certificateContext == nil {
		return nil, E.New("empty Authenticode signer certificate context")
	}
	if certificateContext.Length == 0 || certificateContext.EncodedCert == nil {
		return nil, E.New("empty Authenticode signer certificate")
	}
	encodedCertificate := unsafe.Slice(certificateContext.EncodedCert, int(certificateContext.Length))
	return append([]byte(nil), encodedCertificate...), nil
}

func openLockedExecutable(path string) (windows.Handle, error) {
	pathPointer, err := windows.UTF16PtrFromString(path)
	if err != nil {
		return 0, err
	}
	return windows.CreateFile(
		pathPointer,
		windows.GENERIC_READ,
		windows.FILE_SHARE_READ,
		nil,
		windows.OPEN_EXISTING,
		windows.FILE_ATTRIBUTE_NORMAL|windows.FILE_FLAG_SEQUENTIAL_SCAN,
		0,
	)
}

// finalWindowsPath resolves an open handle back to a canonical path, following
// symbolic links and mount points.
func finalWindowsPath(file windows.Handle) (string, error) {
	buffer := make([]uint16, windows.MAX_LONG_PATH)
	for {
		length, err := windows.GetFinalPathNameByHandle(file, &buffer[0], uint32(len(buffer)), 0)
		if err != nil {
			return "", err
		}
		if length < uint32(len(buffer)) {
			return normalizeWindowsPath(windows.UTF16ToString(buffer[:length])), nil
		}
		buffer = make([]uint16, length+1)
	}
}

func normalizeWindowsPath(path string) string {
	const uncPrefix = `\\?\UNC\`
	if strings.HasPrefix(path, uncPrefix) {
		return `\\` + path[len(uncPrefix):]
	}
	const longPathPrefix = `\\?\`
	return strings.TrimPrefix(path, longPathPrefix)
}
