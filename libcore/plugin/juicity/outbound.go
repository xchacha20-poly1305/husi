package juicity

import (
	"bytes"
	"context"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"net"
	"os"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/common/dialer"
	"github.com/sagernet/sing-box/common/tls"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"

	"libcore/plugin/pluginoption"
	"libcore/plugin/raybridge"

	"github.com/dyhkwong/sing-juicity"
	"github.com/gofrs/uuid/v5"
)

func RegisterOutbound(registry *outbound.Registry) {
	outbound.Register[pluginoption.JuicityOutboundOptions](registry, pluginoption.TypeJuicity, NewOutbound)
}

var _ adapter.Outbound = (*Outbound)(nil)

type Outbound struct {
	outbound.Adapter
	logger logger.ContextLogger
	client *juicity.Client
}

func NewOutbound(ctx context.Context, router adapter.Router, logger log.ContextLogger, tag string, options pluginoption.JuicityOutboundOptions) (adapter.Outbound, error) {
	if options.TLS == nil || !options.TLS.Enabled {
		return nil, C.ErrTLSRequired
	}
	outboundDialer, err := dialer.NewWithOptions(dialer.Options{
		Context:        ctx,
		Options:        options.DialerOptions,
		RemoteIsDomain: options.ServerIsDomain(),
	})
	if err != nil {
		return nil, err
	}
	if options.TLS.ALPN == nil { // not len(options.TLS.ALPN) > 0
		options.TLS.ALPN = []string{"h3"}
	}
	tlsConfig, err := tls.NewSTDClient(ctx, logger, options.Server, *options.TLS)
	if err != nil {
		return nil, err
	}
	if options.PinCertSha256 != "" {
		pinCertSha256, err := tryDecodeBase64(options.PinCertSha256)
		if err != nil {
			return nil, E.Cause(err, "decode pin cert sha256")
		}
		stdTLSConfig, _ := tlsConfig.STDConfig()
		stdTLSConfig.VerifyPeerCertificate = func(rawCerts [][]byte, verifiedChains [][]*x509.Certificate) error {
			peerHash := raybridge.CertChainHash(rawCerts)
			if !bytes.Equal(pinCertSha256, peerHash) {
				return E.New("peer cert hash mismatch")
			}
			return nil
		}
		stdTLSConfig.InsecureSkipVerify = true
	}
	uuidInstance, err := uuid.FromString(options.UUID)
	if err != nil {
		return nil, err
	}
	client, err := juicity.NewClient(juicity.ClientOptions{
		Context:       ctx,
		Dialer:        outboundDialer,
		ServerAddress: options.Build(),
		TLSConfig:     tlsConfig,
		UUID:          [uuid.Size]byte(uuidInstance.Bytes()),
		Password:      options.Password,
	})
	if err != nil {
		return nil, err
	}
	return &Outbound{
		Adapter: outbound.NewAdapterWithDialerOptions(pluginoption.TypeJuicity, tag, []string{N.NetworkTCP, N.NetworkUDP}, options.DialerOptions),
		logger:  logger,
		client:  client,
	}, nil
}

func (o *Outbound) Close() error {
	return o.client.CloseWithError(os.ErrClosed)
}

func (o *Outbound) DialContext(ctx context.Context, network string, destination M.Socksaddr) (net.Conn, error) {
	switch network {
	case N.NetworkTCP:
		ctx, metadata := adapter.ExtendContext(ctx)
		metadata.Outbound = o.Tag()
		metadata.Destination = destination
		o.logger.InfoContext(ctx, "outbound connection to ", destination)
		return o.client.DialConn(ctx, destination)
	case N.NetworkUDP:
		conn, err := o.ListenPacket(ctx, destination)
		if err != nil {
			return nil, err
		}
		return bufio.NewBindPacketConn(conn, destination), nil
	default:
		return nil, E.New("unsupported network: ", network)
	}
}

func (o *Outbound) ListenPacket(ctx context.Context, destination M.Socksaddr) (net.PacketConn, error) {
	ctx, metadata := adapter.ExtendContext(ctx)
	metadata.Outbound = o.Tag()
	metadata.Destination = destination
	o.logger.InfoContext(ctx, "outbound packet connection to ", destination)
	return o.client.ListenPacket(ctx, destination)
}

// https://github.com/juicity/juicity/blob/412dbe43e091788c5464eb2d6e9c169bdf39f19c/cmd/client/run.go#L86-L96
func tryDecodeBase64(raw string) ([]byte, error) {
	var errors []error
	decoders := []*base64.Encoding{base64.URLEncoding, base64.StdEncoding}
	for _, decoder := range decoders {
		decoded, err := decoder.DecodeString(raw)
		if err == nil {
			return decoded, nil
		}
		errors = append(errors, err)
	}
	decoded, err := hex.DecodeString(raw)
	if err == nil {
		return decoded, nil
	}
	errors = append(errors, err)
	return nil, E.Cause(E.Errors(errors...), "try decoding base64")
}
