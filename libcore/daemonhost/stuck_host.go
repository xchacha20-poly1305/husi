package daemonhost

import (
	"context"

	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
)

type stuckHostSignal struct {
	cancel     context.CancelFunc
	logMessage string
	exitReason string
	cause      common.TypedValue[error]
}

func newStuckHostSignal(cancel context.CancelFunc, logMessage, exitReason string) *stuckHostSignal {
	return &stuckHostSignal{
		cancel:     cancel,
		logMessage: logMessage,
		exitReason: exitReason,
	}
}

func (s *stuckHostSignal) report(err error) {
	if !s.cause.CompareAndSwap(nil, err) {
		return
	}
	log.Error(s.logMessage, ": ", err)
	s.cancel()
}

func (s *stuckHostSignal) exitError(closeErr error) error {
	if cause := s.cause.Load(); cause != nil {
		return E.Cause(cause, s.exitReason)
	}
	return closeErr
}
