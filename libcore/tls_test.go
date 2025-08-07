package libcore

import (
	"context"
	"net"
	"os"
	"testing"
	"time"

	aTLS "github.com/sagernet/sing-box/common/tls"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	N "github.com/sagernet/sing/common/network"

	utls "github.com/metacubex/utls"
)

func Test_UpdateRootCACerts(t *testing.T) {
	const (
		chinaRailway     = "www.12306.cn" // Use CA from China
		trustAsiaAddress = chinaRailway + ":443"

		husi = "husi.fr"
	)
	listener := common.Must1(net.Listen(N.NetworkTCP, "127.0.0.1:0"))
	defer listener.Close()
	listen := listener.Addr().String()

	privateKey, publicKey := common.Must2(aTLS.GenerateCertificate(nil, nil, time.Now, husi, time.Now().Add(5*time.Minute)))
	common.Must(os.WriteFile(customCaFile, publicKey, os.ModePerm))
	defer os.Remove(customCaFile)

	done := make(chan struct{})
	go func(listener net.Listener, done chan struct{}) {
		cert := common.Must1(utls.X509KeyPair(publicKey, privateKey))
		config := &utls.Config{
			Certificates: []utls.Certificate{cert},
			ServerName:   husi,
		}
		done <- struct{}{}
		go func(listener net.Listener, done chan struct{}) {
			<-done
			_ = listener.Close()
		}(listener, done)
		for {
			select {
			case <-done:
				return
			default:
			}
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			go func(config *utls.Config, conn net.Conn) {
				defer conn.Close()
				tlsConn := utls.Server(conn, config)
				err := tlsConn.HandshakeContext(t.Context())
				if err != nil {
					if !E.IsClosedOrCanceled(err) {
						t.Logf("TLS conn close because: %v", err)
					}
					return
				}
				defer tlsConn.Close()
				// Write something to prevent client EOF
				_, _ = tlsConn.Write([]byte("hello"))
			}(config, conn)
		}
	}(listener, done)
	<-done
	defer close(done)

	refreshRandomFingerprint()

	testConnect := func(ctx context.Context, serverName, address string, wantErr bool, testName string) {
		config := &utls.Config{
			ServerName: serverName,
		}
		dialer := &net.Dialer{}
		conn, err := dialer.DialContext(ctx, N.NetworkTCP, address)
		if err != nil {
			t.Errorf("dial tcp conn: %v", err)
			return
		}
		defer conn.Close()
		client := utls.UClient(conn, config, randomFingerprint)
		defer client.Close()
		err = client.HandshakeContext(ctx)
		if err == nil {
			_ = conn.Close()
			if wantErr {
				t.Errorf("[%s] wants error but not", testName)
			}
		} else {
			if !wantErr {
				t.Errorf("[%s] got unexpected error: %v", testName, err)
			}
		}
	}

	newContext := func() (context.Context, context.CancelFunc) {
		const Timeout = 10 * time.Second
		return context.WithTimeout(t.Context(), Timeout)
	}
	// normal
	ctx, cancel := newContext()
	testConnect(ctx, chinaRailway, trustAsiaAddress, false, "normal 12306")
	cancel()
	ctx, cancel = newContext()
	testConnect(ctx, husi, listen, true, "normal local")
	cancel()

	// Load local cert and Mozilla CA
	UpdateRootCACerts(true, nil)
	ctx, cancel = newContext()
	testConnect(ctx, chinaRailway, trustAsiaAddress, true, "mozilla 12306")
	cancel()
	ctx, cancel = newContext()
	testConnect(ctx, husi, listen, false, "loaded custom")
	cancel()

	// Set back but load local
	UpdateRootCACerts(false, nil)
	ctx, cancel = newContext()
	testConnect(ctx, chinaRailway, trustAsiaAddress, false, "normal 12306 2")
	cancel()
	ctx, cancel = newContext()
	testConnect(ctx, husi, listen, false, "loaded custom 2")
	cancel()
}
