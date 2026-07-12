package libcore

import (
	"bufio"
	"io"

	"libcore/plugin/pluginoption"
	"libcore/vario"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/urltest"
	"github.com/sagernet/sing-box/protocol/group"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/observable"
)

func (c *Client) SelectOutbound(groupName, tag string) error {
	err := vario.WriteUint8(c.conn, commandSelectOutbound)
	if err != nil {
		return E.Cause(err, "write command")
	}
	err = vario.WriteString(c.conn, groupName)
	if err != nil {
		return E.Cause(err, "write group name")
	}
	err = vario.WriteString(c.conn, tag)
	if err != nil {
		return E.Cause(err, "write tag")
	}
	return nil
}

func (s *Service) handleSelectOutbound(conn io.ReadWriter, instance *boxInstance) error {
	groupName, err := vario.ReadString(conn)
	if err != nil {
		return E.Cause(err, "read group name")
	}
	tag, err := vario.ReadString(conn)
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
	historyStorage := b.urlTestHistory
	if historyStorage == nil {
		return
	}

	tagCache := make(map[string]string, len(urlTests)) // group:current_tag
	for _, urlTest := range urlTests {
		tagCache[urlTest.Tag()] = urlTest.Now()
	}

	hook := observable.NewSubscriber[struct{}](1) // Prevent not receive notification when checking
	historyStorage.AddUpdateHook(hook)
	subscription, done := hook.Subscription()
	go func() {
		defer hook.Close()
		for {
			select {
			case <-b.ctx.Done():
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
	Items      []*ProxyItem
}

func (p *ProxySet) GetItems() ProxyItemIterator {
	return newIterator(p.Items)
}

type ProxySetIterator interface {
	Next() *ProxySet
	HasNext() bool
	Length() int32
}

func (c *Client) QueryProxySets() (ProxySetIterator, error) {
	err := vario.WriteUint8(c.conn, commandQueryProxySets)
	if err != nil {
		return nil, E.Cause(err, "write command")
	}
	proxySets, err := vario.ReadSlices(c.conn, readProxySet)
	if err != nil {
		return nil, E.Cause(err, "read proxy sets")
	}
	return newIterator(proxySets), nil
}

func (c *Client) QueryAllProxyItems() (ProxyItemIterator, error) {
	err := vario.WriteUint8(c.conn, commandQueryAllProxy)
	if err != nil {
		return nil, E.Cause(err, "write command")
	}
	items, err := vario.ReadSlices(c.conn, readProxyItem)
	if err != nil {
		return nil, E.Cause(err, "read proxy items")
	}
	return newIterator(items), nil
}

func (s *Service) handleQueryProxySets(conn io.ReadWriter, instance *boxInstance) error {
	outboundManager := instance.Outbound()
	historyStorage := instance.urlTestHistory
	var proxySets []*ProxySet
	for _, outbound := range outboundManager.Outbounds() {
		outboundGroup, isGroup := outbound.(adapter.OutboundGroup)
		if !isGroup {
			continue
		}
		proxySets = append(proxySets, buildProxySet(outboundManager, outboundGroup, historyStorage))
	}
	writer := bufio.NewWriter(conn)
	err := vario.WriteSlices(writer, proxySets)
	if err != nil {
		return E.Cause(err, "write proxy sets")
	}
	return writer.Flush()
}

func (s *Service) handleQueryAllProxyItems(conn io.ReadWriter, instance *boxInstance) error {
	outbounds := instance.Outbound().Outbounds()
	endpoints := instance.Endpoint().Endpoints()
	historyStorage := instance.urlTestHistory
	items := make([]*ProxyItem, 0, len(outbounds)+len(endpoints))
	for _, outbound := range outbounds {
		items = append(items, buildProxyItem(outbound, historyStorage))
	}
	for _, endpoint := range endpoints {
		items = append(items, buildProxyItem(endpoint, historyStorage))
	}
	writer := bufio.NewWriter(conn)
	err := vario.WriteSlices(writer, items)
	if err != nil {
		return E.Cause(err, "write proxy items")
	}
	return writer.Flush()
}

func buildProxySet(outboundManager adapter.OutboundManager, outboundGroup adapter.OutboundGroup, historyStorage *urltest.HistoryStorage) *ProxySet {
	_, isSelector := outboundGroup.(*group.Selector)
	return &ProxySet{
		Tag:        outboundGroup.Tag(),
		Type:       pluginoption.ProxyDisplayName(outboundGroup.Type()),
		Selected:   outboundGroup.Now(),
		Selectable: isSelector,
		Items: common.Map(outboundGroup.All(), func(it string) *ProxyItem {
			outbound, _ := outboundManager.Outbound(it)
			return buildProxyItem(outbound, historyStorage)
		}),
	}
}

type ProxyItem struct {
	Tag   string
	Type  string
	Delay int32
}

type ProxyItemIterator interface {
	Next() *ProxyItem
	HasNext() bool
	Length() int32
}

func buildProxyItem(outbound adapter.Outbound, historyStorage *urltest.HistoryStorage) *ProxyItem {
	var delay int32
	if historyStorage != nil {
		if history := historyStorage.LoadURLTestHistory(outbound.Tag()); history != nil {
			delay = int32(history.Delay)
		}
	}
	return &ProxyItem{
		Tag:   outbound.Tag(),
		Type:  pluginoption.ProxyDisplayName(outbound.Type()),
		Delay: delay,
	}
}

func (p *ProxySet) WriteToBinary(writer io.Writer) error {
	err := vario.WriteString(writer, p.Tag)
	if err != nil {
		return E.Cause(err, "write tag")
	}
	err = vario.WriteString(writer, p.Type)
	if err != nil {
		return E.Cause(err, "write type")
	}
	err = vario.WriteString(writer, p.Selected)
	if err != nil {
		return E.Cause(err, "write selected")
	}
	err = vario.WriteBool(writer, p.Selectable)
	if err != nil {
		return E.Cause(err, "write selectable")
	}
	err = vario.WriteSlices(writer, p.Items)
	if err != nil {
		return E.Cause(err, "write items")
	}
	return nil
}

func readProxySet(reader io.Reader) (*ProxySet, error) {
	tag, err := vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read tag")
	}
	proxyType, err := vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read type")
	}
	selected, err := vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read selected")
	}
	selectable, err := vario.ReadBool(reader)
	if err != nil {
		return nil, E.Cause(err, "read selectable")
	}
	items, err := vario.ReadSlices(reader, readProxyItem)
	if err != nil {
		return nil, E.Cause(err, "read items")
	}
	return &ProxySet{
		Tag:        tag,
		Type:       proxyType,
		Selected:   selected,
		Selectable: selectable,
		Items:      items,
	}, nil
}

func (g *ProxyItem) WriteToBinary(writer io.Writer) error {
	err := vario.WriteString(writer, g.Tag)
	if err != nil {
		return E.Cause(err, "write tag")
	}
	err = vario.WriteString(writer, g.Type)
	if err != nil {
		return E.Cause(err, "write type")
	}
	err = vario.WriteInt32(writer, g.Delay)
	if err != nil {
		return E.Cause(err, "write delay")
	}
	return nil
}

func readProxyItem(reader io.Reader) (*ProxyItem, error) {
	tag, err := vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read tag")
	}
	itemType, err := vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read type")
	}
	delay, err := vario.ReadInt32(reader)
	if err != nil {
		return nil, E.Cause(err, "read delay")
	}
	return &ProxyItem{
		Tag:   tag,
		Type:  itemType,
		Delay: delay,
	}, nil
}
