package libcore

import (
	"bufio"
	"context"
	"io"
	"net/netip"

	"libcore/vario"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
)

// OpenConnect endpoint states, mirroring adapter.OpenConnectState*.
const (
	OpenConnectStateConnecting  = "connecting"
	OpenConnectStateAuthPending = "auth-pending"
	OpenConnectStateConnected   = "connected"
	OpenConnectStateError       = "error"
)

type OpenConnectEndpointStatus struct {
	Tag        string
	State      string
	Error      string
	AuthForm   *OpenConnectAuthForm
	TunnelInfo *OpenConnectTunnelInfo
}

type OpenConnectEndpointStatusIterator interface {
	Next() *OpenConnectEndpointStatus
	HasNext() bool
	Length() int32
}

type OpenConnectAuthForm struct {
	ID      string
	Banner  string
	Message string
	Error   string
	URL     string
	fields  []*OpenConnectAuthFormField
}

func (o *OpenConnectAuthForm) GetFields() OpenConnectAuthFormFieldIterator {
	return newIterator(o.fields)
}

type OpenConnectAuthFormField struct {
	SubmissionKey string
	Name          string
	Label         string
	Kind          string
	Value         string
	options       []*OpenConnectAuthFormChoice
}

func (o *OpenConnectAuthFormField) GetOptions() OpenConnectAuthFormChoiceIterator {
	return newIterator(o.options)
}

type OpenConnectAuthFormFieldIterator interface {
	Next() *OpenConnectAuthFormField
	HasNext() bool
	Length() int32
}

type OpenConnectAuthFormChoice struct {
	Value string
	Label string
}

type OpenConnectAuthFormChoiceIterator interface {
	Next() *OpenConnectAuthFormChoice
	HasNext() bool
	Length() int32
}

type OpenConnectTunnelInfo struct {
	Server         string
	Flavor         string
	Transport      string
	ipv4           []string
	ipv6           []string
	dns            []string
	MTU            int32
	ConnectedSince int64
}

func (o *OpenConnectTunnelInfo) GetIPv4() StringIterator {
	return newIterator(o.ipv4)
}

func (o *OpenConnectTunnelInfo) GetIPv6() StringIterator {
	return newIterator(o.ipv6)
}

func (o *OpenConnectTunnelInfo) GetDNS() StringIterator {
	return newIterator(o.dns)
}

// OpenConnectFormValues collects auth form answers keyed by submission key.
type OpenConnectFormValues struct {
	keys   []string
	values []string
}

func NewOpenConnectFormValues() *OpenConnectFormValues {
	return &OpenConnectFormValues{}
}

func (v *OpenConnectFormValues) Add(key, value string) {
	v.keys = append(v.keys, key)
	v.values = append(v.values, value)
}

type OpenConnectStatusCallback interface {
	OnStatusUpdate(statuses OpenConnectEndpointStatusIterator)
}

// SubscribeOpenConnectStatus streams full snapshots of all OpenConnect
// endpoint statuses until the connection is closed.
func (c *Client) SubscribeOpenConnectStatus(callback OpenConnectStatusCallback) error {
	err := vario.WriteUint8(c.conn, commandSubscribeOpenConnectStatus)
	if err != nil {
		return E.Cause(err, "write command")
	}
	for {
		statuses, err := vario.ReadSlices(c.conn, readOpenConnectEndpointStatus)
		if err != nil {
			if E.IsClosed(err) {
				return nil
			}
			return E.Cause(err, "read openconnect statuses")
		}
		callback.OnStatusUpdate(newIterator(statuses))
	}
}

func (c *Client) CompleteOpenConnectAuthForm(tag, formID string, values *OpenConnectFormValues) error {
	err := vario.WriteUint8(c.conn, commandCompleteOpenConnectAuthForm)
	if err != nil {
		return E.Cause(err, "write command")
	}
	err = vario.WriteString(c.conn, tag)
	if err != nil {
		return E.Cause(err, "write tag")
	}
	err = vario.WriteString(c.conn, formID)
	if err != nil {
		return E.Cause(err, "write form id")
	}
	var keys, vals []string
	if values != nil {
		keys, vals = values.keys, values.values
	}
	err = vario.WriteStringSlice(c.conn, keys)
	if err != nil {
		return E.Cause(err, "write keys")
	}
	err = vario.WriteStringSlice(c.conn, vals)
	if err != nil {
		return E.Cause(err, "write values")
	}
	return c.readOpenConnectResult()
}

func (c *Client) CancelOpenConnectAuthForm(tag, formID string) error {
	err := vario.WriteUint8(c.conn, commandCancelOpenConnectAuthForm)
	if err != nil {
		return E.Cause(err, "write command")
	}
	err = vario.WriteString(c.conn, tag)
	if err != nil {
		return E.Cause(err, "write tag")
	}
	err = vario.WriteString(c.conn, formID)
	if err != nil {
		return E.Cause(err, "write form id")
	}
	return c.readOpenConnectResult()
}

