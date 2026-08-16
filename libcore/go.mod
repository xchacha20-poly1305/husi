module libcore

go 1.26

require (
	filippo.io/age v1.3.1
	github.com/exclavenetwork/sing-juicity v0.3.0-beta.1
	github.com/gofrs/uuid/v5 v5.5.1
	github.com/klauspost/compress v1.19.2
	github.com/miekg/dns v1.1.72
	github.com/sagernet/sing v0.9.0-beta.2
	github.com/sagernet/sing-box v1.14.0-beta.15
	github.com/sagernet/sing-tun v0.8.12-0.20260810140529-d67734281390
	github.com/sagernet/sing-vmess v0.2.8-0.20250909125414-3aed155119a1
	github.com/stretchr/testify v1.11.1
	github.com/tailscale/go-winio v0.0.0-20231025203758-c4f33415bf55
	github.com/xchacha20-poly1305/TLS-scribe v0.12.2
	github.com/xchacha20-poly1305/anchor v0.8.0
	github.com/xchacha20-poly1305/anja v0.22.16
	github.com/xchacha20-poly1305/libping v0.10.1
	github.com/xchacha20-poly1305/sing-trusttunnel v0.3.0-beta.5
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba
	golang.org/x/sys v0.47.0
	google.golang.org/grpc v1.83.0
	google.golang.org/protobuf v1.36.12
)

tool (
	github.com/xchacha20-poly1305/anja/cmd/anja
	github.com/xchacha20-poly1305/anja/cmd/anjb
	google.golang.org/grpc/cmd/protoc-gen-go-grpc
	google.golang.org/protobuf/cmd/protoc-gen-go
)

// replace github.com/sagernet/sing-box => ../../sing-box

replace github.com/sagernet/sing-vmess => github.com/xchacha20-poly1305/sing-vmess v0.2.9-0.20260730020509-f81302d3921a

// cmd
require (
	github.com/oschwald/geoip2-golang v1.13.0
	github.com/oschwald/maxminddb-golang v1.13.1
	github.com/xchacha20-poly1305/pkgsite-go v0.6.3
	golang.org/x/mod v0.40.0
)

require (
	filippo.io/hpke v0.4.0 // indirect
	github.com/RyuaNerin/go-krypto v1.3.0 // indirect
	github.com/ajg/form v1.5.1 // indirect
	github.com/anchore/go-lzo v0.1.0 // indirect
	github.com/andybalholm/brotli v1.1.0 // indirect
	github.com/anytls/sing-anytls v0.0.11 // indirect
	github.com/caddyserver/certmagic v0.25.3-0.20260421143802-60d9d8b415d6 // indirect
	github.com/caddyserver/zerossl v0.1.5 // indirect
	github.com/database64128/netx-go v0.1.1 // indirect
	github.com/database64128/tfo-go/v2 v2.3.2 // indirect
	github.com/davecgh/go-spew v1.1.2-0.20180830191138-d8f796af33cc // indirect
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
	github.com/pmezard/go-difflib v1.0.1-0.20181226105442-5d4384ee4fb2 // indirect
	github.com/quic-go/qpack v0.6.0 // indirect
	github.com/sagernet/bbolt v0.0.0-20231014093535-ea5cb2fe9f0a // indirect
	github.com/sagernet/cronet-go v0.0.0-20260807162344-ec9a39c5ba3b // indirect
	github.com/sagernet/cronet-go/all v0.0.0-20260807162344-ec9a39c5ba3b // indirect
	github.com/sagernet/cronet-go/lib/android_386 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/android_amd64 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/android_arm v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/android_arm64 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/darwin_amd64 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/darwin_arm64 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/ios_amd64_simulator v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/ios_arm64 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/ios_arm64_simulator v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_386 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_386_musl v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_amd64 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_amd64_musl v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_arm v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_arm64 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_arm64_musl v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_arm_musl v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_loong64 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_loong64_musl v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_mips64le v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_mipsle v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_mipsle_musl v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_riscv64 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/linux_riscv64_musl v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/tvos_amd64_simulator v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/tvos_arm64 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/tvos_arm64_simulator v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/windows_amd64 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/cronet-go/lib/windows_arm64 v0.0.0-20260807161529-8d42107dcdfc // indirect
	github.com/sagernet/fswatch v0.1.2 // indirect
	github.com/sagernet/gvisor v0.0.0-20260727.0-sing-box-mod.1 // indirect
	github.com/sagernet/netlink v0.0.0-20260814022025-64455d367bbf // indirect
	github.com/sagernet/nftables v0.3.0-mod.4 // indirect
	github.com/sagernet/quic-go v0.61.0-sing-box-mod.4 // indirect
	github.com/sagernet/sing-mux v0.3.5 // indirect
	github.com/sagernet/sing-openconnect v0.0.0-20260810065514-53aa8058f8df // indirect
	github.com/sagernet/sing-openvpn v0.0.0-20260729104525-103eb5fe5eb6 // indirect
	github.com/sagernet/sing-quic v0.7.0-beta.2 // indirect
	github.com/sagernet/sing-shadowsocks v0.2.8 // indirect
	github.com/sagernet/sing-shadowsocks2 v0.2.1 // indirect
	github.com/sagernet/sing-shadowtls v0.2.1 // indirect
	github.com/sagernet/sing-snell v0.0.0-20260727093646-7cb813e07b73 // indirect
	github.com/sagernet/sing-usbip v0.0.0-20260813125128-908a3a2fa917 // indirect
	github.com/sagernet/smux v1.5.50-sing-box-mod.1 // indirect
	github.com/sagernet/wireguard-go v0.0.5-0.20260810121456-c6c8a831ef70 // indirect
	github.com/sagernet/ws v0.0.0-20231204124109-acfe8907c854 // indirect
	github.com/smallstep/pkcs7 v0.1.1 // indirect
	github.com/tjfoc/gmsm v1.4.1 // indirect
	github.com/vishvananda/netns v0.0.5 // indirect
	github.com/youmark/pkcs8 v0.0.0-20240726163527-a2c0da244d78 // indirect
	github.com/zeebo/blake3 v0.2.4 // indirect
	go.uber.org/multierr v1.11.0 // indirect
	go.uber.org/zap v1.27.1 // indirect
	go.uber.org/zap/exp v0.3.0 // indirect
	golang.org/x/crypto v0.55.0 // indirect
	golang.org/x/exp v0.0.0-20260410095643-746e56fc9e2f // indirect
	golang.org/x/net v0.58.0 // indirect
	golang.org/x/sync v0.22.0 // indirect
	golang.org/x/text v0.41.0 // indirect
	golang.org/x/time v0.15.0 // indirect
	golang.org/x/tools v0.49.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20260526163538-3dc84a4a5aaa // indirect
	google.golang.org/grpc/cmd/protoc-gen-go-grpc v1.6.2 // indirect
	gopkg.in/yaml.v3 v3.0.1 // indirect
	lukechampine.com/blake3 v1.4.1 // indirect
)
