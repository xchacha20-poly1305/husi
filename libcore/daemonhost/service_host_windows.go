//go:build windows

package daemonhost

import (
	"context"
	"time"

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/windows/svc"
)

const (
	daemonStopTimeout    = 2*C.FatalStopTimeout + 10*time.Second
	stopProgressInterval = 1 * time.Second
	stopWaitHint         = 2 * stopProgressInterval
	exitCodeStopTimeout  = 2
	exitCodeRunFailed    = 1
)

func runDaemonHost(ctx context.Context, host *DaemonHost) error {
	isService, err := svc.IsWindowsService()
	if err != nil {
		return E.Cause(err, "check Windows service session")
	}
	if !isService {
		return host.run(ctx)
	}
	return runWindowsService(host)
}

func runWindowsService(host *DaemonHost) error {
	return svc.Run(serviceName, &windowsDaemonService{host: host})
}

type windowsDaemonService struct {
	host *DaemonHost
}

func (s *windowsDaemonService) Execute(
	_ []string,
	requests <-chan svc.ChangeRequest,
	statuses chan<- svc.Status,
) (bool, uint32) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	runResult := make(chan error, 1)
	go func() {
		runResult <- s.host.run(ctx)
	}()

	const acceptedCommands = svc.AcceptStop | svc.AcceptShutdown
	statuses <- svc.Status{
		State:   svc.StartPending,
		Accepts: acceptedCommands,
	}
	statuses <- svc.Status{
		State:   svc.Running,
		Accepts: acceptedCommands,
	}

	for {
		select {
		case err := <-runResult:
			statuses <- svc.Status{State: svc.StopPending}
			if err != nil {
				log.Error("daemon host exited: ", err)
				return true, exitCodeRunFailed
			}
			return false, 0
		case request := <-requests:
			switch request.Cmd {
			case svc.Interrogate:
				statuses <- request.CurrentStatus
			case svc.Stop, svc.Shutdown:
				cancel()
				return waitForStop(runResult, requests, statuses, daemonStopTimeout)
			}
		}
	}
}

func waitForStop(
	runResult <-chan error,
	requests <-chan svc.ChangeRequest,
	statuses chan<- svc.Status,
	timeout time.Duration,
) (bool, uint32) {
	checkPoint := uint32(1)
	reportProgress := func() {
		statuses <- svc.Status{
			State:      svc.StopPending,
			CheckPoint: checkPoint,
			WaitHint:   uint32(stopWaitHint.Milliseconds()),
		}
		checkPoint++
	}
	reportProgress()

	progress := time.NewTicker(stopProgressInterval)
	defer progress.Stop()
	deadline := time.After(timeout)

	for {
		select {
		case err := <-runResult:
			if err != nil {
				log.Error("daemon host stopped with error: ", err)
				return true, exitCodeRunFailed
			}
			return false, 0
		case <-progress.C:
			reportProgress()
		case request := <-requests:
			if request.Cmd == svc.Interrogate {
				statuses <- request.CurrentStatus
			}
		case <-deadline:
			log.Error("daemon host did not stop within ", timeout, ", exiting for service recovery")
			return true, exitCodeStopTimeout
		}
	}
}

var _ svc.Handler = (*windowsDaemonService)(nil)
