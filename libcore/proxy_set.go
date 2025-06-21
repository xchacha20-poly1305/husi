package libcore

import (
	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/protocol/group"
	"github.com/sagernet/sing/common"
)

// SelectOutbound attempts to select a specific outbound tag within a given group.
func (b *BoxInstance) SelectOutbound(groupName, tag string) (ok bool) {
	outbound, loaded := b.Outbound().Outbound(groupName)
	if !loaded {
		return false
	}
	selector, isSelector := outbound.(*group.Selector)
	if !isSelector {
		return false
	}
	old := selector.Now()
	ok = selector.SelectOutbound(tag)
	if !ok {
		return false
	}
	b.platformInterface.OnGroupSelectedChange(groupName, old, tag)
	return true
}

// watchGroupChange monitors changes in group selections, particularly for URLTest and Selector
// outbound types, and notifies the platform interface about these changes.
func (b *BoxInstance) watchGroupChange() {
	var urlTests []*group.URLTest
	for _, outbound := range b.Outbound().Outbounds() {
		switch outbound.(type) {
		case *group.Selector:
			selector := outbound.(*group.Selector)
			// Initialize
			b.platformInterface.OnGroupSelectedChange(selector.Tag(), "", selector.Now())
		case *group.URLTest:
			urlTest := outbound.(*group.URLTest)
			urlTests = append(urlTests, urlTest)
		}
	}
	if len(urlTests) == 0 {
		return
	}

	tagCache := make(map[string]string, len(urlTests)) // group:current_tag
	check := func() {
		for _, urlTest := range urlTests {
			groupName := urlTest.Tag()
			old := tagCache[groupName]
			now := urlTest.Now()
			tagCache[groupName] = now
			if old != now {
				b.platformInterface.OnGroupSelectedChange(groupName, old, now)
			}
		}
	}
	check()

	hook := make(chan struct{}, 1) // Prevent not receive notify when checking
	b.api.HistoryStorage().SetHook(hook)
	go func() {
		for {
			select {
			case <-b.ctx.Done():
				return
			case <-hook:
			drainLoop:
				for {
					select {
					case <-hook:
					default:
						break drainLoop
					}
				}
			}

			check()
		}
	}()
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
