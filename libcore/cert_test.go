package libcore

import (
	"bytes"
	"crypto/tls"
	"crypto/x509"
	"encoding/pem"
	"net"
	"os"
	"path/filepath"
	"testing"
	"time"

	aTLS "github.com/sagernet/sing-box/common/tls"
	C "github.com/sagernet/sing-box/constant"
	N "github.com/sagernet/sing/common/network"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestRootCABundleAppendBuildsPoolAndPEM(t *testing.T) {
	_, certificatePEM, err := aTLS.GenerateCertificate(nil, nil, time.Now, "example.com", time.Now().Add(5*time.Minute))
	require.NoError(t, err)

	roots := newRootCABundle()
	require.NoError(t, roots.Append(certificatePEM))

	block, remaining := pem.Decode(roots.pem.Bytes())
	require.NotNil(t, block)
	assert.Empty(t, remaining)
	certificate, err := x509.ParseCertificate(block.Bytes)
	require.NoError(t, err)
	require.Len(t, roots.pool.Subjects(), 1)
	assert.True(t, bytes.Equal(certificate.RawSubject, roots.pool.Subjects()[0]))
}

func TestSetupRootCA(t *testing.T) {
	previousExternalAssetsPath := externalAssetsPath
	externalAssetsPath = t.TempDir()
	t.Cleanup(func() {
		externalAssetsPath = previousExternalAssetsPath
	})

	const (
		chinaRailway     = "www.12306.cn" // Use CA from China
		trustAsiaAddress = chinaRailway + ":443"

		husi = "husi.fr"
	)
	listener, err := net.Listen(N.NetworkTCP, "127.0.0.1:0")
	require.NoError(t, err)
	defer listener.Close()
	listen := listener.Addr().String()

	privateKey, publicKey, err := aTLS.GenerateCertificate(nil, nil, time.Now, husi, time.Now().Add(5*time.Minute))
	require.NoError(t, err)
	require.NoError(t, os.WriteFile(customCaFile, publicKey, os.ModePerm))
	defer os.Remove(customCaFile)
	cert, err := tls.X509KeyPair(publicKey, privateKey)
	require.NoError(t, err)

	done := make(chan struct{})
	go func(listener net.Listener, done chan struct{}) {
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
		}
		if wantErr {
			assert.Error(t, err, testName)
		} else {
			assert.NoError(t, err, testName)
		}
	}

	// normal
	testConnect(chinaRailway, trustAsiaAddress, false, "normal 12306")
	testConnect(husi, listen, true, "normal local")

	// Load local cert and Mozilla CA
	SetupRootCA(CertMozilla)
	testConnect(chinaRailway, trustAsiaAddress, true, "mozilla 12306")
	testConnect(husi, listen, !C.IsAndroid, "loaded custom")

	// Set back but load local
	SetupRootCA(CertSystem)
	testConnect(chinaRailway, trustAsiaAddress, false, "normal 12306 2")
	testConnect(husi, listen, !C.IsAndroid, "loaded custom 2")
}

func TestSetupRootCAWritesPluginRootCACerts(t *testing.T) {
	previousExternalAssetsPath := externalAssetsPath
	externalAssetsPath = t.TempDir()
	t.Cleanup(func() {
		externalAssetsPath = previousExternalAssetsPath
	})

	tests := []struct {
		name       string
		certOption int32
	}{
		{name: "mozilla", certOption: CertMozilla},
		{name: "chrome", certOption: CertChrome},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			SetupRootCA(test.certOption)
			certificates, err := os.ReadFile(filepath.Join(externalAssetsPath, PluginCaFile))
			require.NoError(t, err)
			assert.Contains(t, string(certificates), "-----BEGIN CERTIFICATE-----")
		})
	}
}
