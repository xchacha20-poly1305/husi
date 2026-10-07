package main

import (
	"archive/tar"
	"bufio"
	"compress/gzip"
	"io"
	"path"
	"regexp"
	"slices"
	"strings"

	"github.com/sagernet/sing-box/common/geosite"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
)

const (
	geositeRuleTypeDomain     = "domain"
	geositeRuleTypeFullDomain = "full"
	geositeRuleTypeKeyword    = "keyword"
	geositeRuleTypeRegexp     = "regexp"
	geositeRuleTypeInclude    = "include"
)

type geositeEntry struct {
	Type  string
	Value string
	Attrs []string
	Plain string
}

type geositeInclusion struct {
	Source    string
	MustAttrs []string
	BanAttrs  []string
}

type geositeParsedList struct {
	Name       string
	Inclusions []*geositeInclusion
	Entries    []*geositeEntry
}

type geositeProcessor struct {
	parsedLists map[string]*geositeParsedList
	finalLists  map[string][]*geositeEntry
	circular    map[string]bool
}

func generateGeosite(reader io.Reader) (sortedStringMap[[]geosite.Item], error) {
	processor, err := parseGeositeSource(reader)
	if err != nil {
		return nil, err
	}
	domainMap, err := processor.generateDomainMap()
	if err != nil {
		return nil, err
	}
	filterGeositeTags(domainMap)
	mergeGeositeTags(domainMap)

	return domainMap, nil
}

func parseGeositeSource(reader io.Reader) (*geositeProcessor, error) {
	gzipReader, err := gzip.NewReader(reader)
	if err != nil {
		return nil, E.Cause(err, "open geosite source archive")
	}
	defer gzipReader.Close()

	processor := &geositeProcessor{
		parsedLists: make(map[string]*geositeParsedList),
	}
	tarReader := tar.NewReader(gzipReader)
	for {
		header, err := tarReader.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return nil, E.Cause(err, "read geosite source archive")
		}
		if header.Typeflag != tar.TypeReg {
			continue
		}
		listName, loaded := geositeListNameFromArchivePath(header.Name)
		if !loaded {
			continue
		}
		if err := processor.loadData(listName, header.Name, tarReader); err != nil {
			return nil, err
		}
	}
	if len(processor.parsedLists) == 0 {
		return nil, E.New("geosite source archive has no data files")
	}
	return processor, nil
}

func geositeListNameFromArchivePath(name string) (string, bool) {
	name = path.Clean(name)
	parts := strings.Split(name, "/")
	if len(parts) != 3 || parts[1] != "data" {
		return "", false
	}
	listName := strings.ToUpper(parts[2])
	if !validateGeositeListName(listName) {
		return "", false
	}
	return listName, true
}

func (p *geositeProcessor) getOrCreateParsedList(name string) *geositeParsedList {
	parsedList, loaded := p.parsedLists[name]
	if !loaded {
		parsedList = &geositeParsedList{Name: name}
		p.parsedLists[name] = parsedList
	}
	return parsedList
}

func (p *geositeProcessor) loadData(listName string, sourceName string, reader io.Reader) error {
	parsedList := p.getOrCreateParsedList(listName)
	scanner := bufio.NewScanner(reader)
	lineNumber := 0
	for scanner.Scan() {
		lineNumber++
		line, _, _ := strings.Cut(scanner.Text(), "#")
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		ruleType, rule, hasType := strings.Cut(line, ":")
		if !hasType {
			ruleType, rule = geositeRuleTypeDomain, ruleType
		} else {
			ruleType = strings.ToLower(ruleType)
		}
		if ruleType == geositeRuleTypeInclude {
			inclusion, err := parseGeositeInclusion(rule)
			if err != nil {
				return E.Cause(err, "error in ", sourceName, " at line ", lineNumber)
			}
			parsedList.Inclusions = append(parsedList.Inclusions, inclusion)
			continue
		}
		entry, affiliations, err := parseGeositeEntry(ruleType, rule)
		if err != nil {
			return E.Cause(err, "error in ", sourceName, " at line ", lineNumber)
		}
		for _, affiliation := range affiliations {
			affiliateList := p.getOrCreateParsedList(affiliation)
			affiliateList.Entries = append(affiliateList.Entries, entry)
		}
		parsedList.Entries = append(parsedList.Entries, entry)
	}
	err := scanner.Err()
	if err != nil {
		return E.Cause(err, "read ", sourceName)
	}
	return nil
}