func (c *Client) readOpenConnectResult() error {
	result, err := vario.ReadUint8(c.conn)
	if err != nil {
		return E.Cause(err, "read result")
	}
	if result == resultNoError {
		return nil
	}
	message, err := vario.ReadString(c.conn)
	if err != nil {
		return E.Cause(err, "read error message")
	}
	return E.New(message)
}

func (s *Service) handleSubscribeOpenConnectStatus(conn io.ReadWriter, instance *boxInstance) error {
	var endpoints []adapter.OpenConnectEndpoint
	for _, endpoint := range instance.Endpoint().Endpoints() {
		if openConnect, isOpenConnect := endpoint.(adapter.OpenConnectEndpoint); isOpenConnect {
			endpoints = append(endpoints, openConnect)
		}
	}
	writer := bufio.NewWriter(conn)
	writeSnapshot := func() error {
		statuses := common.Map(endpoints, func(endpoint adapter.OpenConnectEndpoint) *OpenConnectEndpointStatus {
			return buildOpenConnectStatus(endpoint)
		})
		err := vario.WriteSlices(writer, statuses)
		if err != nil {
			return E.Cause(err, "write openconnect statuses")
		}
		return writer.Flush()
	}
	if len(endpoints) == 0 {
		// Keep the stream open with an empty snapshot so the client
		// shows an empty list instead of reconnecting in a loop.
		err := writeSnapshot()
		if err != nil {
			return err
		}
		<-instance.ctx.Done()
		return nil
	}

	ctx, cancel := context.WithCancel(instance.ctx)
	defer cancel()
	updated := make(chan struct{}, 1)
	for _, endpoint := range endpoints {
		go func(endpoint adapter.OpenConnectEndpoint) {
			for {
				statusUpdated := endpoint.StatusUpdated()
				select {
				case updated <- struct{}{}:
				default:
				}
				select {
				case <-statusUpdated:
				case <-ctx.Done():
					return
				}
			}
		}(endpoint)
	}
	for {
		select {
		case <-updated:
		case <-ctx.Done():
			return nil
		}
		err := writeSnapshot()
		if err != nil {
			return err
		}
	}
}

func (s *Service) handleCompleteOpenConnectAuthForm(conn io.ReadWriter, instance *boxInstance) error {
	tag, err := vario.ReadString(conn)
	if err != nil {
		return E.Cause(err, "read tag")
	}
	formID, err := vario.ReadString(conn)
	if err != nil {
		return E.Cause(err, "read form id")
	}
	keys, err := vario.ReadStringSlice(conn)
	if err != nil {
		return E.Cause(err, "read keys")
	}
	values, err := vario.ReadStringSlice(conn)
	if err != nil {
		return E.Cause(err, "read values")
	}
	if len(keys) != len(values) {
		return writeOpenConnectResult(conn, E.New("mismatched form keys and values"))
	}
	endpoint, err := requireOpenConnectEndpoint(instance, tag)
	if err != nil {
		return writeOpenConnectResult(conn, err)
	}
	formValues := make(map[string]string, len(keys))
	for i, key := range keys {
		formValues[key] = values[i]
	}
	return writeOpenConnectResult(conn, endpoint.CompleteAuthForm(formID, formValues))
}

func (s *Service) handleCancelOpenConnectAuthForm(conn io.ReadWriter, instance *boxInstance) error {
	tag, err := vario.ReadString(conn)
	if err != nil {
		return E.Cause(err, "read tag")
	}
	formID, err := vario.ReadString(conn)
	if err != nil {
		return E.Cause(err, "read form id")
	}
	endpoint, err := requireOpenConnectEndpoint(instance, tag)
	if err != nil {
		return writeOpenConnectResult(conn, err)
	}
	return writeOpenConnectResult(conn, endpoint.CancelAuthForm(formID))
}

func requireOpenConnectEndpoint(instance *boxInstance, tag string) (adapter.OpenConnectEndpoint, error) {
	endpoint, loaded := instance.Endpoint().Get(tag)
	if !loaded {
		return nil, E.New("endpoint not found: ", tag)
	}
	openConnect, isOpenConnect := endpoint.(adapter.OpenConnectEndpoint)
	if !isOpenConnect {
		return nil, E.New("endpoint is not OpenConnect: ", tag)
	}
	return openConnect, nil
}

func writeOpenConnectResult(conn io.Writer, err error) error {
	if err == nil {
		return vario.WriteUint8(conn, resultNoError)
	}
	writeErr := vario.WriteUint8(conn, resultCommonError)
	if writeErr != nil {
		return E.Cause(writeErr, "write result")
	}
	return vario.WriteString(conn, err.Error())
}

