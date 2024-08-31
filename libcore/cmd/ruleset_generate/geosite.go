package main

import (
	"cmp"
	"slices"
	"strings"

	"github.com/sagernet/sing-box/common/geosite"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"

	"github.com/v2fly/v2ray-core/v5/app/router/routercommon"
	"google.golang.org/protobuf/proto"
)

func generateGeosite(data []byte) ([]*NamedItemList[geosite.Item], error) {
	domainMap, err := parseGeosite(data)
	if err != nil {
		return nil, err
	}
	filterGeositeTags(domainMap)
	mergeGeositeTags(domainMap)

	list := FromMap(domainMap)
	return list, nil
}

func parseGeosite(vGeositeData []byte) (map[string][]geosite.Item, error) {
	vGeositeList := routercommon.GeoSiteList{}
	err := proto.Unmarshal(vGeositeData, &vGeositeList)
	if err != nil {
		return nil, err
	}
	domainMap := make(map[string][]geosite.Item)
	for _, vGeositeEntry := range vGeositeList.Entry {
		code := strings.ToLower(vGeositeEntry.CountryCode)
		domains := make([]geosite.Item, 0, len(vGeositeEntry.Domain)*2)
		attributes := make(map[string][]*routercommon.Domain)
		for _, domain := range vGeositeEntry.Domain {
			if len(domain.Attribute) > 0 {
				for _, attribute := range domain.Attribute {
					attributes[attribute.Key] = append(attributes[attribute.Key], domain)
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
		domainMap[code] = common.Uniq(domains)
		attributesList := FromMap(attributes)
		for _, attribute := range attributesList {
			attributeDomains := make([]geosite.Item, 0, len(attribute.content)*2)
			for _, domain := range attribute.content {
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
			domainMap[code+"@"+attribute.name] = common.Uniq(attributeDomains)
		}
	}
	return domainMap, nil
}

type filteredGeositeCodePair struct {
	code    string
	badCode string
}

func filterGeositeTags(data map[string][]geosite.Item) {
	var codeList []string
	for code := range data {
		codeList = append(codeList, code)
	}
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
			delete(data, code)
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
		badList := data[it.badCode]
		if badList == nil {
			panic("bad list not found: " + it.badCode)
		}
		delete(data, it.badCode)
		newMap := make(map[geosite.Item]bool)
		for _, item := range data[it.code] {
			newMap[item] = true
		}
		for _, item := range badList {
			delete(newMap, item)
		}
		newList := make([]geosite.Item, 0, len(newMap))
		for item := range newMap {
			newList = append(newList, item)
		}
		slices.SortFunc(newList, compareGeositeItem)
		data[it.code] = newList
		mergedCodeMap = append(mergedCodeMap, it.badCode)
	}
	slices.Sort(filteredCodeMap)
	slices.Sort(mergedCodeMap)
	log.Info("filtered ", strings.Join(filteredCodeMap, ","), "\n")
	log.Info("merged ", strings.Join(mergedCodeMap, ","), "\n")
}

func mergeGeositeTags(data map[string][]geosite.Item) {
	var codeList []string
	for code := range data {
		codeList = append(codeList, code)
	}
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
	newMap := make(map[geosite.Item]bool)
	for _, item := range data["geolocation-cn"] {
		newMap[item] = true
	}
	for _, code := range cnCodeList {
		for _, item := range data[code] {
			newMap[item] = true
		}
	}
	newList := make([]geosite.Item, 0, len(newMap))
	for item := range newMap {
		newList = append(newList, item)
	}
	slices.SortFunc(newList, compareGeositeItem)
	data["geolocation-cn"] = newList
	log.Info("merged cn categories: " + strings.Join(cnCodeList, ","))
}

func compareGeositeItem(a, b geosite.Item) int {
	if c := cmp.Compare(a.Type, b.Type); c != 0 {
		return c
	}
	return cmp.Compare(a.Value, b.Value)
}
