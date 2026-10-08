# Windows release signing

`husi-signing-cert.pem` is the certificate the Windows releases are signed with. It is published
here so anyone can check that a downloaded build really came from this project.

It is a **self-signed** certificate, not one issued by a commercial CA. Windows will still warn
about an unknown publisher, and that is expected — SmartScreen was never the point.

## Fingerprints

```
SHA-256  E0:10:8A:03:F1:81:C1:D7:E1:61:C6:E8:B9:94:BC:46:B3:EC:BA:44:54:D8:70:BD:A9:A7:A7:7E:B7:5F:05:E2
SHA-1    B4:1F:B3:8C:6B:08:4A:52:88:07:21:B9:CF:79:B6:94:E0:2F:17:CC
Subject  CN=husi
Valid    2026-08-14 .. 2036-08-11
```

Windows shows the SHA-1 value: it is the "Thumbprint" in the file properties Digital Signatures
tab, and what PowerShell reports.

## Checking a download

PowerShell:

```powershell
(Get-AuthenticodeSignature .\husi-core.exe).SignerCertificate.Thumbprint
```

Linux or macOS, with `osslsigncode`:

```
osslsigncode verify -CAfile release/windows/husi-signing-cert.pem husi-core.exe
```

Every signed payload in a release — the launcher, `husi-core.exe`, the `libcronet.dll` beside it and
the installer — carries this same certificate. `libcronet.dll` is the prebuilt Cronet that SagerNet's cronet-go
publishes unsigned; the signature only says this project shipped it.

## What this does and does not prove

A matching fingerprint means the file was signed with the private key behind this certificate. A
mismatch means it was not, and the build should not be trusted.

Nothing in husi checks this certificate at runtime. Comparing against the fingerprints above is
what ties a build to this project, and that comparison is yours to make.

## Signing a build

See the Windows signing entry in [AGENTS.md](../../AGENTS.md). The private key lives outside this
repository; `release/windows/codesign.sh` reads it from `WINDOWS_SIGNING_P12`.
