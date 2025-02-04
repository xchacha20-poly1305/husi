module libcore

// 1.23.0: https://go.dev/doc/go1.23#timer-changes
go 1.23.0

require (
	github.com/gofrs/uuid/v5 v5.3.0
	github.com/miekg/dns v1.1.63
	github.com/sagernet/gomobile v0.1.4
	github.com/sagernet/sing v0.6.0-beta.12
	github.com/sagernet/sing-box v1.11.1-0.20250201114933-7f79458b4f3d
	github.com/sagernet/sing-dns v0.4.0-beta.2
	github.com/sagernet/sing-tun v0.6.0-beta.8
	github.com/xchacha20-poly1305/TLS-scribe v0.9.0
	github.com/xchacha20-poly1305/anchor v0.5.1
	github.com/xchacha20-poly1305/cazilla v0.3.4
	github.com/xchacha20-poly1305/libping v0.8.1
	golang.org/x/sys v0.29.0
)

// replace github.com/sagernet/sing-box => ../../sing-box

// cmd
require (
	github.com/oschwald/geoip2-golang v1.11.0
	github.com/oschwald/maxminddb-golang v1.13.1
	github.com/v2fly/v2ray-core/v5 v5.26.0
	google.golang.org/protobuf v1.36.4
)

require (
	github.com/adrg/xdg v0.5.3 // indirect
	github.com/andybalholm/brotli v1.0.6 // indirect
	github.com/caddyserver/certmagic v0.20.0 // indirect
	github.com/cloudflare/circl v1.5.0 // indirect
	github.com/fsnotify/fsnotify v1.7.0 // indirect
	github.com/go-chi/chi/v5 v5.2.0 // indirect
	github.com/go-ole/go-ole v1.3.0 // indirect
	github.com/gobwas/httphead v0.1.0 // indirect
	github.com/gobwas/pool v0.2.1 // indirect
	github.com/golang/protobuf v1.5.4 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/google/go-cmp v0.6.0 // indirect
	github.com/hashicorp/yamux v0.1.2 // indirect
	github.com/josharian/native v1.1.0 // indirect
	github.com/klauspost/compress v1.17.4 // indirect
	github.com/klauspost/cpuid/v2 v2.2.5 // indirect
	github.com/libdns/alidns v1.0.3 // indirect
	github.com/libdns/cloudflare v0.1.1 // indirect
	github.com/libdns/libdns v0.2.2 // indirect
	github.com/logrusorgru/aurora v2.0.3+incompatible // indirect
	github.com/mdlayher/netlink v1.7.2 // indirect
	github.com/mdlayher/socket v0.4.1 // indirect
	github.com/metacubex/tfo-go v0.0.0-20241006021335-daedaf0ca7aa // indirect
	github.com/mholt/acmez v1.2.0 // indirect
	github.com/quic-go/qpack v0.5.1 // indirect
	github.com/quic-go/qtls-go1-20 v0.4.1 // indirect
	github.com/sagernet/bbolt v0.0.0-20231014093535-ea5cb2fe9f0a // indirect
	github.com/sagernet/cloudflare-tls v0.0.0-20231208171750-a4483c1b7cd1 // indirect
	github.com/sagernet/fswatch v0.1.1 // indirect
	github.com/sagernet/gvisor v0.0.0-20241123041152-536d05261cff // indirect
	github.com/sagernet/netlink v0.0.0-20240612041022-b9a21c07ac6a // indirect
	github.com/sagernet/nftables v0.3.0-beta.4 // indirect
	github.com/sagernet/quic-go v0.48.2-beta.1 // indirect
	github.com/sagernet/reality v0.0.0-20230406110435-ee17307e7691 // indirect
	github.com/sagernet/sing-mux v0.3.0-alpha.1 // indirect
	github.com/sagernet/sing-quic v0.4.0-beta.4 // indirect
	github.com/sagernet/sing-shadowsocks v0.2.7 // indirect
	github.com/sagernet/sing-shadowsocks2 v0.2.0 // indirect
	github.com/sagernet/sing-shadowtls v0.2.0-alpha.2 // indirect
	github.com/sagernet/sing-vmess v0.2.0-beta.2 // indirect
	github.com/sagernet/smux v0.0.0-20231208180855-7041f6ea79e7 // indirect
	github.com/sagernet/utls v1.6.7 // indirect
	github.com/sagernet/wireguard-go v0.0.1-beta.5 // indirect
	github.com/sagernet/ws v0.0.0-20231204124109-acfe8907c854 // indirect
	github.com/vishvananda/netns v0.0.4 // indirect
	github.com/zeebo/blake3 v0.2.3 // indirect
	go.uber.org/multierr v1.11.0 // indirect
	go.uber.org/zap v1.27.0 // indirect
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba // indirect
	golang.org/x/crypto v0.32.0 // indirect
	golang.org/x/exp v0.0.0-20250106191152-7588d65b2ba8 // indirect
	golang.org/x/mod v0.22.0 // indirect
	golang.org/x/net v0.34.0 // indirect
	golang.org/x/sync v0.10.0 // indirect
	golang.org/x/text v0.21.0 // indirect
	golang.org/x/time v0.8.0 // indirect
	golang.org/x/tools v0.29.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20241202173237-19429a94021a // indirect
	google.golang.org/grpc v1.70.0 // indirect
	lukechampine.com/blake3 v1.3.0 // indirect
)
