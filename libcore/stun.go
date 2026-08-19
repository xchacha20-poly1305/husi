package libcore

import (
	"context"

	"github.com/sagernet/sing-box/common/stun"
	E "github.com/sagernet/sing/common/exceptions"
	N "github.com/sagernet/sing/common/network"

	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/simpleproxyurl"
)

func runSTUNTest(
	ctx context.Context,
	server, proxy string,
	emit func(*husiv1.STUNTestResponse) error,
) error {
	var dialer N.Dialer
	if proxy != "" {
		var err error
		dialer, err = simpleproxyurl.ProxyFromURL(ctx, proxy)
		if err != nil {
			return E.Cause(err, "create proxy dialer")
		}
	}
	result, err := stun.Run(stun.Options{
		Server:  server,
		Dialer:  dialer,
		Context: ctx,
		OnProgress: func(progress stun.Progress) {
			_ = emit(stunResponseFromProgress(progress, false))
		},
	})
	if err != nil {
		return err
	}
	return emit(&husiv1.STUNTestResponse{
		ExternalAddress:  result.ExternalAddr,
		LatencyMs:        result.LatencyMs,
		Mapping:          toProtoNATMapping(result.NATMapping),
		Filtering:        toProtoNATFiltering(result.NATFiltering),
		NatTypeSupported: result.NATTypeSupported,
		MappingDisplay:   result.NATMapping.String(),
		FilteringDisplay: result.NATFiltering.String(),
		Done:             true,
	})
}

func stunResponseFromProgress(progress stun.Progress, done bool) *husiv1.STUNTestResponse {
	return &husiv1.STUNTestResponse{
		ExternalAddress:  progress.ExternalAddr,
		LatencyMs:        progress.LatencyMs,
		Mapping:          toProtoNATMapping(progress.NATMapping),
		Filtering:        toProtoNATFiltering(progress.NATFiltering),
		MappingDisplay:   progress.NATMapping.String(),
		FilteringDisplay: progress.NATFiltering.String(),
		Done:             done,
	}
}

func toProtoNATMapping(m stun.NATMapping) husiv1.NATMapping {
	switch m {
	case stun.NATMappingEndpointIndependent:
		return husiv1.NATMapping_NAT_MAPPING_ENDPOINT_INDEPENDENT
	case stun.NATMappingAddressDependent:
		return husiv1.NATMapping_NAT_MAPPING_ADDRESS_DEPENDENT
	case stun.NATMappingAddressAndPortDependent:
		return husiv1.NATMapping_NAT_MAPPING_ADDRESS_AND_PORT_DEPENDENT
	case stun.NATMappingUnknown:
		return husiv1.NATMapping_NAT_MAPPING_UNKNOWN
	default:
		return husiv1.NATMapping_NAT_MAPPING_UNKNOWN
	}
}

func toProtoNATFiltering(f stun.NATFiltering) husiv1.NATFiltering {
	switch f {
	case stun.NATFilteringEndpointIndependent:
		return husiv1.NATFiltering_NAT_FILTERING_ENDPOINT_INDEPENDENT
	case stun.NATFilteringAddressDependent:
		return husiv1.NATFiltering_NAT_FILTERING_ADDRESS_DEPENDENT
	case stun.NATFilteringAddressAndPortDependent:
		return husiv1.NATFiltering_NAT_FILTERING_ADDRESS_AND_PORT_DEPENDENT
	case stun.NATFilteringUnknown:
		return husiv1.NATFiltering_NAT_FILTERING_UNKNOWN
	default:
		return husiv1.NATFiltering_NAT_FILTERING_UNKNOWN
	}
}
