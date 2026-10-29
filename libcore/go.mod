module github.com/xchacha20-poly1305/husi/libcore/v2

go 1.27

tool (
	github.com/sagernet/gomobile/cmd/gobind
	github.com/sagernet/gomobile/cmd/gomobile
	google.golang.org/grpc/cmd/protoc-gen-go-grpc
	google.golang.org/protobuf/cmd/protoc-gen-go
)

replace (
	github.com/sagernet/sing-vmess => github.com/xchacha20-poly1305/sing-vmess v0.2.9-0.20261001065223-6e0057f95c32

// github.com/sagernet/sing-box => ../../sing-box
)

require (
	filippo.io/age v1.3.2
	github.com/coder/websocket v1.8.15
	github.com/exclavenetwork/sing-juicity v0.3.1
	github.com/godbus/dbus/v5 v5.2.2
	github.com/google/licensecheck v0.3.1
	github.com/miekg/dns v1.1.72
	github.com/oschwald/geoip2-golang v1.13.0
	github.com/oschwald/maxminddb-golang v1.13.1
	github.com/sagernet/cors v1.2.1
	github.com/sagernet/gomobile v0.1.13
	github.com/sagernet/quic-go v0.61.0-sing-box-mod.12
	github.com/sagernet/sing v0.9.7-0.20261023115510-03a6cce64bf7
	github.com/sagernet/sing-anytls v0.0.0-20260928104022-580984e4d8cb
	github.com/sagernet/sing-box v1.15.0-alpha.13
	github.com/sagernet/sing-mux v0.3.10-0.20260929204512-caf09fe32475
	github.com/sagernet/sing-quic v0.7.2-0.20261020082049-7e0a5869a5f4
	github.com/sagernet/sing-snell v0.0.0-20260904135315-bc5a12ac736f
	github.com/sagernet/sing-tun v0.9.7-0.20261023115453-611d83b7a57b
	github.com/sagernet/sing-vmess v0.2.9-0.20261019223527-a0c58542bd9d
	github.com/spf13/cobra v1.10.2
	github.com/stretchr/testify v1.12.1
	github.com/tailscale/go-winio v0.0.0-20231025203758-c4f33415bf55
	github.com/xchacha20-poly1305/TLS-scribe v0.13.1
	github.com/xchacha20-poly1305/anchor v0.9.0
	github.com/xchacha20-poly1305/libping v0.10.5
	github.com/xchacha20-poly1305/protect-go v0.2.0
	github.com/xchacha20-poly1305/sing-trusttunnel v0.3.3-0.20260928134000-daacc8079caf
	github.com/xchacha20-poly1305/stunuri-go v0.2.0
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba
	golang.org/x/mod v0.41.0
	golang.org/x/net v0.59.0
	golang.org/x/sys v0.49.0
	golang.org/x/text v0.42.0
	google.golang.org/grpc v1.83.2
	google.golang.org/protobuf v1.36.12
	howett.net/plist v1.0.1
)

