//go:build darwin

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
	darwinInstallBin   = "/Library/Application Support/husi/bin/husi-core"
	darwinPlistPath    = "/Library/LaunchDaemons/fr.husi.daemon.plist"
	darwinServiceLabel = "fr.husi.daemon"
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
	if err := installPair(srcShim, srcLib, darwinInstallBin, nil); err != nil {
		return err
	}
	// Root ownership + strip quarantine on both pair members so launchd can
	// exec a freshly copied shim and the dylib it dlopens.
	for _, path := range []string{darwinInstallBin, SiblingCoreLibrary(darwinInstallBin)} {
		if err := os.Chown(path, 0, 0); err != nil {
			return E.Cause(err, "chown ", path)
		}
		_ = exec.Command("xattr", "-d", "com.apple.quarantine", path).Run()
	}

	socketPath := DefaultSocketPath()
	plist := fmt.Sprintf(`<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>Label</key>
	<string>%s</string>
	<key>ProgramArguments</key>
	<array>
		<string>%s</string>
		<string>run</string>
		<string>--dir</string>
		<string>%s</string>
		<string>--socket</string>
		<string>%s</string>
	</array>
	<key>RunAtLoad</key>
	<true/>
	<key>KeepAlive</key>
	<true/>
</dict>
</plist>
`, darwinServiceLabel, darwinInstallBin, absDir, socketPath)

	// bootout first so reinstall is idempotent.
	_ = exec.Command("launchctl", "bootout", "system/"+darwinServiceLabel).Run()

	if err := os.WriteFile(darwinPlistPath, []byte(plist), 0o644); err != nil {
		return E.Cause(err, "write launchd plist")
	}
	if err := os.Chown(darwinPlistPath, 0, 0); err != nil {
		return E.Cause(err, "chown launchd plist")
	}
	output, err := exec.Command("launchctl", "bootstrap", "system", darwinPlistPath).CombinedOutput()
	if err != nil {
		message := strings.TrimSpace(string(output))
		if message == "" {
			return E.Cause(err, "launchctl bootstrap")
		}
		return E.New("launchctl bootstrap: ", message)
	}
	_ = exec.Command("launchctl", "enable", "system/"+darwinServiceLabel).Run()
	_ = exec.Command("launchctl", "kickstart", "-k", "system/"+darwinServiceLabel).Run()
	return nil
}

func ServiceUninstall(workingDir string, purge bool) error {
	if os.Geteuid() != 0 {
		return E.New("service uninstall requires root")
	}
	_ = exec.Command("launchctl", "bootout", "system/"+darwinServiceLabel).Run()
	_ = os.Remove(darwinPlistPath)
	if err := removePair(darwinInstallBin); err != nil {
		return err
	}
	_ = os.Remove(filepath.Dir(darwinInstallBin))
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
	output, err := exec.Command("launchctl", "kickstart", "-k", "system/"+darwinServiceLabel).CombinedOutput()
	if err != nil {
		message := strings.TrimSpace(string(output))
		if message == "" {
			return E.Cause(err, "launchctl kickstart")
		}
		return E.New("launchctl kickstart: ", message)
	}
	return nil
}

func ServiceStop() error {
	output, err := exec.Command("launchctl", "kill", "SIGTERM", "system/"+darwinServiceLabel).CombinedOutput()
	if err != nil {
		// Not loaded is fine.
		message := strings.TrimSpace(string(output))
		if strings.Contains(message, "No such process") || strings.Contains(message, "Could not find") {
			return nil
		}
		if message == "" {
			return E.Cause(err, "launchctl kill")
		}
		return E.New("launchctl kill: ", message)
	}
	return nil
}

func ServiceStatus() (*ServiceStatusResult, error) {
	if _, err := os.Stat(darwinPlistPath); os.IsNotExist(err) {
		return &ServiceStatusResult{ExitCode: 3, Description: "not installed"}, nil
	}
	output, err := exec.Command("launchctl", "print", "system/"+darwinServiceLabel).CombinedOutput()
	if err != nil {
		return &ServiceStatusResult{ExitCode: 2, Description: "stopped"}, nil
	}
	text := string(output)
	if strings.Contains(text, "state = running") || strings.Contains(text, "pid = ") {
		return &ServiceStatusResult{ExitCode: 0, Description: "running"}, nil
	}
	return &ServiceStatusResult{ExitCode: 2, Description: "stopped"}, nil
}
