//go:build darwin

package systemproxy

import (
	"fmt"

	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/shell"
)

func Enable(host string, port uint16) error {
	return runNetworksetup(func(service string) [][]string {
		return networksetupEnableArgs(service, host, port)
	})
}

func Disable() error {
	return runNetworksetup(networksetupDisableArgs)
}

func runNetworksetup(argsForService func(service string) [][]string) error {
	output, err := shell.Exec("networksetup", "-listallnetworkservices").Output()
	if err != nil {
		return E.Cause(err, "list network services")
	}
	for service := range parseNetworkServices(string(output)) {
		for _, args := range argsForService(service) {
			err := shell.Exec("networksetup", args...).Attach().Run()
			if err != nil {
				return E.Cause(err, "networksetup ", fmt.Sprint(args))
			}
		}
	}
	return nil
}
