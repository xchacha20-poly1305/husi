//go:build linux

package daemonhost

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	E "github.com/sagernet/sing/common/exceptions"
)

const (
	serviceUnitName   = "husi-daemon.service"
	serviceUnitPath   = "/etc/systemd/system/" + serviceUnitName
	defaultInstallBin = "/usr/local/libexec/husi/husi-core"
)

func ServiceInstall(workingDir string) error {
	if os.Geteuid() != 0 {
		return E.New("service install requires root")
	}
	if workingDir == "" {
		workingDir = DefaultWorkingDir()
	}
	absDir, err := filepath.Abs(workingDir)
	if err != nil {
		return E.Cause(err, "resolve working directory")
	}
	if err := os.MkdirAll(absDir, 0o700); err != nil {
		return E.Cause(err, "create working directory")
	}

	executablePath, err := os.Executable()
	if err != nil {
		return E.Cause(err, "get executable path")
	}
	srcShim, srcLib, err := resolvePairSources(executablePath)
	if err != nil {
		return err
	}
	installBin, useInPlace, err := resolveInstallBinary(executablePath)
	if err != nil {
		return err
	}
	if err := prepareInstallDirectory(installBin); err != nil {
		return err
	}
	if useInPlace {
		// Package-managed layout: both files already live in a protected dir.
		if err := ensurePairPresent(installBin); err != nil {
			return err
		}
	} else {
		// stop → replace pair (library first) → start (restart below).
		if err := installPair(srcShim, srcLib, installBin, func() error {
			return runSystemctl("stop", serviceUnitName)
		}); err != nil {
			return err
		}
	}

	socketPath := DefaultSocketPath()
	unit := fmt.Sprintf(`[Unit]
Description=Husi Core Daemon
After=network.target

[Service]
Type=simple
ExecStart=%s run --dir %s --socket %s
Restart=on-failure
RestartSec=5
# Create /run/husi for the socket when RuntimeDirectory is available.
RuntimeDirectory=husi
RuntimeDirectoryMode=0755
StateDirectory=husi

[Install]
WantedBy=multi-user.target
`, installBin, absDir, socketPath)

	if err := os.WriteFile(serviceUnitPath, []byte(unit), 0o644); err != nil {
		return E.Cause(err, "write systemd unit")
	}
	if err := runSystemctl("daemon-reload"); err != nil {
		return err
	}
	if err := runSystemctl("enable", serviceUnitName); err != nil {
		return err
	}
	// Idempotent upgrade path: restart picks up the new pair.
	if err := runSystemctl("restart", serviceUnitName); err != nil {
		return err
	}
	return nil
}

func ServiceUninstall(workingDir string, purge bool) error {
	if os.Geteuid() != 0 {
		return E.New("service uninstall requires root")
	}
	_ = runSystemctl("disable", "--now", serviceUnitName)
	_ = os.Remove(serviceUnitPath)
	_ = runSystemctl("daemon-reload")

	// Only the portable copy is ours to delete. Package-managed pairs
	// (deb/rpm/pacman) stay in place for the package manager.
	if err := removePair(defaultInstallBin); err != nil {
		return err
	}
	_ = os.Remove(filepath.Dir(defaultInstallBin))
	_ = os.Remove(DefaultSocketPath())

	if purge {
		if workingDir == "" {
			workingDir = DefaultWorkingDir()
		}
		if err := os.RemoveAll(workingDir); err != nil {
			return E.Cause(err, "purge working directory")
		}
	}
	return nil
}

func ServiceStart() error {
	return runSystemctl("start", serviceUnitName)
}

func ServiceStop() error {
	installed, err := serviceInstalled()
	if err != nil {
		return err
	}
	if !installed {
		return nil
	}
	return runSystemctl("stop", serviceUnitName)
}

func ServiceStatus() (*ServiceStatusResult, error) {
	installed, err := serviceInstalled()
	if err != nil {
		return nil, err
	}
	if !installed {
		return &ServiceStatusResult{ExitCode: 3, Description: "not installed"}, nil
	}
	activeState, err := systemctlProperty("ActiveState")
	if err != nil {
		return nil, err
	}
	if activeState == "active" {
		return &ServiceStatusResult{ExitCode: 0, Description: "running"}, nil
	}
	return &ServiceStatusResult{ExitCode: 2, Description: "stopped"}, nil
}

func serviceInstalled() (bool, error) {
	loadState, err := systemctlProperty("LoadState")
	if err != nil {
		return false, err
	}
	return loadState != "" && loadState != "not-found", nil
}

func systemctlProperty(property string) (string, error) {
	output, err := exec.Command("systemctl", "show", "--property="+property, "--value", serviceUnitName).CombinedOutput()
	if err != nil {
		message := strings.TrimSpace(string(output))
		if message == "" {
			return "", E.Cause(err, "query system service")
		}
		return "", E.New("query system service: ", message)
	}
	return strings.TrimSpace(string(output)), nil
}

func runSystemctl(arguments ...string) error {
	output, err := exec.Command("systemctl", arguments...).CombinedOutput()
	if err != nil {
		message := strings.TrimSpace(string(output))
		if message == "" {
			return E.Cause(err, "systemctl ", strings.Join(arguments, " "))
		}
		return E.New("systemctl ", strings.Join(arguments, " "), ": ", message)
	}
	return nil
}

// resolveInstallBinary chooses the systemd ExecStart binary.
//
// If the running executable already lives in a package-managed protected
// directory (root-owned, non-world-writable), it is used in place. That
// covers deb/rpm/pacman layouts such as /usr/lib/fr.husi/bin/husi-core.
// Otherwise the caller copies the pair to defaultInstallBin.
func resolveInstallBinary(executablePath string) (installBin string, useInPlace bool, err error) {
	resolvedPath, err := resolveExecutablePath(executablePath)
	if err != nil {
		return "", false, err
	}
	if err := ValidateProtectedDirectory(filepath.Dir(resolvedPath)); err == nil {
		return resolvedPath, true, nil
	}
	return defaultInstallBin, false, nil
}

func prepareInstallDirectory(installBin string) error {
	installDir := filepath.Dir(installBin)
	if err := os.MkdirAll(installDir, 0o755); err != nil {
		return E.Cause(err, "create install directory")
	}
	if err := ValidateProtectedDirectory(installDir); err != nil {
		return E.Cause(err, "validate install directory")
	}
	return nil
}
