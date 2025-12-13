module libcore

go 1.24.7

require (
	github.com/dyhkwong/sing-juicity v0.1.0-beta.3
	github.com/gofrs/uuid/v5 v5.4.0
	github.com/klauspost/compress v1.18.0
	github.com/miekg/dns v1.1.68
	github.com/sagernet/cronet-go v0.0.0-20251212022311-629f90088dc7
	github.com/sagernet/cronet-go/lib/android_386 v0.0.0-20251212141048-e53eb0672e59
	github.com/sagernet/cronet-go/lib/android_amd64 v0.0.0-20251212141048-e53eb0672e59
	github.com/sagernet/cronet-go/lib/android_arm v0.0.0-20251212141048-e53eb0672e59
	github.com/sagernet/cronet-go/lib/android_arm64 v0.0.0-20251212141048-e53eb0672e59
	github.com/sagernet/gomobile v0.1.9
	github.com/sagernet/sing v0.8.0-beta.6.0.20251207063731-56fd482ce1c6
	github.com/sagernet/sing-box v1.12.13-0.20251212132234-51e067e320f6
	github.com/sagernet/sing-tun v0.8.0-beta.11
	github.com/sagernet/sing-vmess v0.2.8-0.20250909125414-3aed155119a1
	github.com/xchacha20-poly1305/TLS-scribe v0.12.1
	github.com/xchacha20-poly1305/anchor v0.7.1
	github.com/xchacha20-poly1305/cazilla v1.1.0
	github.com/xchacha20-poly1305/libping v0.10.1
	golang.org/x/sync v0.18.0
	golang.org/x/sys v0.38.0
)

// replace github.com/sagernet/sing-box => ../../sing-box

replace github.com/sagernet/sing-vmess => github.com/xchacha20-poly1305/sing-vmess v0.2.7-0.20251206154549-ffe038342a81

// cmd
require (
	github.com/oschwald/geoip2-golang v1.11.0
	github.com/oschwald/maxminddb-golang v1.13.1
	github.com/v2fly/v2ray-core/v5 v5.42.0
	google.golang.org/protobuf v1.36.10
)

require (
	github.com/adrg/xdg v0.5.3 // indirect
	github.com/andybalholm/brotli v1.1.0 // indirect
	github.com/anytls/sing-anytls v0.0.11 // indirect
	github.com/caddyserver/certmagic v0.23.0 // indirect
	github.com/caddyserver/zerossl v0.1.3 // indirect
	github.com/database64128/netx-go v0.1.1 // indirect
	github.com/database64128/tfo-go/v2 v2.3.1 // indirect
	github.com/ebitengine/purego v0.9.1 // indirect
	github.com/fsnotify/fsnotify v1.7.0 // indirect
	github.com/go-chi/chi/v5 v5.2.3 // indirect
	github.com/go-ole/go-ole v1.3.0 // indirect
	github.com/gobwas/httphead v0.1.0 // indirect
	github.com/gobwas/pool v0.2.1 // indirect
	github.com/godbus/dbus/v5 v5.1.1-0.20230522191255-76236955d466 // indirect
	github.com/golang/protobuf v1.5.4 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/google/go-cmp v0.7.0 // indirect
	github.com/hashicorp/yamux v0.1.2 // indirect
	github.com/insomniacslk/dhcp v0.0.0-20250417080101-5f8cf70e8c5f // indirect
	github.com/klauspost/cpuid/v2 v2.2.10 // indirect
	github.com/libdns/alidns v1.0.5-libdns.v1.beta1 // indirect
	github.com/libdns/cloudflare v0.2.2-0.20250708034226-c574dccb31a6 // indirect
	github.com/libdns/libdns v1.1.0 // indirect
	github.com/logrusorgru/aurora v2.0.3+incompatible // indirect
	github.com/mdlayher/netlink v1.7.3-0.20250113171957-fbb4dce95f42 // indirect
	github.com/mdlayher/socket v0.5.1 // indirect
	github.com/metacubex/utls v1.8.3 // indirect
	github.com/mholt/acmez/v3 v3.1.2 // indirect
	github.com/pierrec/lz4/v4 v4.1.21 // indirect
	github.com/quic-go/qpack v0.6.0 // indirect
	github.com/sagernet/bbolt v0.0.0-20231014093535-ea5cb2fe9f0a // indirect
	github.com/sagernet/fswatch v0.1.1 // indirect
	github.com/sagernet/gvisor v0.0.0-20250909151924-850a370d8506 // indirect
	github.com/sagernet/netlink v0.0.0-20240612041022-b9a21c07ac6a // indirect
	github.com/sagernet/nftables v0.3.0-beta.4 // indirect
	github.com/sagernet/quic-go v0.57.1-sing-box-mod.1 // indirect
	github.com/sagernet/sing-mux v0.3.3 // indirect
	github.com/sagernet/sing-quic v0.6.0-beta.5 // indirect
	github.com/sagernet/sing-shadowsocks v0.2.8 // indirect
	github.com/sagernet/sing-shadowsocks2 v0.2.1 // indirect
	github.com/sagernet/sing-shadowtls v0.2.1-0.20250503051639-fcd445d33c11 // indirect
	github.com/sagernet/smux v1.5.34-mod.2 // indirect
	github.com/sagernet/wireguard-go v0.0.2-beta.1.0.20250917110311-16510ac47288 // indirect
	github.com/sagernet/ws v0.0.0-20231204124109-acfe8907c854 // indirect
	github.com/u-root/uio v0.0.0-20240224005618-d2acac8f3701 // indirect
	github.com/vishvananda/netns v0.0.5 // indirect
	github.com/zeebo/blake3 v0.2.4 // indirect
	go.uber.org/multierr v1.11.0 // indirect
	go.uber.org/zap v1.27.0 // indirect
	go.uber.org/zap/exp v0.3.0 // indirect
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba // indirect
	golang.org/x/crypto v0.45.0 // indirect
	golang.org/x/exp v0.0.0-20250911091902-df9299821621 // indirect
	golang.org/x/mod v0.29.0 // indirect
	golang.org/x/net v0.47.0 // indirect
	golang.org/x/text v0.31.0 // indirect
	golang.org/x/time v0.11.0 // indirect
	golang.org/x/tools v0.38.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20251022142026-3a174f9686a8 // indirect
	google.golang.org/grpc v1.77.0 // indirect
	lukechampine.com/blake3 v1.4.1 // indirect
)
