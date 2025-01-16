package main

import (
	"net"
	"strings"

	"libcore/named"

	"github.com/oschwald/geoip2-golang"
	"github.com/oschwald/maxminddb-golang"
)

func generateGeoip(data []byte) (ips []*named.Named[[]*net.IPNet], err error) {
	var countryMap map[string][]*net.IPNet
	countryMap, err = parseGeoip(data)
	if err != nil {
		return
	}

	ips = named.FromMap(countryMap)
	return
}

func parseGeoip(binary []byte) (countryMap map[string][]*net.IPNet, err error) {
	database, err := maxminddb.FromBytes(binary)
	if err != nil {
		return
	}
	networks := database.Networks(maxminddb.SkipAliasedNetworks)
	countryMap = make(map[string][]*net.IPNet)
	var country geoip2.Enterprise
	var ipNet *net.IPNet
	for networks.Next() {
		ipNet, err = networks.Network(&country)
		if err != nil {
			return
		}
		code := strings.ToLower(country.RegisteredCountry.IsoCode)
		countryMap[code] = append(countryMap[code], ipNet)
	}
	err = networks.Err()
	return
}
