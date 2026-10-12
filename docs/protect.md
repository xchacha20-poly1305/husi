# Socket Protect Path Protocol

## Abstract

A tunnel that captures a host's traffic also captures sockets of other
local processes. A process that must reach a host outside that tunnel has to
hand each such socket to the program that owns the tunnel, and that program
exempts the socket before any packet is sent. This document specifies that
handoff. It is a one-octet exchange on a Unix-domain stream socket. The socket
to exempt travels as a single `SCM_RIGHTS` file descriptor.

## Status of This Memo

This document is a project-level specification of a local inter-process
protocol. It is not an Internet Standards Track specification.

## 1. Introduction

Packets of a socket that is not exempted enter the tunnel and loop back to
the program that owns it. The process that created the socket (the client)
therefore passes the descriptor to the program that owns the tunnel (the
server). The server exempts the descriptor and replies. The client uses the
socket after a successful reply.

The client cannot exempt the socket itself. On Android only the app that owns
the VPN may call `VpnService.protect`. On Linux, setting `SO_MARK` requires
`CAP_NET_ADMIN`, which the kernel checks against the process that calls
`setsockopt`, and only the server knows which interface and routing mark keep
packets out of its tunnel.

The listening path is agreed out of band. This document does not define how
that path is configured. Section 7 describes how current servers exempt a
descriptor, but that operation is not part of the exchange.

## 2. Background

The exchange was introduced by shadowsocks-android [SS-ANDROID] and
shadowsocks-libev [SS-LIBEV] so that SIP003 plugins could keep their sockets
out of the Android VPN. Matsuri added a client to its Hysteria 1 fork
[MATSURI-HYSTERIA] so that the plugin could dial its server directly instead
of going through the tunnel. Husi uses the same exchange on Linux and macOS,
so plugins bypass a TUN interface on those systems as well.

## 3. Requirements Language

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
"SHOULD", "SHOULD NOT", "RECOMMENDED", "NOT RECOMMENDED", "MAY", and
"OPTIONAL" in this document are to be interpreted as described in BCP 14
[RFC2119] [RFC8174] when, and only when, they appear in all capitals, as
shown here.

## 4. Terminology

- Client: the process that created the socket to be exempted.
- Server: the process that owns the tunnel and accepts protect exchanges.
- Exemption: the platform-specific operation that keeps later packets of a
  socket out of the tunnel.

## 5. Transport

The server MUST listen on a Unix-domain socket of type `SOCK_STREAM`, bound
to a filesystem pathname. This document does not define the use of
abstract-namespace addresses.

A relative path is resolved by each process against its own working
directory. A relative path names one socket only when both processes resolve
it to the same pathname.

What a server does when the pathname already exists, including when another
server still listens on it, is not defined by this document.

The server SHOULD create the listening socket with mode `0777`, so a client
running as another user can connect. The server MAY use a stricter mode.

The server MAY inspect the credentials of the connecting process, for example
with `SO_PEERCRED` or `LOCAL_PEERCRED`, and MAY reject a process it does not
expect. A server that does not inspect the peer conforms to this document.

The protocol requires a system that can pass descriptors with `SCM_RIGHTS`.
Systems without it, such as Windows, whose `AF_UNIX` sockets carry no
descriptors, are out of scope. A separate draft, `protect_windows.md`,
specifies a Windows variant.

## 6. Exchange

One connection carries one descriptor. The client repeats the exchange for
each further socket.

```
Client                                       Server
  |  connect(path)                             |
  |------------------------------------------->|
  |  sendmsg: 1 octet + SCM_RIGHTS { fd }      |
  |------------------------------------------->|
  |                                            |  recvmsg, validate,
  |                                            |  exempt fd, close its copy
  |  1 octet: 0x00 success / 0x01 failure      |
  |<-------------------------------------------|
  |  close                                     |  close
```

### 6.1. Request

