package libcore

import (
	"crypto/tls"
	"net"
	"os"
	"testing"
	"time"

	aTLS "github.com/sagernet/sing-box/common/tls"
	"github.com/sagernet/sing/common"
	N "github.com/sagernet/sing/common/network"
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

	privateKey, publicKey := common.Must2(aTLS.GenerateKeyPair(time.Now, husi, time.Now().Add(5*time.Minute)))
	common.Must(os.WriteFile(customCaFile, publicKey, os.ModePerm))
	defer os.Remove(customCaFile)

	done := make(chan struct{})
	go func(listener net.Listener, done chan struct{}) {
		cert := common.Must1(tls.X509KeyPair(publicKey, privateKey))
		config := &tls.Config{
			Certificates: []tls.Certificate{cert},
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
			go func(config *tls.Config, conn net.Conn) {
				defer conn.Close()
				tlsConn := tls.Server(conn, config)
				err := tlsConn.Handshake()
				if err != nil {
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

	testConnect := func(serverName, address string, wantErr bool, testName string) {
		config := &tls.Config{
			ServerName: serverName,
		}
		conn, err := tls.Dial(N.NetworkTCP, address, config)
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

	// normal
	testConnect(chinaRailway, trustAsiaAddress, false, "normal 12306")
	testConnect(husi, listen, true, "normal local")

	// Load local cert and Mozilla CA
	UpdateRootCACerts(true)
	testConnect(chinaRailway, trustAsiaAddress, true, "mozilla 12306")
	testConnect(husi, listen, false, "loaded custom")

	// Set back but load local
	UpdateRootCACerts(false)
	testConnect(chinaRailway, trustAsiaAddress, false, "normal 12306 2")
	testConnect(husi, listen, false, "loaded custom 2")
}
