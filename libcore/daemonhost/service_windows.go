//go:build windows

package daemonhost

import (
	"errors"
	"os"
	"path/filepath"
	"time"

	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/windows"
	"golang.org/x/sys/windows/svc"
	"golang.org/x/sys/windows/svc/mgr"
)

const (
	serviceName            = "husi-daemon"
	serviceDisplayName     = "Husi Core Daemon"
	serviceDescriptionText = "Privileged core daemon for Husi"
)

func ServiceInstall(workingDir string) error {
	if !windows.GetCurrentProcessToken().IsElevated() {
		return E.New("service install requires an elevated process")
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
	if err := VerifyCorePairSignature(srcShim); err != nil {
		return E.Cause(err, "verify core pair to install")
	}
	installDir := filepath.Join(os.Getenv("ProgramFiles"), "husi")
	if installDir == `\husi` || filepath.Base(installDir) != "husi" {
		// ProgramFiles unset: fall back.
		installDir = `C:\Program Files\husi`
	}
	installBin := filepath.Join(installDir, "husi-core.exe")
	if err := prepareInstallDirectory(installBin); err != nil {
		return err
	}

	manager, err := mgr.Connect()
	if err != nil {
		return E.Cause(err, "connect to service manager")
	}
	defer manager.Disconnect()

	// Quoted ImagePath via ComposeCommandLine to prevent space injection.
	// Grammar stays: husi-core run --dir <dir> (no socket flag; named pipe default).
	arguments := []string{"run", "--dir", absDir}
	config := mgr.Config{
		DisplayName:  serviceDisplayName,
		Description:  serviceDescriptionText,
		StartType:    mgr.StartAutomatic,
		Dependencies: []string{"Tcpip"},
	}

	created := false
	service, err := manager.OpenService(serviceName)
	if err != nil {
		if !errors.Is(err, windows.ERROR_SERVICE_DOES_NOT_EXIST) {
			return E.Cause(err, "open service")
		}
	} else {
		// stop → replace pair → start (idempotent upgrade / torn-pair repair).
		if err := stopServiceAndWait(service); err != nil {
			service.Close()
			return E.Cause(err, "stop service")
		}
		if err := updateServiceConfig(service, config, installBin, arguments); err != nil {
			service.Close()
			return err
		}
	}

	if err := installPair(srcShim, srcLib, installBin, nil); err != nil {
		if service != nil {
			service.Close()
		}
		return err
	}
	// Re-check what actually landed on disk: the copy above is not atomic across
	// both files, so this closes the window between copying and starting.
	if err := VerifyCorePairSignature(installBin); err != nil {
		if service != nil {
			service.Close()
		}
		return E.Cause(err, "verify installed core pair")
	}

	if service == nil {
		service, err = manager.CreateService(serviceName, installBin, config, arguments...)
		if err != nil {
			return E.Cause(err, "create service")
		}
		created = true
	}
	defer service.Close()

	if err := startServiceAndWait(service); err != nil {
		if created {
			_ = service.Delete()
		}
		return E.Cause(err, "start service")
	}
	return nil
}

func updateServiceConfig(service *mgr.Service, config mgr.Config, executablePath string, arguments []string) error {
	binaryPathName := windows.ComposeCommandLine(append([]string{executablePath}, arguments...))
	currentConfig, err := service.Config()
	if err != nil {
		return E.Cause(err, "query service config")
	}
	currentConfig.DisplayName = config.DisplayName
	currentConfig.Description = config.Description
	currentConfig.StartType = config.StartType
	currentConfig.Dependencies = config.Dependencies
	currentConfig.BinaryPathName = binaryPathName
	if err := service.UpdateConfig(currentConfig); err != nil {
		return E.Cause(err, "update service config")
	}
	return nil
}

func ServiceUninstall(workingDir string, purge bool) error {
	if !windows.GetCurrentProcessToken().IsElevated() {
		return E.New("service uninstall requires an elevated process")
	}
	manager, err := mgr.Connect()
	if err != nil {
		return E.Cause(err, "connect to service manager")
	}
	defer manager.Disconnect()

	service, err := manager.OpenService(serviceName)
	if err != nil {
		if errors.Is(err, windows.ERROR_SERVICE_DOES_NOT_EXIST) {
			// still clean up binary / data
		} else {
			return E.Cause(err, "open service")
		}
	} else {
		_ = stopServiceAndWait(service)
		if delErr := service.Delete(); delErr != nil {
			service.Close()
			return E.Cause(delErr, "delete service")
		}
		service.Close()
	}

	installBin := filepath.Join(os.Getenv("ProgramFiles"), "husi", "husi-core.exe")
	_ = removePair(installBin)
	_ = os.Remove(filepath.Dir(installBin))

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
	manager, err := mgr.Connect()
	if err != nil {
		return E.Cause(err, "connect to service manager")
	}
	defer manager.Disconnect()
	service, err := manager.OpenService(serviceName)
	if err != nil {
		return E.Cause(err, "open service")
	}
	defer service.Close()
	return startServiceAndWait(service)
}

func ServiceStop() error {
	manager, err := mgr.Connect()
	if err != nil {
		return E.Cause(err, "connect to service manager")
	}
	defer manager.Disconnect()
	service, err := manager.OpenService(serviceName)
	if err != nil {
		if errors.Is(err, windows.ERROR_SERVICE_DOES_NOT_EXIST) {
			return nil
		}
		return E.Cause(err, "open service")
	}
	defer service.Close()
	return stopServiceAndWait(service)
}

func ServiceStatus() (*ServiceStatusResult, error) {
	manager, err := mgr.Connect()
	if err != nil {
		return nil, E.Cause(err, "connect to service manager")
	}
	defer manager.Disconnect()
	service, err := manager.OpenService(serviceName)
	if err != nil {
		if errors.Is(err, windows.ERROR_SERVICE_DOES_NOT_EXIST) {
			return &ServiceStatusResult{ExitCode: 3, Description: "not installed"}, nil
		}
		return nil, E.Cause(err, "open service")
	}
	defer service.Close()
	status, err := service.Query()
	if err != nil {
		return nil, E.Cause(err, "query service")
	}
	if status.State == svc.Running {
		return &ServiceStatusResult{ExitCode: 0, Description: "running"}, nil
	}
	return &ServiceStatusResult{ExitCode: 2, Description: "stopped"}, nil
}

func startServiceAndWait(service *mgr.Service) error {
	status, err := service.Query()
	if err != nil {
		return err
	}
	if status.State == svc.Running {
		return nil
	}
	if err := service.Start(); err != nil {
		return err
	}
	deadline := time.Now().Add(30 * time.Second)
	for time.Now().Before(deadline) {
		status, err = service.Query()
		if err != nil {
			return err
		}
		if status.State == svc.Running {
			return nil
		}
		time.Sleep(200 * time.Millisecond)
	}
	return E.New("service did not reach running state")
}

func stopServiceAndWait(service *mgr.Service) error {
	status, err := service.Query()
	if err != nil {
		return err
	}
	if status.State == svc.Stopped {
		return nil
	}
	status, err = service.Control(svc.Stop)
	if err != nil {
		return err
	}
	deadline := time.Now().Add(30 * time.Second)
	for time.Now().Before(deadline) {
		if status.State == svc.Stopped {
			return nil
		}
		time.Sleep(200 * time.Millisecond)
		status, err = service.Query()
		if err != nil {
			return err
		}
	}
	return E.New("service did not stop in time")
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