The client MUST send exactly one message, with `sendmsg`, and MUST NOT send
any other octet on that connection. The message contains:

- a payload of exactly one octet, whose value receivers MUST ignore;
- exactly one `SCM_RIGHTS` ancillary message, carrying exactly one file
  descriptor.

### 6.2. Reading and Validating the Request

The kernel attaches the ancillary data to the first octet of the payload it
was sent with. A server that reads that octet with `read` or `recv` loses the
descriptor. The server MUST therefore read the payload octet and the
ancillary data with a single `recvmsg`, with room for the ancillary data of
one descriptor.

The request is valid only if that read returns exactly one payload octet and
exactly one `SCM_RIGHTS` message carrying exactly one descriptor, and the
system did not report the ancillary data as truncated (`MSG_CTRUNC`). The
server MUST NOT exempt any descriptor from a request that is not valid, and
MUST close every descriptor such a request delivered.

The server MUST reject a descriptor that does not name a socket, which it can
detect because `getsockopt` with `SO_TYPE` fails on it. This document places
no requirement on the family, type, or protocol of the socket: a TCP, UDP,
ICMP, raw, or packet socket can be submitted alike. Whether the server can
exempt it is up to the exemption (Section 8).

### 6.3. Reply

After a valid request, the server attempts exemption. It MUST NOT write the
reply before that attempt has finished. It then MUST write exactly one octet:

- `0x00` if exemption succeeded (Section 8).
- `0x01` if exemption failed, or if the request was not valid.

The server SHOULD close the connection after the reply. The client MUST read
exactly one octet and MUST close the connection after that read.

The client MUST treat the exchange as a success only when the octet is
`0x00`. Any other value, a read that returns no octet, or a connection that
closes before an octet arrives is a failure.

### 6.4. Descriptor Ownership

The descriptor received through `SCM_RIGHTS` is a new reference to the same
open file description. The server MUST close its reference when it no longer
needs it. Closing that reference does not close the client's socket. The
server MAY close its reference before sending the reply.

### 6.5. Timeouts

Either side SHOULD bound the whole exchange with a timeout and treat its
expiry as a failure. Without one, a peer that connects and never sends holds
the connection open indefinitely.

## 7. Client Requirements

The client MUST submit the descriptor after the socket exists and before
`connect`, `sendto`, or `sendmsg`. An interface binding affects routing
decisions made after it is applied.

The client MUST submit every socket whose packets would otherwise enter the
tunnel. That includes a socket created for a reconnect, a connection
migration, or a change of remote port.

The client MUST NOT submit a socket that will only exchange packets with a
loopback address. Loopback traffic does not enter the tunnel, and on Linux a
socket bound to a physical interface cannot reach `127.0.0.1`.

After a failure the client MAY still use the socket, accepting that its
packets may enter the tunnel. A client that requires its traffic to stay
outside the tunnel SHOULD close the socket instead.

Where exemption binds the socket to an interface, the socket keeps the
interface chosen for that exchange. After the host's default network
changes, the client SHOULD open new sockets and run the exchange again.

## 8. Exemption

How a server exempts a descriptor is outside this protocol. A reply of
`0x00` means that the server has finished every operation its configuration
calls for, and that the client may use the socket. When the configuration
calls for none, for example because it provides neither an interface nor a
routing mark, the server leaves the socket unchanged and replies `0x00`. When
an operation that the configuration calls for cannot be applied to the
submitted socket, for example because it is defined only for some address
families, the exemption fails.

Current practice is:

- Android. Pass the descriptor to `VpnService.protect`.
- Linux. Bind the socket to an interface with `SO_BINDTOIFINDEX` or
  `SO_BINDTODEVICE`, set `SO_MARK` to a mark that policy routing uses for
  packets that must stay out of the tunnel, or both. The interface is one the
  operator selected, or otherwise the default interface at the time of the
  exchange.
- macOS. Set `IP_BOUND_IF` or `IPV6_BOUND_IF` to that same choice of
  interface.

