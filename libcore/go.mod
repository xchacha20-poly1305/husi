module github.com/xchacha20-poly1305/husi/libcore/v2

go 1.26

require (
	filippo.io/age v1.3.2
	github.com/coder/websocket v1.8.15
	github.com/exclavenetwork/sing-juicity v0.3.1-0.20260904055012-342aef37343b
	github.com/gofrs/uuid/v5 v5.5.1
	github.com/klauspost/compress v1.19.2
	github.com/miekg/dns v1.1.72
	github.com/sagernet/cors v1.2.1
	github.com/sagernet/sing v0.9.1-0.20260902140658-8bb71f553f8d
	github.com/sagernet/sing-box v1.15.0-alpha.1
	github.com/sagernet/sing-tun v0.9.1-0.20260902150540-98e457e39c90
	github.com/sagernet/sing-vmess v0.2.8
	github.com/stretchr/testify v1.12.1
	github.com/tailscale/go-winio v0.0.0-20231025203758-c4f33415bf55
	github.com/xchacha20-poly1305/TLS-scribe v0.12.2
	github.com/xchacha20-poly1305/anchor v0.8.0
	github.com/xchacha20-poly1305/anja v0.22.16
	github.com/xchacha20-poly1305/libping v0.10.5
	github.com/xchacha20-poly1305/sing-trusttunnel v0.3.3-0.20260904152801-535ad468f115
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba
	golang.org/x/net v0.58.0
	golang.org/x/sys v0.47.0
	google.golang.org/grpc v1.83.2
	google.golang.org/protobuf v1.36.12
)

tool (
	github.com/xchacha20-poly1305/anja/cmd/anja
	github.com/xchacha20-poly1305/anja/cmd/anjb
	google.golang.org/grpc/cmd/protoc-gen-go-grpc
	google.golang.org/protobuf/cmd/protoc-gen-go
)

replace (
	github.com/sagernet/sing-vmess => github.com/xchacha20-poly1305/sing-vmess v0.2.9-0.20260730020509-f81302d3921a

// github.com/sagernet/sing-box => ../../sing-box
)

// cmd
require (
	github.com/google/licensecheck v0.3.1
	github.com/oschwald/geoip2-golang v1.13.0
	github.com/oschwald/maxminddb-golang v1.13.1
	golang.org/x/mod v0.40.0
)

