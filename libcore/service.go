package libcore

import (
	"sync"

	"libcore/coresvc"
	"libcore/pb/husi/v1"
	"libcore/plugin/pluginoption"
	"libcore/pluginpool"

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/service"
)

type Service struct {
	access             sync.RWMutex
	version            string
	platformInterface  PlatformInterface
	appHandler         coresvc.AppHandler
	host               *coresvc.Host
	pluginPool         *pluginpool.PluginPool
	pluginFatalHandler PluginFatalHandler
	// pluginWorkingDir is the parent directory for transient URL-test plugin
	// pools and (on Android) the long-lived StartService plugin pool.
	pluginWorkingDir string
}

func (s *Service) SetPluginWorkingDir(dir string) {
	s.access.Lock()
	defer s.access.Unlock()
	s.pluginWorkingDir = dir
}

func (s *Service) buildHost() (*coresvc.Host, error) {
	ctx := baseContext(s.platformInterface)
	registerPlatformInterface(ctx, s.platformInterface, false)
	holder := coresvc.NewInstanceContextHolder()
	service.MustRegister[*coresvc.InstanceContextHolder](ctx, holder)

	opts := coresvc.HostOptions{
		Context:     ctx,
		Version:     s.version,
		LogMaxLines: currentLogMaxLines(),
		AppHandler:  s.appHandler,
		CheckConfig: CheckConfig,
		GenerateSchema: func(kind husiv1.SchemaKind) (string, error) {
			switch kind {
			case husiv1.SchemaKind_SCHEMA_KIND_CONFIG:
				return generateSchema[option.Options]()
			case husiv1.SchemaKind_SCHEMA_KIND_OUTBOUND:
				return generateSchema[option.Outbound]()
			case husiv1.SchemaKind_SCHEMA_KIND_DNS_RULE:
				return generateSchema[option.DNSRule]()
			default:
				return "", E.New("unknown schema kind: ", kind.String())
			}
		},
		StandaloneURLTest: s.standaloneURLTest,
		BuildEnvironment:  BuildEnvironment,
		FileLogSink:       fileLogSink(),
	}
	WireApplicationTools(&opts)
	return coresvc.NewHost(opts)
}

func (s *Service) Start() error {
	s.access.Lock()
	defer s.access.Unlock()
	if s.host != nil {
		return nil
	}
	host, err := s.buildHost()
	if err != nil {
		return err
	}
	if err = host.Start(apiPath("")); err != nil {
		_ = host.Close()
		return err
	}
	s.host = host
	return nil
}

func (s *Service) Close() error {
	s.access.Lock()
	defer s.access.Unlock()
	s.closePluginPoolLocked()
	return common.Close(
		common.PtrOrNil(s.host),
	)
}

// StopService tears down the plugin pool and the instance. Callers stop it
// unconditionally, because plugin processes outlive an instance that already
// went away on its own; CloseService rejects an idle instance, so that case
// returns here instead.
func (s *Service) StopService() error {
	s.access.Lock()
	s.closePluginPoolLocked()
	host := s.host
	s.access.Unlock()
	if host == nil || !host.HasInstance() {
		return nil
	}
	return host.CloseService(C.FatalStopTimeout)
}

func (s *Service) closePluginPoolLocked() {
	if s.pluginPool == nil {
		return
	}
	_ = s.pluginPool.Close()
	s.pluginPool = nil
}

func (s *Service) HasInstance() bool {
	s.access.RLock()
	defer s.access.RUnlock()
	if s.host == nil {
		return false
	}
	return s.host.HasInstance()
}

func (s *Service) standaloneURLTest(config, tag, link string, timeoutMs int32, options uint8, plugins []*husiv1.PluginProcessSpec) (int32, error) {
	s.access.RLock()
	workingDir := s.pluginWorkingDir
	platformInterface := s.platformInterface
	s.access.RUnlock()
	return pluginpool.RunWithPlugins(workingDir, plugins, nil, func() (int32, error) {
		return StandaloneURLTest(config, tag, link, timeoutMs, options, platformInterface)
	})
}

func StandaloneURLTest(config, tag, link string, timeoutMs int32, options uint8, platformInterface PlatformInterface) (int32, error) {
	instance, err := newBoxInstance(config, platformInterface, true)
	if err != nil {
		return -1, E.Cause(err, "create instance")
	}
	defer instance.Close()
	err = instance.Start()
	if err != nil {
		return -1, E.Cause(err, "start instance")
	}
	return instance.urlTest(tag, link, timeoutMs, options)
}

func ProxyDisplayName(proxyType string) string {
	return pluginoption.ProxyDisplayName(proxyType)
}

func apiPath(basePath string) string {
	if basePath == "" {
		basePath = internalAssetsPath
	}
	return coresvc.SocketPath(basePath)
}
