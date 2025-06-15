package libcore

import (
	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/protocol/group"
	"github.com/sagernet/sing/common"
)

func (b *BoxInstance) SelectOutbound(groupName, tag string) (ok bool) {
	outbound, loaded := b.Outbound().Outbound(groupName)
	if !loaded {
		return false
	}
	selector, isSelector := outbound.(*group.Selector)
	if !isSelector {
		return false
	}
	return selector.SelectOutbound(tag)
}

// ProxySet is group for sing-box.
type ProxySet struct {
	Tag        string
	Type       string
	Selected   string
	Selectable bool
	items      []*GroupItem
}

func (p *ProxySet) GetItems() GroupItemIterator {
	return newIterator(p.items)
}

type ProxySetIterator interface {
	Next() *ProxySet
	HasNext() bool
	Length() int32
}

func (b *BoxInstance) QueryProxySets() ProxySetIterator {
	outboundManager := b.Outbound()
	var sets []*ProxySet
	for _, outbound := range outboundManager.Outbounds() {
		outboundGroup, isGroup := outbound.(adapter.OutboundGroup)
		if !isGroup {
			continue
		}
		sets = append(sets, buildProxySet(outboundManager, outboundGroup))
	}
	return newIterator(sets)
}

func buildProxySet(outboundManager adapter.OutboundManager, outboundGroup adapter.OutboundGroup) *ProxySet {
	_, isSelector := outboundGroup.(*group.Selector)
	return &ProxySet{
		Tag:        outboundGroup.Tag(),
		Type:       C.ProxyDisplayName(outboundGroup.Type()),
		Selected:   outboundGroup.Now(),
		Selectable: isSelector,
		items: common.Map(outboundGroup.All(), func(it string) *GroupItem {
			outbound, _ := outboundManager.Outbound(it)
			return buildGroupItem(outbound)
		}),
	}
}

type GroupItem struct {
	Tag  string
	Type string
}

type GroupItemIterator interface {
	Next() *GroupItem
	HasNext() bool
	Length() int32
}

func buildGroupItem(outbound adapter.Outbound) *GroupItem {
	return &GroupItem{
		Tag:  outbound.Tag(),
		Type: C.ProxyDisplayName(outbound.Type()),
	}
}