On failure the protocol carries no reason. The server SHOULD record the
reason for its operator.

## 9. Compatibility

Deployed implementations differ from this document in three ways:

- Husi servers before this document replied `0x01` for success and `0x00`
  for failure. shadowsocks-android before [SS-ANDROID-IPC] replied `0x00`
  for success and wrote nothing on failure.
- The client in sing (`control.ProtectPath`) and the client in
  [MATSURI-HYSTERIA] treat any reply octet as success.
- Servers that follow an earlier revision of this document reject every
  socket that is not an `AF_INET` or `AF_INET6` socket of type `SOCK_STREAM`
  or `SOCK_DGRAM`, and reply `0x01` for it.

Clients of the second kind interoperate with every server above, but cannot
detect a failure. They behave as a client that ignores failures
(Section 7).

## 10. Security Considerations

Exemption lets a local process send packets outside the tunnel. Mode `0777`,
which Section 5 recommends, lets any local user request that operation for a
socket the user holds.

A deployment can restrict the socket mode, place the socket in a directory
that other users cannot traverse, or use the peer check in Section 5. The
server MAY reject a peer whose user is not expected to run clients.

Accepting a socket of any family, type, or protocol (Section 6.2) gives a
client nothing beyond exemption itself, because the client can submit only a
socket it already holds.

The payload octet is not a credential and not a version. This document
defines no authentication and no version negotiation. Peers recognize the
protocol by the path they were given.

The server does not learn the remote address, because the client submits the
descriptor before `connect`.

A local process can open many connections and leave them idle. The timeout in
Section 6.5 limits how long each one is held.

## 11. References

### 11.1. Normative References

[RFC2119] Bradner, S., "Key words for use in RFCs to Indicate Requirement
Levels", BCP 14, RFC 2119, March 1997.

[RFC8174] Leiba, B., "Ambiguity of Uppercase vs Lowercase in RFC 2119 Key
Words", BCP 14, RFC 8174, May 2017.

### 11.2. Informative References

[SS-ANDROID] shadowsocks-android, "add management thread", commit 809f4c1,
<https://github.com/shadowsocks/shadowsocks-android/commit/809f4c13e01c2fb4c88b42ba68bc5576a5e755a8>.

[SS-ANDROID-IPC] shadowsocks-android, "refine IPC handling", commit 7c31f5e,
<https://github.com/shadowsocks/shadowsocks-android/commit/7c31f5e5b4a6>.

[SS-LIBEV] shadowsocks-libev, "use blocking IPC", commit 94a1c4f,
<https://github.com/shadowsocks/shadowsocks-libev/commit/94a1c4fa10c6af71600048e43b148f8e8b8ab8b2>.

[MATSURI-HYSTERIA] MatsuriDayo/hysteria, commit 8e55493,
<https://github.com/MatsuriDayo/hysteria/commit/8e554937537329fc1091dc5365a7978327013626>.

## Appendix A. Minimal C Implementation

The following code is a client and a server that conform to this document. It
is released under the MIT License. It builds on Linux and macOS with a C99
compiler. Exemption (Section 8) is left to the `exempt` callback that the
caller supplies. Creating, binding, and accepting on the listening socket
(Section 5) is also left to the caller, which passes each accepted connection
to `protect_serve`.