require (
	filippo.io/edwards25519 v1.2.0 // indirect
	filippo.io/hpke v0.4.0 // indirect
	github.com/RyuaNerin/go-krypto v1.3.0 // indirect
	github.com/ajg/form v1.5.1 // indirect
	github.com/akutz/memconn v0.1.0 // indirect
	github.com/alexbrainman/sspi v0.0.0-20231016080023-1a75b4708caa // indirect
	github.com/anchore/go-lzo v0.1.0 // indirect
	github.com/andybalholm/brotli v1.2.4 // indirect
	github.com/anmitsu/go-shlex v0.0.0-20200514113438-38f4b401e2be // indirect
	github.com/caddyserver/certmagic v0.25.3-0.20260421143802-60d9d8b415d6 // indirect
	github.com/caddyserver/zerossl v0.1.5 // indirect
	github.com/coreos/go-iptables v0.7.1-0.20240112124308-65c67c9f46e6 // indirect
	github.com/creack/pty v1.1.24 // indirect
	github.com/database64128/netx-go v0.1.1 // indirect
	github.com/database64128/tfo-go/v2 v2.3.2 // indirect
	github.com/dblohm7/wingoes v0.0.0-20240119213807-a09d6be7affa // indirect
	github.com/dgryski/go-camellia v0.0.0-20191119043421-69a8a13fb23d // indirect
	github.com/ebitengine/purego v0.10.0 // indirect
	github.com/florianl/go-nfqueue/v2 v2.1.0 // indirect
	github.com/fsnotify/fsnotify v1.9.0 // indirect
	github.com/fxamacker/cbor/v2 v2.9.0 // indirect
	github.com/gaissmai/bart v0.26.1 // indirect
	github.com/go-chi/chi/v5 v5.2.5 // indirect
	github.com/go-chi/render v1.0.3 // indirect
	github.com/go-ole/go-ole v1.3.0 // indirect
	github.com/gobwas/httphead v0.1.0 // indirect
	github.com/gobwas/pool v0.2.1 // indirect
	github.com/gofrs/uuid/v5 v5.5.1 // indirect
	github.com/golang/groupcache v0.0.0-20241129210726-2c02b8208cf8 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/google/certificate-transparency-go v1.3.2 // indirect
	github.com/google/go-cmp v0.7.0 // indirect
	github.com/google/gopacket v1.1.19 // indirect
	github.com/google/nftables v0.2.1-0.20240414091927-5e242ec57806 // indirect
	github.com/hashicorp/yamux v0.1.2 // indirect
	github.com/hdevalence/ed25519consensus v0.2.0 // indirect
	github.com/huin/goupnp v1.3.0 // indirect
	github.com/inconshreveable/mousetrap v1.1.0 // indirect
	github.com/jackpal/go-nat-pmp v1.0.2 // indirect
	github.com/jsimonetti/rtnetlink v1.4.1 // indirect
	github.com/klauspost/compress v1.20.0 // indirect
	github.com/klauspost/cpuid/v2 v2.3.0 // indirect
	github.com/koron/go-ssdp v0.0.4 // indirect
	github.com/kr/fs v0.1.0 // indirect
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
	github.com/mitchellh/go-ps v1.0.0 // indirect
	github.com/pierrec/lz4/v4 v4.1.26 // indirect
	github.com/pion/dtls/v3 v3.1.5 // indirect
	github.com/pion/logging v0.2.4 // indirect
	github.com/pion/transport/v4 v4.0.2 // indirect
	github.com/pires/go-proxyproto v0.8.1 // indirect
	github.com/pkg/sftp v1.13.10 // indirect
	github.com/quic-go/qpack v0.6.0 // indirect
	github.com/safchain/ethtool v0.3.0 // indirect
	github.com/sagernet/bbolt v0.0.0-20260915102804-500ee1e84832 // indirect
	github.com/sagernet/cronet-go v0.0.0-20261023060337-b3c2ed82ac97 // indirect
	github.com/sagernet/cronet-go/all v0.0.0-20261023060337-b3c2ed82ac97 // indirect
	github.com/sagernet/cronet-go/lib/android_386 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/android_amd64 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/android_arm v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/android_arm64 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/darwin_amd64 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/darwin_arm64 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/ios_amd64_simulator v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/ios_arm64 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/ios_arm64_simulator v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_386 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_386_musl v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_amd64 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_amd64_musl v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_arm v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_arm64 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_arm64_musl v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_arm_musl v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_loong64 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_loong64_musl v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_mips64le v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_mipsle v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_mipsle_musl v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_riscv64 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/linux_riscv64_musl v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/tvos_amd64_simulator v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/tvos_arm64 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/tvos_arm64_simulator v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/windows_amd64 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/cronet-go/lib/windows_arm64 v0.0.0-20261023055542-d5437a0df01a // indirect
	github.com/sagernet/fswatch v0.1.2 // indirect
	github.com/sagernet/gliderssh v0.3.4-0.20261023092853-e0cad6d28647 // indirect
	github.com/sagernet/gvisor v0.0.0-20260727.0-sing-box-mod.1 // indirect
	github.com/sagernet/netlink v0.0.0-20260814022025-64455d367bbf // indirect
	github.com/sagernet/nftables v0.3.0-mod.4 // indirect
	github.com/sagernet/sing-openconnect v0.1.1-0.20260929151226-29757281a247 // indirect
	github.com/sagernet/sing-openvpn v0.1.1-0.20261020061844-7d24ecd92837 // indirect
	github.com/sagernet/sing-shadowsocks v0.2.9-0.20260929204512-65740e0f0e3e // indirect
	github.com/sagernet/sing-shadowsocks2 v0.2.2-0.20260929152114-a69d1086332b // indirect
	github.com/sagernet/sing-shadowtls v0.2.2-0.20260928201441-a9c0127d5c99 // indirect
	github.com/sagernet/sing-usbip v0.0.0-20260817040617-28bd42667eca // indirect
	github.com/sagernet/smux v1.5.50-sing-box-mod.1 // indirect
	github.com/sagernet/tailscale v1.102.1-sing-box-1.14-mod.5.0.20261022222424-1688b52f8204 // indirect
	github.com/sagernet/wireguard-go v0.0.8-0.20261022215849-fdf0ad046586 // indirect
	github.com/sagernet/ws v0.0.0-20231204124109-acfe8907c854 // indirect
	github.com/smallstep/pkcs7 v0.1.1 // indirect
	github.com/spf13/pflag v1.0.10 // indirect
	github.com/tailscale/certstore v0.1.1-0.20260409135935-3638fb84b77d // indirect
	github.com/tailscale/hujson v0.0.0-20260302212456-ecc657c15afd // indirect
	github.com/tailscale/netlink v1.1.1-0.20240822203006-4d49adab4de7 // indirect
	github.com/tailscale/peercred v0.0.0-20250107143737-35a0c7bd7edc // indirect
	github.com/tailscale/web-client-prebuilt v0.0.0-20250124233751-d4cd19a26976 // indirect
	github.com/tjfoc/gmsm v1.4.1 // indirect
	github.com/vishvananda/netns v0.0.5 // indirect
	github.com/x448/float16 v0.8.4 // indirect
	github.com/youmark/pkcs8 v0.0.0-20240726163527-a2c0da244d78 // indirect
	github.com/zeebo/blake3 v0.2.4 // indirect
	go.uber.org/multierr v1.11.0 // indirect
	go.uber.org/zap v1.28.0 // indirect
	go.uber.org/zap/exp v0.3.0 // indirect
	go.yaml.in/yaml/v3 v3.0.5 // indirect
	go4.org/mem v0.0.0-20240501181205-ae6ca9944745 // indirect
	golang.org/x/crypto v0.57.0 // indirect
	golang.org/x/exp v0.0.0-20260908205506-85c1c2202aba // indirect
	golang.org/x/oauth2 v0.36.0 // indirect
	golang.org/x/sync v0.23.0 // indirect
	golang.org/x/term v0.46.0 // indirect
	golang.org/x/time v0.15.0 // indirect
	golang.org/x/tools v0.50.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	golang.zx2c4.com/wireguard/windows v0.5.3 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20260526163538-3dc84a4a5aaa // indirect
	google.golang.org/grpc/cmd/protoc-gen-go-grpc v1.6.2 // indirect
	lukechampine.com/blake3 v1.4.1 // indirect
)
