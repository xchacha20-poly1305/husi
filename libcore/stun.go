package libcore

import (
	"fmt"
	"strings"

	"libcore/stun"
)

func StunTest(server string) string {
	// note: this library doesn't support stun1.l.google.com:19302

	var resultBuilder strings.Builder

	// Old NAT Type Test
	client := stun.NewClient()
	client.SetServerAddr(server)
	nat, host, err, fakeFullCone := client.Discover()
	if err != nil {
		_, _ = fmt.Fprintf(&resultBuilder, "Discover Error: %v\n", err)
	}

	if fakeFullCone {
		_, _ = resultBuilder.WriteString("Fake fullcone (no endpoint IP change) detected!!")
	}

	if host != nil {
		_, _ = fmt.Fprintf(&resultBuilder, "NAT Type: %s\n", nat)
		_, _ = fmt.Fprintf(&resultBuilder, "External IP Family: %d\n", host.Family())
		_, _ = fmt.Fprintf(&resultBuilder, "External IP: %s\n", host.IP())
		_, _ = fmt.Fprintf(&resultBuilder, "External Port: %d\n", host.Port())
	}

	// New NAT Test

	natBehavior, err := client.BehaviorTest()
	if err != nil {
		_, _ = fmt.Fprintf(&resultBuilder, "Behavior Test Error: %v\n", err)
	}

	if natBehavior != nil {
		_, _ = fmt.Fprintf(&resultBuilder, "Mapping Behavior: %s\n", natBehavior.MappingType.String())
		_, _ = fmt.Fprintf(&resultBuilder, "Filtering Behavior: %s\n", natBehavior.FilteringType.String())
		_, _ = fmt.Fprintf(&resultBuilder, "Normal NAT Type: %s\n", natBehavior.NormalType())
	}

	return resultBuilder.String()
}