```c
/* SPDX-License-Identifier: MIT
 * Copyright (C) 2026 by Husi authors
 *
 * Minimal client and server of the Socket Protect Path Protocol.
 */

#include <string.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/un.h>
#include <unistd.h>

#define PROTECT_SUCCESS 0x00
#define PROTECT_FAILURE 0x01
#define PROTECT_TIMEOUT_SECONDS 5

static int set_timeout(int connection)
{
    struct timeval timeout = { .tv_sec = PROTECT_TIMEOUT_SECONDS };

    if (setsockopt(connection, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout)) < 0)
        return -1;
    return setsockopt(connection, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
}

/* Client: submit fd to the server listening on path. Returns 0 on success. */
int protect_fd(const char *path, int fd)
{
    struct sockaddr_un address = { .sun_family = AF_UNIX };
    char payload = 0;
    char reply;
    union {
        struct cmsghdr header;
        char buffer[CMSG_SPACE(sizeof(int))];
    } control = { 0 };
    struct iovec iov = { .iov_base = &payload, .iov_len = 1 };
    struct msghdr message = {
        .msg_iov = &iov,
        .msg_iovlen = 1,
        .msg_control = control.buffer,
        .msg_controllen = sizeof(control.buffer),
    };
    struct cmsghdr *cmsg = CMSG_FIRSTHDR(&message);
    int connection;
    int result = -1;

    if (strlen(path) >= sizeof(address.sun_path))
        return -1;
    strcpy(address.sun_path, path);

    cmsg->cmsg_level = SOL_SOCKET;
    cmsg->cmsg_type = SCM_RIGHTS;
    cmsg->cmsg_len = CMSG_LEN(sizeof(int));
    memcpy(CMSG_DATA(cmsg), &fd, sizeof(int));

    connection = socket(AF_UNIX, SOCK_STREAM, 0);
    if (connection < 0)
        return -1;
    if (set_timeout(connection) == 0
        && connect(connection, (struct sockaddr *)&address, sizeof(address)) == 0
        && sendmsg(connection, &message, 0) == 1
        && read(connection, &reply, 1) == 1
        && reply == PROTECT_SUCCESS)
        result = 0;
    close(connection);
    return result;
}

/* Returns the single descriptor of a valid request, or -1. */
static int receive_request(int connection)
{
    char payload;
    union {
        struct cmsghdr header;
        char buffer[CMSG_SPACE(sizeof(int))];
    } control;
    struct iovec iov = { .iov_base = &payload, .iov_len = 1 };
    struct msghdr message = {
        .msg_iov = &iov,
        .msg_iovlen = 1,
        .msg_control = control.buffer,
        .msg_controllen = sizeof(control.buffer),
    };
    struct cmsghdr *cmsg;
    ssize_t received = recvmsg(connection, &message, 0);
    int received_fd = -1;
    int fd_count = 0;

    if (received < 0)
        return -1;
    for (cmsg = CMSG_FIRSTHDR(&message); cmsg != NULL; cmsg = CMSG_NXTHDR(&message, cmsg)) {
        if (cmsg->cmsg_level != SOL_SOCKET || cmsg->cmsg_type != SCM_RIGHTS)
            continue;
        size_t count = (cmsg->cmsg_len - CMSG_LEN(0)) / sizeof(int);
        for (size_t i = 0; i < count; i++) {
            int fd;
            memcpy(&fd, CMSG_DATA(cmsg) + i * sizeof(int), sizeof(int));
            if (fd_count++ == 0)
                received_fd = fd;
            else
                close(fd);
        }
    }
    if (received != 1 || fd_count != 1 || (message.msg_flags & MSG_CTRUNC)) {
        if (received_fd >= 0)
            close(received_fd);
        return -1;
    }
    return received_fd;
}

static int is_socket(int fd)
{
    int type;
    socklen_t type_length = sizeof(type);

    return getsockopt(fd, SOL_SOCKET, SO_TYPE, &type, &type_length) == 0;
}

/* Server: handle one accepted connection. exempt returns 0 on success. */
void protect_serve(int connection, int (*exempt)(int fd))
{
    char reply = PROTECT_FAILURE;
    int fd;

    if (set_timeout(connection) == 0) {
        fd = receive_request(connection);
        if (fd >= 0) {
            if (is_socket(fd) && exempt(fd) == 0)
                reply = PROTECT_SUCCESS;
            close(fd);
        }
        write(connection, &reply, 1);
    }
    close(connection);
}
```

The MIT License text that applies to the code above:

```
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
