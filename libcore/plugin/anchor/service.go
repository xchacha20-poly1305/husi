package anchor

import (
	"cmp"
	"context"
	"net"
	"regexp"

	"libcore/plugin/pluginoption"

	"github.com/sagernet/sing-box/adapter"
	boxService "github.com/sagernet/sing-box/adapter/service"
	"github.com/sagernet/sing-box/common/listener"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/common/x/list"
	"github.com/sagernet/sing/service"

	"github.com/xchacha20-poly1305/anchor"
	"github.com/xchacha20-poly1305/anchor/anchorservice"
	"go4.org/netipx"
)

func RegisterService(registry *boxService.Registry) {
	boxService.Register[pluginoption.AnchorServiceOptions](registry, pluginoption.TypeAnchor, NewService)
}

var _ adapter.Service = (*Service)(nil)

type Service struct {
	boxService.Adapter
	ctx            context.Context
	logger         log.ContextLogger
	expressions    []*regexp.Regexp
	anchor         *anchorservice.Anchor
	networkManager adapter.NetworkManager
	listener       *listener.Listener
}

func NewService(ctx context.Context, logger log.ContextLogger, tag string, options pluginoption.AnchorServiceOptions) (adapter.Service, error) {
	if options.SocksPort == 0 {
		return nil, E.New("missing socks port")
	}
	var expressions []*regexp.Regexp
	if len(options.AllowedSSIDs) == 0 {
		logger.WarnContext(ctx, "dangerous skip rules")
	} else {
		expressions = make([]*regexp.Regexp, 0, len(options.AllowedSSIDs))
		for i, expression := range options.AllowedSSIDs {
			regex, err := regexp.Compile(expression)
			if err != nil {
				return nil, E.Cause(err, "compile regexp ", i)
			}
			expressions = append(expressions, regex)
		}
	}
	response := &anchor.Response{
		Version:    anchor.Version,
		DnsPort:    options.DNSPort,
		DeviceName: cmp.Or(options.DeviceName, "unknown"),
		SocksPort:  options.SocksPort,
	}
	anchorListener := listener.New(listener.Options{
		Context: ctx,
		Logger:  logger,
		Network: []string{N.NetworkUDP},
		Listen:  options.ListenOptions,
	})
	s := &Service{
		Adapter:        boxService.NewAdapter(pluginoption.TypeAnchor, tag),
		ctx:            ctx,
		logger:         logger,
		expressions:    expressions,
		listener:       anchorListener,
		networkManager: service.FromContext[adapter.NetworkManager](ctx),
	}
	// StartedService has no pre-Start hook for Network().Initialize, so force
	// needWIFIState here during box.New (D-P1.3). NetworkManager.Initialize
	// only ORs the flag — safe when already true.
	if s.networkManager != nil {
		s.networkManager.Initialize([]adapter.RuleSet{fakeWifiRuleSet{}})
	}
	anchorService := anchorservice.New(
		ctx,
		logger,
		func(_ context.Context) (net.PacketConn, error) {
			return anchorListener.ListenUDP()
		},
		response,
		s.shouldReject,
	)
	s.anchor = anchorService
	return s, nil
}

// fakeWifiRuleSet is a no-op RuleSet whose metadata reports ContainsWIFIRule,
// so NetworkManager.Initialize sets needWIFIState. Moved here from box.go when
// lifecycle ownership moved to daemon.StartedService (D-P1.3).
var _ adapter.RuleSet = fakeWifiRuleSet{}

type fakeWifiRuleSet struct{}

func (f fakeWifiRuleSet) Name() string { return f.String() }

func (f fakeWifiRuleSet) StartContext(ctx context.Context, startContext *adapter.HTTPStartContext) error {
	return nil
}

func (f fakeWifiRuleSet) Metadata() adapter.RuleSetMetadata {
	return adapter.RuleSetMetadata{ContainsWIFIRule: true}
}

func (f fakeWifiRuleSet) ExtractIPSet() []*netipx.IPSet { return nil }
func (f fakeWifiRuleSet) IncRef()                       {}
func (f fakeWifiRuleSet) DecRef()                       {}
func (f fakeWifiRuleSet) Cleanup()                      {}
func (f fakeWifiRuleSet) RegisterCallback(callback adapter.RuleSetUpdateCallback) *list.Element[adapter.RuleSetUpdateCallback] {
	return nil
}

func (f fakeWifiRuleSet) UnregisterCallback(element *list.Element[adapter.RuleSetUpdateCallback]) {
}
func (f fakeWifiRuleSet) Close() error                                { return nil }
func (f fakeWifiRuleSet) Match(metadata *adapter.InboundContext) bool { return false }
func (f fakeWifiRuleSet) String() string                              { return "fakeWifiRuleSet" }

func (s *Service) Start(stage adapter.StartStage) error {
	if stage != adapter.StartStateStart {
		return nil
	}
	return s.anchor.Start()
}

func (s *Service) Close() error {
	return common.Close(
		common.PtrOrNil(s.anchor),
		common.PtrOrNil(s.listener),
	)
}

func (s *Service) shouldReject(source net.Addr, deviceName string) bool {
	s.logger.InfoContext(s.ctx, "response from ", source, "(", deviceName, ")")
	switch s.networkManager.DefaultNetworkInterface().Type {
	case C.InterfaceTypeWIFI:
	case C.InterfaceTypeEthernet:
		return false
	default:
		return true
	}
	if len(s.expressions) == 0 {
		return false // Dangerous
	}
	ssid := s.networkManager.WIFIState().SSID
	if ssid == "" {
		return true
	}
	if common.Any(s.expressions, func(it *regexp.Regexp) bool {
		return it.MatchString(ssid)
	}) {
		return false
	}
	return true
}
