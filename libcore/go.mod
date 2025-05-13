module libcore

go 1.24

require (
	github.com/gofrs/uuid/v5 v5.3.2
	github.com/miekg/dns v1.1.65
	github.com/sagernet/gomobile v0.1.6
	github.com/sagernet/sing v0.6.10-0.20250505040842-ba62fee9470f
	github.com/sagernet/sing-box v1.12.0-beta.13
	github.com/sagernet/sing-tun v0.6.6-0.20250428031943-0686f8c4f210
	github.com/xchacha20-poly1305/TLS-scribe v0.11.2
	github.com/xchacha20-poly1305/anchor v0.6.1
	github.com/xchacha20-poly1305/libping v0.9.0
	golang.org/x/sys v0.32.0
)

// replace github.com/sagernet/sing-box => ../../sing-box

// cmd
require (
	github.com/oschwald/geoip2-golang v1.11.0
	github.com/oschwald/maxminddb-golang v1.13.1
	github.com/v2fly/v2ray-core/v5 v5.31.0
	google.golang.org/protobuf v1.36.6
)

require (
	github.com/adrg/xdg v0.5.3 // indirect
	github.com/andybalholm/brotli v1.1.0 // indirect
	github.com/anytls/sing-anytls v0.0.8 // indirect
	github.com/caddyserver/certmagic v0.23.0 // indirect
	github.com/caddyserver/zerossl v0.1.3 // indirect
	github.com/cloudflare/circl v1.6.1 // indirect
	github.com/fsnotify/fsnotify v1.7.0 // indirect
	github.com/go-chi/chi/v5 v5.2.1 // indirect
	github.com/go-ole/go-ole v1.3.0 // indirect
	github.com/gobwas/httphead v0.1.0 // indirect
	github.com/gobwas/pool v0.2.1 // indirect
	github.com/golang/protobuf v1.5.4 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/google/go-cmp v0.7.0 // indirect
	github.com/hashicorp/yamux v0.1.2 // indirect
	github.com/klauspost/compress v1.17.11 // indirect
	github.com/klauspost/cpuid/v2 v2.2.10 // indirect
	github.com/libdns/alidns v1.0.4-libdns.v1.beta1 // indirect
	github.com/libdns/cloudflare v0.2.2-0.20250430151523-b46a2b0885f6 // indirect
	github.com/libdns/libdns v1.0.0-beta.1 // indirect
	github.com/logrusorgru/aurora v2.0.3+incompatible // indirect
	github.com/mdlayher/netlink v1.7.3-0.20250113171957-fbb4dce95f42 // indirect
	github.com/mdlayher/socket v0.5.1 // indirect
	github.com/metacubex/tfo-go v0.0.0-20241231083714-66613d49c422 // indirect
	github.com/metacubex/utls v1.7.0-alpha.2 // indirect
	github.com/mholt/acmez/v3 v3.1.2 // indirect
	github.com/quic-go/qpack v0.5.1 // indirect
	github.com/sagernet/bbolt v0.0.0-20231014093535-ea5cb2fe9f0a // indirect
	github.com/sagernet/fswatch v0.1.1 // indirect
	github.com/sagernet/gvisor v0.0.0-20250325023245-7a9c0f5725fb // indirect
	github.com/sagernet/netlink v0.0.0-20240612041022-b9a21c07ac6a // indirect
	github.com/sagernet/nftables v0.3.0-beta.4 // indirect
	github.com/sagernet/quic-go v0.51.0-beta.5 // indirect
	github.com/sagernet/sing-mux v0.3.2 // indirect
	github.com/sagernet/sing-quic v0.4.1-0.20250511050139-d459f561c9c3 // indirect
	github.com/sagernet/sing-shadowsocks v0.2.7 // indirect
	github.com/sagernet/sing-shadowsocks2 v0.2.0 // indirect
	github.com/sagernet/sing-shadowtls v0.2.1-0.20250503051639-fcd445d33c11 // indirect
	github.com/sagernet/sing-vmess v0.2.2-0.20250503051933-9b4cf17393f8 // indirect
	github.com/sagernet/smux v1.5.34-mod.2 // indirect
	github.com/sagernet/wireguard-go v0.0.1-beta.7 // indirect
	github.com/sagernet/ws v0.0.0-20231204124109-acfe8907c854 // indirect
	github.com/vishvananda/netns v0.0.5 // indirect
	github.com/zeebo/blake3 v0.2.4 // indirect
	go.uber.org/multierr v1.11.0 // indirect
	go.uber.org/zap v1.27.0 // indirect
	go.uber.org/zap/exp v0.3.0 // indirect
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba // indirect
	golang.org/x/crypto v0.37.0 // indirect
	golang.org/x/exp v0.0.0-20250408133849-7e4ce0ab07d0 // indirect
	golang.org/x/mod v0.24.0 // indirect
	golang.org/x/net v0.39.0 // indirect
	golang.org/x/sync v0.13.0 // indirect
	golang.org/x/text v0.24.0 // indirect
	golang.org/x/time v0.9.0 // indirect
	golang.org/x/tools v0.32.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20250218202821-56aae31c358a // indirect
	google.golang.org/grpc v1.72.0 // indirect
	lukechampine.com/blake3 v1.4.0 // indirect
)