require (
	filippo.io/hpke v0.4.0 // indirect
	github.com/RyuaNerin/go-krypto v1.3.0 // indirect
	github.com/ajg/form v1.5.1 // indirect
	github.com/anchore/go-lzo v0.1.0 // indirect
	github.com/andybalholm/brotli v1.1.0 // indirect
	github.com/caddyserver/certmagic v0.25.3-0.20260421143802-60d9d8b415d6 // indirect
	github.com/caddyserver/zerossl v0.1.5 // indirect
	github.com/database64128/netx-go v0.1.1 // indirect
	github.com/database64128/tfo-go/v2 v2.3.2 // indirect
	github.com/dgryski/go-camellia v0.0.0-20191119043421-69a8a13fb23d // indirect
	github.com/ebitengine/purego v0.10.0 // indirect
	github.com/florianl/go-nfqueue/v2 v2.1.0 // indirect
	github.com/fsnotify/fsnotify v1.9.0 // indirect
	github.com/go-chi/chi/v5 v5.2.5 // indirect
	github.com/go-chi/render v1.0.3 // indirect
	github.com/go-ole/go-ole v1.3.0 // indirect
	github.com/gobwas/httphead v0.1.0 // indirect
	github.com/gobwas/pool v0.2.1 // indirect
	github.com/godbus/dbus/v5 v5.2.2 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/google/certificate-transparency-go v1.3.2 // indirect
	github.com/google/go-cmp v0.7.0 // indirect
	github.com/google/gopacket v1.1.19 // indirect
	github.com/hashicorp/yamux v0.1.2 // indirect
	github.com/huin/goupnp v1.3.0 // indirect
	github.com/jackpal/go-nat-pmp v1.0.2 // indirect
	github.com/jsimonetti/rtnetlink v1.4.1 // indirect
	github.com/klauspost/cpuid/v2 v2.3.0 // indirect
	github.com/koron/go-ssdp v0.0.4 // indirect
	github.com/libdns/acmedns v0.5.0 // indirect
	github.com/libdns/alidns v1.0.6 // indirect
	github.com/libdns/cloudflare v0.2.2 // indirect
	github.com/libdns/libdns v1.1.1 // indirect
	github.com/libp2p/go-nat v1.0.1-0.20250821073202-01afc089f138 // indirect
	github.com/libp2p/go-netroute v0.2.1 // indirect
	github.com/logrusorgru/aurora v2.0.3+incompatible // indirect
	github.com/mdlayher/netlink v1.11.2 // indirect
	github.com/mdlayher/socket v0.6.0 // indirect
	github.com/metacubex/utls v1.8.7 // indirect
	github.com/mholt/acmez/v3 v3.1.6 // indirect
	github.com/pierrec/lz4/v4 v4.1.26 // indirect
	github.com/pion/dtls/v3 v3.1.5 // indirect
	github.com/pion/logging v0.2.4 // indirect
	github.com/pion/transport/v4 v4.0.2 // indirect
	github.com/quic-go/qpack v0.6.0 // indirect
	github.com/sagernet/bbolt v0.0.0-20260823094646-e24805439c9c // indirect
	github.com/sagernet/cronet-go v0.0.0-20260831031307-45832ab07484 // indirect
	github.com/sagernet/cronet-go/all v0.0.0-20260831031307-45832ab07484 // indirect
	github.com/sagernet/cronet-go/lib/android_386 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/android_amd64 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/android_arm v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/android_arm64 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/darwin_amd64 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/darwin_arm64 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/ios_amd64_simulator v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/ios_arm64 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/ios_arm64_simulator v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_386 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_386_musl v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_amd64 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_amd64_musl v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_arm v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_arm64 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_arm64_musl v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_arm_musl v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_loong64 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_loong64_musl v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_mips64le v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_mipsle v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_mipsle_musl v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_riscv64 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/linux_riscv64_musl v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/tvos_amd64_simulator v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/tvos_arm64 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/tvos_arm64_simulator v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/windows_amd64 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/cronet-go/lib/windows_arm64 v0.0.0-20260831030607-f80ef37265e5 // indirect
	github.com/sagernet/fswatch v0.1.2 // indirect
	github.com/sagernet/gvisor v0.0.0-20260727.0-sing-box-mod.1 // indirect
	github.com/sagernet/netlink v0.0.0-20260814022025-64455d367bbf // indirect
	github.com/sagernet/nftables v0.3.0-mod.4 // indirect
	github.com/sagernet/quic-go v0.61.0-sing-box-mod.7 // indirect
	github.com/sagernet/sing-anytls v0.0.0-20260904043735-a2775a3fbcad // indirect
	github.com/sagernet/sing-mux v0.3.6-0.20260904043737-7e4c81a2011c // indirect
	github.com/sagernet/sing-openconnect v0.0.0-20260903200519-8b89c968949d // indirect
	github.com/sagernet/sing-openvpn v0.0.0-20260903200517-e060dda5b1f1 // indirect
	github.com/sagernet/sing-quic v0.7.1-0.20260904043739-344d4a543cbf // indirect
	github.com/sagernet/sing-shadowsocks v0.2.8 // indirect
	github.com/sagernet/sing-shadowsocks2 v0.2.1 // indirect
	github.com/sagernet/sing-shadowtls v0.2.1 // indirect
	github.com/sagernet/sing-snell v0.0.0-20260904043742-138588f1fa53 // indirect
	github.com/sagernet/sing-usbip v0.0.0-20260817040617-28bd42667eca // indirect
	github.com/sagernet/smux v1.5.50-sing-box-mod.1 // indirect
	github.com/sagernet/wireguard-go v0.0.5 // indirect
	github.com/sagernet/ws v0.0.0-20231204124109-acfe8907c854 // indirect
	github.com/smallstep/pkcs7 v0.1.1 // indirect
	github.com/tjfoc/gmsm v1.4.1 // indirect
	github.com/vishvananda/netns v0.0.5 // indirect
	github.com/youmark/pkcs8 v0.0.0-20240726163527-a2c0da244d78 // indirect
	github.com/zeebo/blake3 v0.2.4 // indirect
	go.uber.org/multierr v1.11.0 // indirect
	go.uber.org/zap v1.27.1 // indirect
	go.uber.org/zap/exp v0.3.0 // indirect
	go.yaml.in/yaml/v3 v3.0.5 // indirect
	golang.org/x/crypto v0.55.0 // indirect
	golang.org/x/exp v0.0.0-20260410095643-746e56fc9e2f // indirect
	golang.org/x/sync v0.22.0 // indirect
	golang.org/x/text v0.41.0 // indirect
	golang.org/x/time v0.15.0 // indirect
	golang.org/x/tools v0.49.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20260526163538-3dc84a4a5aaa // indirect
	google.golang.org/grpc/cmd/protoc-gen-go-grpc v1.6.2 // indirect
	lukechampine.com/blake3 v1.4.1 // indirect
)