func parseGeositeEntry(ruleType string, rule string) (*geositeEntry, []string, error) {
	entry := &geositeEntry{Type: ruleType}
	parts := strings.Fields(rule)
	if len(parts) == 0 {
		return entry, nil, E.New("empty domain rule")
	}
	switch entry.Type {
	case geositeRuleTypeRegexp:
		if _, err := regexp.Compile(parts[0]); err != nil {
			return entry, nil, E.Cause(err, "invalid regexp: ", parts[0])
		}
		entry.Value = parts[0]
	case geositeRuleTypeDomain, geositeRuleTypeFullDomain, geositeRuleTypeKeyword:
		entry.Value = strings.ToLower(parts[0])
		if !validateGeositeDomain(entry.Value) {
			return entry, nil, E.New("invalid domain: ", entry.Value)
		}
	default:
		return entry, nil, E.New("unknown rule type: ", entry.Type)
	}

	plainLength := len(entry.Type) + len(entry.Value) + 1
	var affiliations []string
	for _, part := range parts[1:] {
		switch part[0] {
		case '@':
			attr := strings.ToLower(part[1:])
			if !validateGeositeAttr(attr) {
				return entry, affiliations, E.New("invalid attribute: ", attr)
			}
			entry.Attrs = append(entry.Attrs, attr)
			plainLength += 2 + len(attr)
		case '&':
			affiliation := strings.ToUpper(part[1:])
			if !validateGeositeListName(affiliation) {
				return entry, affiliations, E.New("invalid affiliation: ", affiliation)
			}
			affiliations = append(affiliations, affiliation)
		default:
			return entry, affiliations, E.New("unknown field: ", part)
		}
	}

	slices.Sort(entry.Attrs)
	var plain strings.Builder
	plain.Grow(plainLength)
	plain.WriteString(entry.Type)
	plain.WriteByte(':')
	plain.WriteString(entry.Value)
	for index, attr := range entry.Attrs {
		if index == 0 {
			plain.WriteByte(':')
		} else {
			plain.WriteByte(',')
		}
		plain.WriteByte('@')
		plain.WriteString(attr)
	}
	entry.Plain = plain.String()
	return entry, affiliations, nil
}

func parseGeositeInclusion(rule string) (*geositeInclusion, error) {
	parts := strings.Fields(rule)
	if len(parts) == 0 {
		return nil, E.New("empty inclusion")
	}
	inclusion := &geositeInclusion{Source: strings.ToUpper(parts[0])}
	if !validateGeositeListName(inclusion.Source) {
		return inclusion, E.New("invalid included list name: ", inclusion.Source)
	}
	for _, part := range parts[1:] {
		switch part[0] {
		case '@':
			attr := strings.ToLower(part[1:])
			if strings.HasPrefix(attr, "-") {
				banAttr := attr[1:]
				if !validateGeositeAttr(banAttr) {
					return inclusion, E.New("invalid ban attribute: ", banAttr)
				}
				inclusion.BanAttrs = append(inclusion.BanAttrs, banAttr)
			} else {
				if !validateGeositeAttr(attr) {
					return inclusion, E.New("invalid must attribute: ", attr)
				}
				inclusion.MustAttrs = append(inclusion.MustAttrs, attr)
			}
		case '&':
			return inclusion, E.New("affiliation is not allowed for inclusion")
		default:
			return inclusion, E.New("unknown field: ", part)
		}
	}
	return inclusion, nil
}

func validateGeositeDomain(domain string) bool {
	if domain == "" {
		return false
	}
	for index := range domain {
		character := domain[index]
		if (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9') || character == '.' || character == '-' {
			continue
		}
		return false
	}
	return true
}

func validateGeositeAttr(attr string) bool {
	if attr == "" {
		return false
	}
	for index := range attr {
		character := attr[index]
		if (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9') || character == '!' {
			continue
		}
		return false
	}
	return true
}

