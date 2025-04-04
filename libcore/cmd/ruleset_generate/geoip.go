package main

import (
	"net"
	"strings"

	"github.com/oschwald/geoip2-golang"
	"github.com/oschwald/maxminddb-golang"
	"github.com/sagernet/sing/common/x/linkedhashmap"
)

func parseGeoip(binary []byte) (countryMap *linkedhashmap.Map[string, []*net.IPNet], err error) {
	database, err := maxminddb.FromBytes(binary)
	if err != nil {
		return
	}
	networks := database.Networks(maxminddb.SkipAliasedNetworks)
	countryMap = new(linkedhashmap.Map[string, []*net.IPNet])
	var country geoip2.Enterprise
	var ipNet *net.IPNet
	for networks.Next() {
		ipNet, err = networks.Network(&country)
		if err != nil {
			return
		}
		code := strings.ToLower(country.RegisteredCountry.IsoCode)
		old, _ := countryMap.Get(code)
		countryMap.Put(code, append(old, ipNet))
	}
	err = networks.Err()
	return
}