func buildOpenConnectStatus(endpoint adapter.OpenConnectEndpoint) *OpenConnectEndpointStatus {
	endpointStatus := endpoint.OpenConnectStatus()
	status := &OpenConnectEndpointStatus{
		Tag:   endpoint.Tag(),
		State: endpointStatus.State,
		Error: endpointStatus.Error,
	}
	if authForm := endpointStatus.AuthForm; authForm != nil {
		status.AuthForm = &OpenConnectAuthForm{
			ID:      authForm.ID,
			Banner:  authForm.Banner,
			Message: authForm.Message,
			Error:   authForm.Error,
			URL:     authForm.URL,
			fields: common.Map(authForm.Fields, func(field adapter.OpenConnectAuthFormField) *OpenConnectAuthFormField {
				return &OpenConnectAuthFormField{
					SubmissionKey: field.SubmissionKey,
					Name:          field.Name,
					Label:         field.Label,
					Kind:          field.Kind,
					Value:         field.Value,
					options: common.Map(field.Options, func(choice adapter.OpenConnectAuthFormChoice) *OpenConnectAuthFormChoice {
						return &OpenConnectAuthFormChoice{
							Value: choice.Value,
							Label: choice.Label,
						}
					}),
				}
			}),
		}
	}
	if tunnelInfo := endpointStatus.TunnelInfo; tunnelInfo != nil {
		status.TunnelInfo = &OpenConnectTunnelInfo{
			Server:         tunnelInfo.Server,
			Flavor:         tunnelInfo.Flavor,
			Transport:      tunnelInfo.Transport,
			ipv4:           common.Map(tunnelInfo.IPv4, netip.Prefix.String),
			ipv6:           common.Map(tunnelInfo.IPv6, netip.Prefix.String),
			dns:            common.Map(tunnelInfo.DNS, netip.Addr.String),
			MTU:            int32(tunnelInfo.MTU),
			ConnectedSince: tunnelInfo.ConnectedSince.Unix(),
		}
	}
	return status
}

func (s *OpenConnectEndpointStatus) WriteToBinary(writer io.Writer) error {
	err := vario.WriteString(writer, s.Tag)
	if err != nil {
		return E.Cause(err, "write tag")
	}
	err = vario.WriteString(writer, s.State)
	if err != nil {
		return E.Cause(err, "write state")
	}
	err = vario.WriteString(writer, s.Error)
	if err != nil {
		return E.Cause(err, "write error")
	}
	err = vario.WriteBool(writer, s.AuthForm != nil)
	if err != nil {
		return E.Cause(err, "write auth form flag")
	}
	if s.AuthForm != nil {
		err = s.AuthForm.writeToBinary(writer)
		if err != nil {
			return E.Cause(err, "write auth form")
		}
	}
	err = vario.WriteBool(writer, s.TunnelInfo != nil)
	if err != nil {
		return E.Cause(err, "write tunnel info flag")
	}
	if s.TunnelInfo != nil {
		err = s.TunnelInfo.writeToBinary(writer)
		if err != nil {
			return E.Cause(err, "write tunnel info")
		}
	}
	return nil
}

func readOpenConnectEndpointStatus(reader io.Reader) (*OpenConnectEndpointStatus, error) {
	tag, err := vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read tag")
	}
	state, err := vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read state")
	}
	statusError, err := vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read error")
	}
	status := &OpenConnectEndpointStatus{
		Tag:   tag,
		State: state,
		Error: statusError,
	}
	hasAuthForm, err := vario.ReadBool(reader)
	if err != nil {
		return nil, E.Cause(err, "read auth form flag")
	}
	if hasAuthForm {
		status.AuthForm, err = readOpenConnectAuthForm(reader)
		if err != nil {
			return nil, E.Cause(err, "read auth form")
		}
	}
	hasTunnelInfo, err := vario.ReadBool(reader)
	if err != nil {
		return nil, E.Cause(err, "read tunnel info flag")
	}
	if hasTunnelInfo {
		status.TunnelInfo, err = readOpenConnectTunnelInfo(reader)
		if err != nil {
			return nil, E.Cause(err, "read tunnel info")
		}
	}
	return status, nil
}

func (o *OpenConnectAuthForm) writeToBinary(writer io.Writer) error {
	err := vario.WriteString(writer, o.ID)
	if err != nil {
		return err
	}
	err = vario.WriteString(writer, o.Banner)
	if err != nil {
		return err
	}
	err = vario.WriteString(writer, o.Message)
	if err != nil {
		return err
	}
	err = vario.WriteString(writer, o.Error)
	if err != nil {
		return err
	}
	err = vario.WriteString(writer, o.URL)
	if err != nil {
		return err
	}
	return vario.WriteSlices(writer, o.fields)
}

