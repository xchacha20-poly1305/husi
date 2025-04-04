package main

import (
	"strings"

	"github.com/sagernet/sing-box/common/geosite"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/x/linkedhashmap"

	"github.com/v2fly/v2ray-core/v5/app/router/routercommon"
	"google.golang.org/protobuf/proto"
)

func generateGeosite(data []byte) (*linkedhashmap.Map[string, []geosite.Item], error) {
	domainMap, err := parseGeosite(data)
	if err != nil {
		return nil, err
	}
	filterGeositeTags(domainMap)
	mergeGeositeTags(domainMap)

	return domainMap, nil
}

func parseGeosite(vGeositeData []byte) (*linkedhashmap.Map[string, []geosite.Item], error) {
	vGeositeList := routercommon.GeoSiteList{}
	err := proto.Unmarshal(vGeositeData, &vGeositeList)
	if err != nil {
		return nil, err
	}
	domainMap := new(linkedhashmap.Map[string, []geosite.Item])
	for _, vGeositeEntry := range vGeositeList.Entry {
		code := strings.ToLower(vGeositeEntry.CountryCode)
		domains := make([]geosite.Item, 0, len(vGeositeEntry.Domain)*2)
		attributes := new(linkedhashmap.Map[string, []*routercommon.Domain])
		for _, domain := range vGeositeEntry.Domain {
			if len(domain.Attribute) > 0 {
				for _, attribute := range domain.Attribute {
					old, _ := attributes.Get(attribute.Key)
					attributes.Put(attribute.Key, append(old, domain))
				}
			}
			switch domain.Type {
			case routercommon.Domain_Plain:
				domains = append(domains, geosite.Item{
					Type:  geosite.RuleTypeDomainKeyword,
					Value: domain.Value,
				})
			case routercommon.Domain_Regex:
				domains = append(domains, geosite.Item{
					Type:  geosite.RuleTypeDomainRegex,
					Value: domain.Value,
				})
			case routercommon.Domain_RootDomain:
				if strings.Contains(domain.Value, ".") {
					domains = append(domains, geosite.Item{
						Type:  geosite.RuleTypeDomain,
						Value: domain.Value,
					})
				}
				domains = append(domains, geosite.Item{
					Type:  geosite.RuleTypeDomainSuffix,
					Value: "." + domain.Value,
				})
			case routercommon.Domain_Full:
				domains = append(domains, geosite.Item{
					Type:  geosite.RuleTypeDomain,
					Value: domain.Value,
				})
			}
		}
		domainMap.Put(code, common.Uniq(domains))
		for _, attribute := range attributes.Entries() {
			attributeDomains := make([]geosite.Item, 0, len(attribute.Value)*2)
			for _, domain := range attribute.Value {
				switch domain.Type {
				case routercommon.Domain_Plain:
					attributeDomains = append(attributeDomains, geosite.Item{
						Type:  geosite.RuleTypeDomainKeyword,
						Value: domain.Value,
					})
				case routercommon.Domain_Regex:
					attributeDomains = append(attributeDomains, geosite.Item{
						Type:  geosite.RuleTypeDomainRegex,
						Value: domain.Value,
					})
				case routercommon.Domain_RootDomain:
					if strings.Contains(domain.Value, ".") {
						attributeDomains = append(attributeDomains, geosite.Item{
							Type:  geosite.RuleTypeDomain,
							Value: domain.Value,
						})
					}
					attributeDomains = append(attributeDomains, geosite.Item{
						Type:  geosite.RuleTypeDomainSuffix,
						Value: "." + domain.Value,
					})
				case routercommon.Domain_Full:
					attributeDomains = append(attributeDomains, geosite.Item{
						Type:  geosite.RuleTypeDomain,
						Value: domain.Value,
					})
				}
			}
			domainMap.Put(code+"@"+attribute.Key, common.Uniq(attributeDomains))
		}
	}
	return domainMap, nil
}

type filteredGeositeCodePair struct {
	code    string
	badCode string
}

func filterGeositeTags(data *linkedhashmap.Map[string, []geosite.Item]) {
	codeList := data.Keys()
	var badCodeList []filteredGeositeCodePair
	var filteredCodeMap []string
	var mergedCodeMap []string
	for _, code := range codeList {
		codeParts := strings.Split(code, "@")
		if len(codeParts) != 2 {
			continue
		}
		leftParts := strings.Split(codeParts[0], "-")
		var lastName string
		if len(leftParts) > 1 {
			lastName = leftParts[len(leftParts)-1]
		}
		if lastName == "" {
			lastName = codeParts[0]
		}
		if lastName == codeParts[1] {
			data.Remove(code)
			filteredCodeMap = append(filteredCodeMap, code)
			continue
		}
		if "!"+lastName == codeParts[1] {
			badCodeList = append(badCodeList, filteredGeositeCodePair{
				code:    codeParts[0],
				badCode: code,
			})
		} else if lastName == "!"+codeParts[1] {
			badCodeList = append(badCodeList, filteredGeositeCodePair{
				code:    codeParts[0],
				badCode: code,
			})
		}
	}
	for _, it := range badCodeList {
		badList, _ := data.Get(it.badCode)
		if badList == nil {
			panic("bad list not found: " + it.badCode)
		}
		data.Remove(it.badCode)
		newMap := new(linkedhashmap.Map[geosite.Item, bool])
		items, _ := data.Get(it.code)
		for _, item := range items {
			newMap.Put(item, true)
		}
		for _, item := range badList {
			newMap.Remove(item)
		}
		newList := newMap.Keys()
		data.Put(it.code, newList)
		mergedCodeMap = append(mergedCodeMap, it.badCode)
	}
	log.Info("filtered ", strings.Join(filteredCodeMap, ","), "\n")
	log.Info("merged ", strings.Join(mergedCodeMap, ","), "\n")
}

func mergeGeositeTags(data *linkedhashmap.Map[string, []geosite.Item]) {
	codeList := data.Keys()
	var cnCodeList []string
	for _, code := range codeList {
		codeParts := strings.Split(code, "@")
		if len(codeParts) != 2 {
			continue
		}
		if codeParts[1] != "cn" {
			continue
		}
		if !strings.HasPrefix(codeParts[0], "category-") {
			continue
		}
		if strings.HasSuffix(codeParts[0], "-cn") || strings.HasSuffix(codeParts[0], "-!cn") {
			continue
		}
		cnCodeList = append(cnCodeList, code)
	}
	for _, code := range codeList {
		if !strings.HasPrefix(code, "category-") {
			continue
		}
		if !strings.HasSuffix(code, "-cn") {
			continue
		}
		if strings.Contains(code, "@") {
			continue
		}
		cnCodeList = append(cnCodeList, code)
	}
	newMap := new(linkedhashmap.Map[geosite.Item, bool])
	cnLocation, _ := data.Get("geolocation-cn")
	for _, item := range cnLocation {
		newMap.Put(item, true)
	}
	for _, code := range cnCodeList {
		items, _ := data.Get(code)
		for _, item := range items {
			newMap.Put(item, true)
		}
	}
	newList := newMap.Keys()
	data.Put("geolocation-cn", newList)
	data.Put("cn", append(newList, geosite.Item{
		Type:  geosite.RuleTypeDomainSuffix,
		Value: "cn",
	}))
	log.Info("merged cn categories: " + strings.Join(cnCodeList, ","))
}
