package libcore

import (
	"io"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/protocol/group"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/binary"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/observable"
	"github.com/sagernet/sing/common/varbin"

	"libcore/plugin/pluginoption"
)

func (c *Client) SelectOutbound(groupName, tag string) error {
	err := varbin.Write(c.conn, binary.BigEndian, commandSelectOutbound)
	if err != nil {
		return E.Cause(err, "write command")
	}
	err = varbin.Write(c.conn, binary.BigEndian, groupName)
	if err != nil {
		return E.Cause(err, "write group name")
	}
	err = varbin.Write(c.conn, binary.BigEndian, tag)
	if err != nil {
		return E.Cause(err, "write tag")
	}
	return nil
}

func (s *Service) handleSelectOutbound(conn io.ReadWriter, instance *boxInstance) error {
	groupName, err := varbin.ReadValue[string](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read group name")
	}
	tag, err := varbin.ReadValue[string](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read tag")
	}
	outbound, loaded := instance.Outbound().Outbound(groupName)
	if !loaded {
		return nil
	}
	selector, isSelector := outbound.(*group.Selector)
	if !isSelector {
		return nil
	}
	old := selector.Now()
	_ = selector.SelectOutbound(tag)
	s.platformInterface.OnGroupSelectedChange(groupName, old, tag)
	return nil
}

// InitializeProxySet initializes the proxy set by iterating through all outbounds
// and identifying outbound groups.
func (b *boxInstance) InitializeProxySet() {
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
func (b *boxInstance) watchGroupChange(urlTests []*group.URLTest) {
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

func (c *Client) QueryProxySets() (ProxySetIterator, error) {
	err := varbin.Write(c.conn, binary.BigEndian, commandQueryProxySets)
	if err != nil {
		return nil, E.Cause(err, "write command")
	}
	proxySets, err := varbin.ReadValue[[]*ProxySet](c.conn, binary.BigEndian)
	if err != nil {
		return nil, E.Cause(err, "read proxy sets")
	}
	return newIterator(proxySets), nil
}

func (s *Service) handleQueryProxySets(conn io.ReadWriter, instance *boxInstance) error {
	outboundManager := instance.Outbound()
	var proxySets []*ProxySet
	for _, outbound := range outboundManager.Outbounds() {
		outboundGroup, isGroup := outbound.(adapter.OutboundGroup)
		if !isGroup {
			continue
		}
		proxySets = append(proxySets, buildProxySet(outboundManager, outboundGroup))
	}
	err := varbin.Write(conn, binary.BigEndian, proxySets)
	if err != nil {
		return E.Cause(err, "write proxy sets")
	}
	return nil
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