func validateGeositeListName(name string) bool {
	if name == "" {
		return false
	}
	for index := range name {
		character := name[index]
		if (character >= 'A' && character <= 'Z') || (character >= '0' && character <= '9') || character == '!' || character == '-' {
			continue
		}
		return false
	}
	return true
}

func (p *geositeProcessor) generateDomainMap() (sortedStringMap[[]geosite.Item], error) {
	listCount := len(p.parsedLists)
	p.finalLists = make(map[string][]*geositeEntry, listCount)
	p.circular = make(map[string]bool)
	for listName := range p.parsedLists {
		if err := p.resolveList(listName); err != nil {
			return nil, E.Cause(err, "resolve list: ", listName)
		}
	}

	listNames := make([]string, 0, len(p.finalLists))
	for listName := range p.finalLists {
		listNames = append(listNames, listName)
	}
	slices.Sort(listNames)

	domainMap := make(sortedStringMap[[]geosite.Item], len(listNames))
	for _, listName := range listNames {
		entries := p.finalLists[listName]
		items := make([]geosite.Item, 0, len(entries)*2)
		attributes := make(sortedStringMap[[]*geositeEntry])
		for _, entry := range entries {
			items = appendGeositeItems(items, entry)
			for _, attr := range entry.Attrs {
				attributes.Put(attr, append(attributes.Get(attr), entry))
			}
		}
		code := strings.ToLower(listName)
		domainMap.Put(code, common.Uniq(items))
		attrNames := attributes.Keys()
		for _, attrName := range attrNames {
			attrEntries := attributes[attrName]
			attrItems := make([]geosite.Item, 0, len(attrEntries)*2)
			for _, entry := range attrEntries {
				attrItems = appendGeositeItems(attrItems, entry)
			}
			domainMap.Put(code+"@"+attrName, common.Uniq(attrItems))
		}
	}
	return domainMap, nil
}

func appendGeositeItems(items []geosite.Item, entry *geositeEntry) []geosite.Item {
	switch entry.Type {
	case geositeRuleTypeKeyword:
		items = append(items, geosite.Item{
			Type:  geosite.RuleTypeDomainKeyword,
			Value: entry.Value,
		})
	case geositeRuleTypeRegexp:
		items = append(items, geosite.Item{
			Type:  geosite.RuleTypeDomainRegex,
			Value: entry.Value,
		})
	case geositeRuleTypeDomain:
		if strings.Contains(entry.Value, ".") {
			items = append(items, geosite.Item{
				Type:  geosite.RuleTypeDomain,
				Value: entry.Value,
			})
		}
		items = append(items, geosite.Item{
			Type:  geosite.RuleTypeDomainSuffix,
			Value: "." + entry.Value,
		})
	case geositeRuleTypeFullDomain:
		items = append(items, geosite.Item{
			Type:  geosite.RuleTypeDomain,
			Value: entry.Value,
		})
	}
	return items
}

func (p *geositeProcessor) resolveList(listName string) error {
	if _, resolved := p.finalLists[listName]; resolved {
		return nil
	}
	parsedList, loaded := p.parsedLists[listName]
	if !loaded {
		return E.New("list not found")
	}
	if p.circular[listName] {
		return E.New("circular inclusion")
	}
	p.circular[listName] = true
	defer delete(p.circular, listName)

	roughMap := make(map[string]*geositeEntry)
	for _, entry := range parsedList.Entries {
		roughMap[entry.Plain] = entry
	}
	for _, inclusion := range parsedList.Inclusions {
		if err := p.resolveList(inclusion.Source); err != nil {
			return E.Cause(err, "resolve inclusion: ", inclusion.Source)
		}
		fullInclusion := len(inclusion.MustAttrs) == 0 && len(inclusion.BanAttrs) == 0
		for _, entry := range p.finalLists[inclusion.Source] {
			if fullInclusion || isMatchGeositeAttrFilters(entry, inclusion) {
				roughMap[entry.Plain] = entry
			}
		}
	}
	if len(roughMap) == 0 {
		return E.New("empty list")
	}
	p.finalLists[listName] = polishGeositeList(roughMap)
	return nil
}

