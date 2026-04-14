package libcore

import (
	"context"
	"os"

	"github.com/sagernet/sing-box/common/stun"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"
)

type StunTester struct {
	ctx    context.Context
	cancel context.CancelFunc
}

type STUNTestHandler interface {
	OnReport(report *STUNTestReport, done bool)
	OnError(message string)
}

type STUNTestReport struct {
	ExternalAddr     string
	LatencyMs        int32
	NATMapping       int32
	NATFiltering     int32
	NATTypeSupported bool
}

const (
	NATMappingUnknown                 = int32(stun.NATMappingUnknown)
	NATMappingEndpointIndependent     = int32(stun.NATMappingEndpointIndependent)
	NATMappingAddressDependent        = int32(stun.NATMappingAddressDependent)
	NATMappingAddressAndPortDependent = int32(stun.NATMappingAddressAndPortDependent)
)

const (
	NATFilteringUnknown                 = int32(stun.NATFilteringUnknown)
	NATFilteringEndpointIndependent     = int32(stun.NATFilteringEndpointIndependent)
	NATFilteringAddressDependent        = int32(stun.NATFilteringAddressDependent)
	NATFilteringAddressAndPortDependent = int32(stun.NATFilteringAddressAndPortDependent)
)

func (s *StunTester) Start(server, proxy string, handler STUNTestHandler) {
	if s.ctx != nil && !common.Done(s.ctx) {
		handler.OnError(os.ErrExist.Error())
		return
	}

	s.ctx, s.cancel = context.WithCancel(context.Background())
	var dialer N.Dialer
	if proxy != "" {
		var err error
		dialer, err = socks.NewClientFromURL(new(N.DefaultDialer), proxy)
		if err != nil {
			handler.OnError(E.Cause(err, "failed to create proxy dialer").Error())
			return
		}
	}
	go s.start(server, dialer, handler)
}

func (s *StunTester) start(server string, dialer N.Dialer, handler STUNTestHandler) {
	var report STUNTestReport
	result, err := stun.Run(stun.Options{
		Server:  server,
		Dialer:  dialer,
		Context: s.ctx,
		OnProgress: func(progress stun.Progress) {
			report.ExternalAddr = progress.ExternalAddr
			report.LatencyMs = progress.LatencyMs
			report.NATMapping = int32(progress.NATMapping)
			report.NATFiltering = int32(progress.NATFiltering)
			handler.OnReport(&report, false)
		},
	})
	if err != nil {
		handler.OnError(err.Error())
		return
	}
	report.ExternalAddr = result.ExternalAddr
	report.LatencyMs = result.LatencyMs
	report.NATMapping = int32(result.NATMapping)
	report.NATFiltering = int32(result.NATFiltering)
	report.NATTypeSupported = result.NATTypeSupported
	handler.OnReport(&report, true)
}

func (s *StunTester) Cancel() {
	if s.cancel != nil {
		s.cancel()
	}
}

func FormatNATMapping(natMapping int32) string {
	return stun.NATMapping(natMapping).String()
}

func FormatNATFiltering(natFiltering int32) string {
	return stun.NATFiltering(natFiltering).String()
}
