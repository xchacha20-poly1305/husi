module libcore

go 1.26

require (
	filippo.io/age v1.3.1
	github.com/exclavenetwork/sing-juicity v0.2.0
	github.com/gofrs/uuid/v5 v5.5.0
	github.com/klauspost/compress v1.19.1
	github.com/miekg/dns v1.1.72
	github.com/sagernet/sing v0.8.13-0.20260804143108-f22b119cc7a2
	github.com/sagernet/sing-box v1.14.0-beta.7.0.20260805061919-d1e283be4ff8
	github.com/sagernet/sing-tun v0.8.12-0.20260805001200-da24acaf4de3
	github.com/sagernet/sing-vmess v0.2.8-0.20250909125414-3aed155119a1
	github.com/stretchr/testify v1.11.1
	github.com/xchacha20-poly1305/TLS-scribe v0.12.2
	github.com/xchacha20-poly1305/anchor v0.8.0
	github.com/xchacha20-poly1305/anja v0.22.13
	github.com/xchacha20-poly1305/libping v0.10.1
	github.com/xchacha20-poly1305/sing-trusttunnel v0.3.0-beta.3
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba
	golang.org/x/sys v0.47.0
	google.golang.org/protobuf v1.36.11
)

tool (
	github.com/xchacha20-poly1305/anja/cmd/anja
	github.com/xchacha20-poly1305/anja/cmd/anjb
)

// replace github.com/sagernet/sing-box => ../../sing-box

replace github.com/sagernet/sing-vmess => github.com/xchacha20-poly1305/sing-vmess v0.2.9-0.20260730020509-f81302d3921a

// cmd
require (
	github.com/oschwald/geoip2-golang v1.13.0
	github.com/oschwald/maxminddb-golang v1.13.1
	github.com/xchacha20-poly1305/pkgsite-go v0.6.3
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
	github.com/insomniacslk/dhcp v0.0.0-20260220084031-5adc3eb26f91 // indirect
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
	github.com/sagernet/cronet-go v0.0.0-20260731162621-4185d471b2e4 // indirect
	github.com/sagernet/cronet-go/all v0.0.0-20260731162621-4185d471b2e4 // indirect
	github.com/sagernet/cronet-go/lib/android_386 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/android_amd64 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/android_arm v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/android_arm64 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/darwin_amd64 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/darwin_arm64 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/ios_amd64_simulator v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/ios_arm64 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/ios_arm64_simulator v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_386 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_386_musl v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_amd64 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_amd64_musl v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_arm v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_arm64 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_arm64_musl v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_arm_musl v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_loong64 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_loong64_musl v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_mips64le v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_mipsle v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_mipsle_musl v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_riscv64 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/linux_riscv64_musl v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/tvos_amd64_simulator v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/tvos_arm64 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/tvos_arm64_simulator v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/windows_amd64 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/cronet-go/lib/windows_arm64 v0.0.0-20260731161755-38229fb700f6 // indirect
	github.com/sagernet/fswatch v0.1.2 // indirect
	github.com/sagernet/gvisor v0.0.0-20260727.0-sing-box-mod.1 // indirect
	github.com/sagernet/netlink v0.0.0-20240612041022-b9a21c07ac6a // indirect
	github.com/sagernet/nftables v0.3.0-mod.4 // indirect
	github.com/sagernet/quic-go v0.61.0-sing-box-mod.1 // indirect
	github.com/sagernet/sing-mux v0.3.5 // indirect
	github.com/sagernet/sing-openconnect v0.0.0-20260722140139-e0c977659ca2 // indirect
	github.com/sagernet/sing-openvpn v0.0.0-20260729104525-103eb5fe5eb6 // indirect
	github.com/sagernet/sing-quic v0.6.4 // indirect
	github.com/sagernet/sing-shadowsocks v0.2.8 // indirect
	github.com/sagernet/sing-shadowsocks2 v0.2.1 // indirect
	github.com/sagernet/sing-shadowtls v0.2.1 // indirect
	github.com/sagernet/sing-snell v0.0.0-20260727093646-7cb813e07b73 // indirect
	github.com/sagernet/sing-usbip v0.0.0-20260616101517-efb91521eddb // indirect
	github.com/sagernet/smux v1.5.50-sing-box-mod.1 // indirect
	github.com/sagernet/wireguard-go v0.0.5-0.20260805042941-f39689ad3562 // indirect
	github.com/sagernet/ws v0.0.0-20231204124109-acfe8907c854 // indirect
	github.com/smallstep/pkcs7 v0.1.1 // indirect
	github.com/tjfoc/gmsm v1.4.1 // indirect
	github.com/u-root/uio v0.0.0-20240224005618-d2acac8f3701 // indirect
	github.com/vishvananda/netns v0.0.5 // indirect
	github.com/youmark/pkcs8 v0.0.0-20240726163527-a2c0da244d78 // indirect
	github.com/zeebo/blake3 v0.2.4 // indirect
	go.uber.org/multierr v1.11.0 // indirect
	go.uber.org/zap v1.27.1 // indirect
	go.uber.org/zap/exp v0.3.0 // indirect
	golang.org/x/crypto v0.54.0 // indirect
	golang.org/x/exp v0.0.0-20260410095643-746e56fc9e2f // indirect
	golang.org/x/mod v0.37.0 // indirect
	golang.org/x/net v0.57.0 // indirect
	golang.org/x/sync v0.22.0 // indirect
	golang.org/x/text v0.40.0 // indirect
	golang.org/x/time v0.15.0 // indirect
	golang.org/x/tools v0.47.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20260226221140-a57be14db171 // indirect
	google.golang.org/grpc v1.81.1 // indirect
	gopkg.in/yaml.v3 v3.0.1 // indirect
	lukechampine.com/blake3 v1.4.1 // indirect
)
