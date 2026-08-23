//go:build windows

package daemonhost

import (
	"context"

	E "github.com/sagernet/sing/common/exceptions"
	"golang.org/x/sys/windows/svc"
)

func runDaemonHost(ctx context.Context, host *DaemonHost) error {
	interactive, err := svc.IsAnInteractiveSession()
	if err != nil {
		return E.Cause(err, "check Windows service session")
	}
	if interactive {
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
				return true, 1
			}
			return false, 0
		case request := <-requests:
			switch request.Cmd {
			case svc.Interrogate:
				statuses <- request.CurrentStatus
			case svc.Stop, svc.Shutdown:
				statuses <- svc.Status{State: svc.StopPending}
				cancel()
				if err := <-runResult; err != nil {
					return true, 1
				}
				return false, 0
			}
		}
	}
}

var _ svc.Handler = (*windowsDaemonService)(nil)
