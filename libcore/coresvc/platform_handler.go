package coresvc

import (
	"os"

	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/log"
)

type platformHandler struct{}

var _ daemon.PlatformHandler = platformHandler{}

func (platformHandler) WriteDebugMessage(message string) {
	log.Debug(message)
}

func (platformHandler) ConnectSSHAgent() (int32, error) {
	return 0, os.ErrInvalid
}
