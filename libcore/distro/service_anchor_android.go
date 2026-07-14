package distro

import (
	"libcore/plugin/anchor"

	"github.com/sagernet/sing-box/adapter/service"
)

func registerAnchor(registry *service.Registry) {
	anchor.RegisterService(registry)
}
