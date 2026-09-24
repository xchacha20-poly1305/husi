package main

import (
	"net"
	"net/netip"
	"strings"

	E "github.com/sagernet/sing/common/exceptions"

	"github.com/oschwald/geoip2-golang"
	"github.com/oschwald/maxminddb-golang"
	"go4.org/netipx"
)

func parseGeoip(binary []byte) (countryMap sortedStringMap[[]netip.Prefix], err error) {
	database, err := maxminddb.FromBytes(binary)
	if err != nil {
		return
	}
	defer database.Close()
	networks := database.Networks(maxminddb.SkipAliasedNetworks)
	countryMap = make(sortedStringMap[[]netip.Prefix])
	var country geoip2.Enterprise
	var ipNet *net.IPNet
	for networks.Next() {
		ipNet, err = networks.Network(&country)
		if err != nil {
			return
		}
		prefix, loaded := netipx.FromStdIPNet(ipNet)
		if !loaded {
			err = E.New("invalid network: ", ipNet)
			return
		}
		code := strings.ToLower(country.RegisteredCountry.IsoCode)
		old := countryMap.Get(code)
		countryMap.Put(code, append(old, prefix))
	}
	err = networks.Err()
	return
}
