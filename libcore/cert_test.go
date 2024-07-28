package libcore

import (
	"crypto/tls"
	"net"
	"testing"

	"github.com/sagernet/sing/common"
	N "github.com/sagernet/sing/common/network"
)

func TestUpdateRootCACerts(t *testing.T) {
	const (
		chinaRailway     = "www.12306.cn" // Use CA from China
		trustAsiaAddress = chinaRailway + ":443"

		husi        = "husi.fr"
		localListen = "127.0.0.1:50625"
	)

	done := make(chan struct{})
	go func(done chan struct{}) {
		cert := common.Must1(tls.LoadX509KeyPair("ca.pem", "ca.key"))
		config := &tls.Config{
			Certificates: []tls.Certificate{cert},
			ServerName:   husi,
		}
		listener := common.Must1(tls.Listen(N.NetworkTCP, localListen, config))
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
			go func(conn net.Conn) {
				defer conn.Close()
				_, _ = conn.Write([]byte("hello"))
			}(conn)
		}
	}(done)
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
	testConnect(husi, localListen, true, "normal local")

	// Load local cert and Mozilla CA
	UpdateRootCACerts(true)
	testConnect(chinaRailway, trustAsiaAddress, true, "mozilla 12306")
	testConnect(husi, localListen, false, "loaded custom")

	// Set back but load local
	UpdateRootCACerts(false)
	testConnect(chinaRailway, trustAsiaAddress, false, "normal 12306 2")
	testConnect(husi, localListen, false, "loaded custom 2")
}
