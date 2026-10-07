package main

import (
	"fmt"
	"slices"
	"testing"

	"github.com/sagernet/sing-box/common/geosite"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/x/linkedhashmap"
)

var geositeBenchmarkItemCount int

func BenchmarkGeositeAttributeAggregation(b *testing.B) {
	entries := makeBenchmarkGeositeEntries(8192)

	b.Run("linkedhashmap", func(b *testing.B) {
		for range b.N {
			geositeBenchmarkItemCount = benchmarkAggregateAttrsLinkedHashMap(entries)
		}
	})

	b.Run("map_sort", func(b *testing.B) {
		for range b.N {
			geositeBenchmarkItemCount = benchmarkAggregateAttrsMapSort(entries)
		}
	})
}

func BenchmarkGeositeItemDedup(b *testing.B) {
	items := makeBenchmarkGeositeItems(32768)

	b.Run("linkedhashmap", func(b *testing.B) {
		for range b.N {
			geositeBenchmarkItemCount = len(benchmarkDedupItemsLinkedHashMap(items))
		}
	})

	b.Run("map_slice", func(b *testing.B) {
		for range b.N {
			geositeBenchmarkItemCount = len(benchmarkDedupItemsMapSlice(items))
		}
	})

	b.Run("map_slice_no_cap", func(b *testing.B) {
		for range b.N {
			geositeBenchmarkItemCount = len(benchmarkDedupItemsMapSliceNoCap(items))
		}
	})
}

func makeBenchmarkGeositeEntries(count int) []*geositeEntry {
	entries := make([]*geositeEntry, 0, count)
	attrNames := []string{
		"ads", "cn", "media", "games", "bank", "cloud", "dev", "edu",
		"mail", "news", "social", "stream", "tracker", "vpn", "work",
	}
	for index := range count {
		attrs := []string{
			attrNames[index%len(attrNames)],
			attrNames[(index*7)%len(attrNames)],
			fmt.Sprintf("group-%03d", index%512),
		}
		slices.Sort(attrs)
		entries = append(entries, &geositeEntry{
			Type:  geositeRuleTypeDomain,
			Value: fmt.Sprintf("example-%05d.com", index),
			Attrs: attrs,
			Plain: fmt.Sprintf("domain:example-%05d.com", index),
		})
	}
	return entries
}

func makeBenchmarkGeositeItems(count int) []geosite.Item {
	items := make([]geosite.Item, 0, count)
	for index := range count {
		items = append(items, geosite.Item{
			Type:  geosite.RuleTypeDomain,
			Value: fmt.Sprintf("example-%05d.com", index%8192),
		})
	}
	return items
}

func benchmarkAggregateAttrsLinkedHashMap(entries []*geositeEntry) int {
	attributes := new(linkedhashmap.Map[string, []*geositeEntry])
	for _, entry := range entries {
		for _, attr := range entry.Attrs {
			oldEntries, _ := attributes.Get(attr)
			attributes.Put(attr, append(oldEntries, entry))
		}
	}
	itemCount := 0
	for _, attrEntries := range attributes.Entries() {
		attrItems := make([]geosite.Item, 0, len(attrEntries.Value)*2)
		for _, entry := range attrEntries.Value {
			attrItems = appendGeositeItems(attrItems, entry)
		}
		itemCount += len(common.Uniq(attrItems))
	}
	return itemCount
}

func benchmarkAggregateAttrsMapSort(entries []*geositeEntry) int {
	attributes := make(map[string][]*geositeEntry)
	for _, entry := range entries {
		for _, attr := range entry.Attrs {
			attributes[attr] = append(attributes[attr], entry)
		}
	}
	attrNames := make([]string, 0, len(attributes))
	for attrName := range attributes {
		attrNames = append(attrNames, attrName)
	}
	slices.Sort(attrNames)
	itemCount := 0
	for _, attrName := range attrNames {
		attrEntries := attributes[attrName]
		attrItems := make([]geosite.Item, 0, len(attrEntries)*2)
		for _, entry := range attrEntries {
			attrItems = appendGeositeItems(attrItems, entry)
		}
		itemCount += len(common.Uniq(attrItems))
	}
	return itemCount
}

func benchmarkDedupItemsLinkedHashMap(items []geosite.Item) []geosite.Item {
	newMap := new(linkedhashmap.Map[geosite.Item, bool])
	for _, item := range items {
		newMap.Put(item, true)
	}
	return newMap.Keys()
}

func benchmarkDedupItemsMapSlice(items []geosite.Item) []geosite.Item {
	seen := make(map[geosite.Item]struct{}, len(items))
	newList := make([]geosite.Item, 0, len(items))
	for _, item := range items {
		if _, exists := seen[item]; exists {
			continue
		}
		seen[item] = struct{}{}
		newList = append(newList, item)
	}
	return newList
}

func benchmarkDedupItemsMapSliceNoCap(items []geosite.Item) []geosite.Item {
	seen := make(map[geosite.Item]struct{})
	var newList []geosite.Item
	for _, item := range items {
		if _, exists := seen[item]; exists {
			continue
		}
		seen[item] = struct{}{}
		newList = append(newList, item)
	}
	return newList
}
