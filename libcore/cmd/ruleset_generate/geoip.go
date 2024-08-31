package main

import (
	"net"
	"strings"

	"github.com/oschwald/geoip2-golang"
	"github.com/oschwald/maxminddb-golang"
)

func generateGeoip(data []byte) (ips []*NamedItemList[*net.IPNet], err error) {
	var countryMap map[string][]*net.IPNet
	countryMap, err = parseGeoip(data)
	if err != nil {
		return
	}

	ips = FromMap(countryMap)
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
		var code string
		if country.Country.IsoCode != "" {
			code = strings.ToLower(country.Country.IsoCode)
		} else if country.RegisteredCountry.IsoCode != "" {
			code = strings.ToLower(country.RegisteredCountry.IsoCode)
		} else if country.RepresentedCountry.IsoCode != "" {
			code = strings.ToLower(country.RepresentedCountry.IsoCode)
		} else if country.Continent.Code != "" {
			code = strings.ToLower(country.Continent.Code)
		} else {
			continue
		}
		countryMap[code] = append(countryMap[code], ipNet)
	}
	err = networks.Err()
	return
}