func readOpenConnectAuthForm(reader io.Reader) (*OpenConnectAuthForm, error) {
	form := &OpenConnectAuthForm{}
	var err error
	form.ID, err = vario.ReadString(reader)
	if err != nil {
		return nil, err
	}
	form.Banner, err = vario.ReadString(reader)
	if err != nil {
		return nil, err
	}
	form.Message, err = vario.ReadString(reader)
	if err != nil {
		return nil, err
	}
	form.Error, err = vario.ReadString(reader)
	if err != nil {
		return nil, err
	}
	form.URL, err = vario.ReadString(reader)
	if err != nil {
		return nil, err
	}
	fields, err := vario.ReadSlices(reader, readOpenConnectAuthFormField)
	if err != nil {
		return nil, E.Cause(err, "read fields")
	}
	form.fields = fields
	return form, nil
}

func (o *OpenConnectAuthFormField) WriteToBinary(writer io.Writer) error {
	err := vario.WriteString(writer, o.SubmissionKey)
	if err != nil {
		return err
	}
	err = vario.WriteString(writer, o.Name)
	if err != nil {
		return err
	}
	err = vario.WriteString(writer, o.Label)
	if err != nil {
		return err
	}
	err = vario.WriteString(writer, o.Kind)
	if err != nil {
		return err
	}
	err = vario.WriteString(writer, o.Value)
	if err != nil {
		return err
	}
	return vario.WriteSlices(writer, o.options)
}

func readOpenConnectAuthFormField(reader io.Reader) (*OpenConnectAuthFormField, error) {
	field := &OpenConnectAuthFormField{}
	var err error
	field.SubmissionKey, err = vario.ReadString(reader)
	if err != nil {
		return nil, err
	}
	field.Name, err = vario.ReadString(reader)
	if err != nil {
		return nil, err
	}
	field.Label, err = vario.ReadString(reader)
	if err != nil {
		return nil, err
	}
	field.Kind, err = vario.ReadString(reader)
	if err != nil {
		return nil, err
	}
	field.Value, err = vario.ReadString(reader)
	if err != nil {
		return nil, err
	}
	options, err := vario.ReadSlices(reader, readOpenConnectAuthFormChoice)
	if err != nil {
		return nil, E.Cause(err, "read options")
	}
	field.options = options
	return field, nil
}

func (c *OpenConnectAuthFormChoice) WriteToBinary(writer io.Writer) error {
	err := vario.WriteString(writer, c.Value)
	if err != nil {
		return E.Cause(err, "write value")
	}
	return vario.WriteString(writer, c.Label)
}

func readOpenConnectAuthFormChoice(reader io.Reader) (*OpenConnectAuthFormChoice, error) {
	value, err := vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read value")
	}
	label, err := vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read label")
	}
	return &OpenConnectAuthFormChoice{
		Value: value,
		Label: label,
	}, nil
}

func (o *OpenConnectTunnelInfo) writeToBinary(writer io.Writer) error {
	err := vario.WriteString(writer, o.Server)
	if err != nil {
		return err
	}
	err = vario.WriteString(writer, o.Flavor)
	if err != nil {
		return err
	}
	err = vario.WriteString(writer, o.Transport)
	if err != nil {
		return err
	}
	err = vario.WriteStringSlice(writer, o.ipv4)
	if err != nil {
		return err
	}
	err = vario.WriteStringSlice(writer, o.ipv6)
	if err != nil {
		return err
	}
	err = vario.WriteStringSlice(writer, o.dns)
	if err != nil {
		return err
	}
	err = vario.WriteInt32(writer, o.MTU)
	if err != nil {
		return E.Cause(err, "write mtu")
	}
	return vario.WriteInt64(writer, o.ConnectedSince)
}

func readOpenConnectTunnelInfo(reader io.Reader) (*OpenConnectTunnelInfo, error) {
	info := &OpenConnectTunnelInfo{}
	var err error
	info.Server, err = vario.ReadString(reader)
	if err != nil {
		return nil, err
	}
	info.Flavor, err = vario.ReadString(reader)
	if err != nil {
		return nil, err
	}
	info.Transport, err = vario.ReadString(reader)
	if err != nil {
		return nil, err
	}
	info.ipv4, err = vario.ReadStringSlice(reader)
	if err != nil {
		return nil, err
	}
	info.ipv6, err = vario.ReadStringSlice(reader)
	if err != nil {
		return nil, err
	}
	info.dns, err = vario.ReadStringSlice(reader)
	if err != nil {
		return nil, err
	}
	mtu, err := vario.ReadInt32(reader)
	if err != nil {
		return nil, E.Cause(err, "read mtu")
	}
	info.MTU = mtu
	connectedSince, err := vario.ReadInt64(reader)
	if err != nil {
		return nil, E.Cause(err, "read connected since")
	}
	info.ConnectedSince = connectedSince
	return info, nil
}