func isMatchGeositeAttrFilters(entry *geositeEntry, inclusion *geositeInclusion) bool {
	if len(entry.Attrs) == 0 {
		return len(inclusion.MustAttrs) == 0
	}
	for _, attr := range inclusion.MustAttrs {
		if !slices.Contains(entry.Attrs, attr) {
			return false
		}
	}
	for _, attr := range inclusion.BanAttrs {
		if slices.Contains(entry.Attrs, attr) {
			return false
		}
	}
	return true
}

func polishGeositeList(roughMap map[string]*geositeEntry) []*geositeEntry {
	finalList := make([]*geositeEntry, 0, len(roughMap))
	queuedList := make([]*geositeEntry, 0, len(roughMap))
	domains := make(map[string]bool)
	for _, entry := range roughMap {
		switch entry.Type {
		case geositeRuleTypeRegexp, geositeRuleTypeKeyword:
			finalList = append(finalList, entry)
		case geositeRuleTypeDomain:
			domains[entry.Value] = true
			if len(entry.Attrs) != 0 {
				finalList = append(finalList, entry)
			} else {
				queuedList = append(queuedList, entry)
			}
		case geositeRuleTypeFullDomain:
			if len(entry.Attrs) != 0 {
				finalList = append(finalList, entry)
			} else {
				queuedList = append(queuedList, entry)
			}
		}
	}
	for _, entry := range queuedList {
		redundant := false
		parentDomain := entry.Value
		if entry.Type == geositeRuleTypeFullDomain {
			parentDomain = "." + parentDomain
		}
		for {
			var hasParent bool
			_, parentDomain, hasParent = strings.Cut(parentDomain, ".")
			if !hasParent {
				break
			}
			if domains[parentDomain] {
				redundant = true
				break
			}
		}
		if !redundant {
			finalList = append(finalList, entry)
		}
	}
	slices.SortFunc(finalList, func(a, b *geositeEntry) int {
		return strings.Compare(a.Plain, b.Plain)
	})
	return finalList
}

type filteredGeositeCodePair struct {
	code    string
	badCode string
}

func filterGeositeTags(data sortedStringMap[[]geosite.Item]) {
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
		badList := data.Get(it.badCode)
		if badList == nil {
			panic("bad list not found: " + it.badCode)
		}
		data.Remove(it.badCode)
		items := data.Get(it.code)
		seen := make(map[geosite.Item]struct{}, len(items))
		newList := make([]geosite.Item, 0, len(items))
		for _, item := range items {
			newList = appendUniqueGeositeItem(newList, seen, item)
		}
		for _, item := range badList {
			delete(seen, item)
		}
		newList = filterGeositeItems(newList, seen)
		data.Put(it.code, newList)
		mergedCodeMap = append(mergedCodeMap, it.badCode)
	}
	log.Info("filtered ", strings.Join(filteredCodeMap, ","), "\n")
	log.Info("merged ", strings.Join(mergedCodeMap, ","), "\n")
}

func mergeGeositeTags(data sortedStringMap[[]geosite.Item]) {
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
	seen := make(map[geosite.Item]struct{})
	var newList []geosite.Item
	cnLocation := data.Get("geolocation-cn")
	for _, item := range cnLocation {
		newList = appendUniqueGeositeItem(newList, seen, item)
	}
	for _, code := range cnCodeList {
		items := data.Get(code)
		for _, item := range items {
			newList = appendUniqueGeositeItem(newList, seen, item)
		}
	}
	data.Put("geolocation-cn", newList)
	data.Put("cn", append(newList, geosite.Item{
		Type:  geosite.RuleTypeDomainSuffix,
		Value: "cn",
	}))
	log.Info("merged cn categories: " + strings.Join(cnCodeList, ","))
}

func appendUniqueGeositeItem(items []geosite.Item, seen map[geosite.Item]struct{}, item geosite.Item) []geosite.Item {
	if _, loaded := seen[item]; loaded {
		return items
	}
	seen[item] = struct{}{}
	return append(items, item)
}

func filterGeositeItems(items []geosite.Item, keep map[geosite.Item]struct{}) []geosite.Item {
	filtered := items[:0]
	for _, item := range items {
		if _, loaded := keep[item]; loaded {
			filtered = append(filtered, item)
		}
	}
	return filtered
}
