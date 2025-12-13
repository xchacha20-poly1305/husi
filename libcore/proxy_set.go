package libcore

import (
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/protocol/group"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/observable"

	"libcore/plugin/pluginoption"
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

// InitializeProxySet initializes the proxy set by iterating through all outbounds
// and identifying outbound groups.
func (b *BoxInstance) InitializeProxySet() {
	var urlTests []*group.URLTest
	for _, outbound := range b.Outbound().Outbounds() {
		if outboundGroup, isGroup := outbound.(adapter.OutboundGroup); isGroup {
			b.platformInterface.OnGroupSelectedChange(outboundGroup.Tag(), "", outboundGroup.Now())
			if urlTest, isURLTest := outboundGroup.(*group.URLTest); isURLTest {
				urlTests = append(urlTests, urlTest)
			}
		}
	}
	if len(urlTests) > 0 {
		b.watchGroupChange(urlTests)
	}
}

// watchGroupChange monitors changes in the selected outbound for URLTest groups.
func (b *BoxInstance) watchGroupChange(urlTests []*group.URLTest) {
	tagCache := make(map[string]string, len(urlTests)) // group:current_tag
	for _, urlTest := range urlTests {
		tagCache[urlTest.Tag()] = urlTest.Now()
	}

	hook := observable.NewSubscriber[struct{}](1) // Prevent not receive notification when checking
	b.api.HistoryStorage().SetHook(hook)
	subscription, done := hook.Subscription()
	go func() {
		for {
			select {
			case <-b.ctx.Done():
				_ = hook.Close()
				return
			case <-done:
				return
			case <-subscription:
			drainLoop:
				// clean up hook channel
				for {
					select {
					case <-subscription:
					default:
						break drainLoop
					}
				}
			}

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
		Type:       pluginoption.ProxyDisplayName(outboundGroup.Type()),
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
		Type: pluginoption.ProxyDisplayName(outbound.Type()),
	}
}
