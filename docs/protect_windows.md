# Socket Protect Path Protocol for Windows

## Abstract

This document specifies a Windows counterpart of the Socket Protect Path
Protocol [PROTECT]. A process that must reach a host outside a tunnel hands
each socket to the program that owns the tunnel, and that program exempts the
socket before any packet is sent. Windows `AF_UNIX` sockets cannot carry
descriptors, so the client sends the value of its `SOCKET` handle, and the
server duplicates that handle out of the client process. The exchange runs on
a Unix-domain stream socket and ends with the same one-octet reply as
[PROTECT].

## Status of This Memo

This document is a draft. It may change in incompatible ways, and
implementations SHOULD NOT be deployed where the peer cannot be upgraded with
them.

This document is a project-level specification of a local inter-process
protocol. It is not an Internet Standards Track specification.

## 1. Introduction

[PROTECT] passes the socket as an `SCM_RIGHTS` descriptor and declares
Windows out of scope, because Windows `AF_UNIX` sockets carry no descriptors.
This document keeps the roles, the transport type, the reply, and the client
requirements of [PROTECT], and replaces only the request: the client sends the
numeric value of its socket handle, and the server, which learns the client's
process identifier from the connection, duplicates that handle into its own
process.

On Windows a process needs no privilege to bind its own socket to an interface
with `IP_UNICAST_IF`. The client still hands the socket to the server, because
only the server knows which interface keeps packets out of its tunnel, and
that choice changes when the default network changes.

The listening path is agreed out of band. This document does not define how
that path is configured.

## 2. Requirements Language

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
"SHOULD", "SHOULD NOT", "RECOMMENDED", "NOT RECOMMENDED", "MAY", and
"OPTIONAL" in this document are to be interpreted as described in BCP 14
[RFC2119] [RFC8174] when, and only when, they appear in all capitals, as
shown here.

## 3. Terminology

This document uses the terms Client, Server, and Exemption as defined in
[PROTECT], Section 4, and the following:

- Handle value: the numeric value of a `SOCKET`, which is a kernel handle in
  the client's handle table.
- Peer process identifier: the process identifier that
  `SIO_AF_UNIX_GETPEERPID` returns for a connected `AF_UNIX` socket.

## 4. Transport

The server MUST listen on an `AF_UNIX` socket of type `SOCK_STREAM`, bound to
a filesystem pathname. Windows provides `AF_UNIX` from Windows 10 version
1803. The server also needs the `SIO_AF_UNIX_GETPEERPID` control code from
`afunix.h`.

A relative path is resolved by each process against its own working
directory. A relative path names one socket only when both processes resolve
it to the same pathname.

What a server does when the pathname already exists, including when another
server still listens on it, is not defined by this document.

Connecting requires write access to the socket file, so the file's DACL
decides which users can be clients. The server SHOULD give every local user
read and write access to the socket file, so a client running as another user
can connect. This corresponds to mode `0777` in [PROTECT]. The server MAY use
a stricter DACL.

The server MAY reject a peer it does not expect, after identifying it as in
Section 5.2.

## 5. Exchange

One connection carries one socket. The client repeats the exchange for each
further socket.

```
Client                                       Server
  |  connect(path)                             |
  |------------------------------------------->|
  |  8 octets: handle value, little-endian     |
  |------------------------------------------->|
  |                                            |  SIO_AF_UNIX_GETPEERPID,
  |                                            |  OpenProcess, DuplicateHandle,
  |                                            |  validate, exempt, close its copy
  |  1 octet: 0x00 success / 0x01 failure      |
  |<-------------------------------------------|
  |  close                                     |  close
```

### 5.1. Request

The client MUST send exactly eight octets and MUST NOT send any other octet on
that connection. The eight octets are the handle value of the socket to
exempt, as an unsigned integer in little-endian byte order.

Windows keeps handle values within 32 bits, so the upper four octets are zero
in practice. The request has eight octets so that 32-bit and 64-bit processes
send the same format.

The request uses little-endian byte order rather than network byte order
because it never leaves the host, and both peers run on Windows, whose
supported architectures are all little-endian. The eight octets are therefore
the in-memory form of the handle value as a 64-bit integer, and a client can
send that integer without conversion.

### 5.2. Receiving and Validating the Request

The server reads the request with ordinary receive operations. A request is
valid only if the server reads eight octets before the connection closes or
the timeout expires.

The server resolves the handle value in these steps:

1. The server obtains the peer process identifier by issuing
   `SIO_AF_UNIX_GETPEERPID` with `WSAIoctl` on the accepted connection.
2. The server opens that process with `OpenProcess` and the access right
   `PROCESS_DUP_HANDLE`.
3. After reading the request, the server calls `DuplicateHandle` with that
   process as the source, the handle value as the source handle, its own
   process as the target, and `DUPLICATE_SAME_ACCESS`. The server MUST NOT
   pass `DUPLICATE_CLOSE_SOURCE`.
4. The server closes the process handle.

Steps 1 and 2 MAY happen before or after the server reads the request.
Section 10 explains why doing them first is preferable.

The server MUST take the source process from the connection and MUST NOT
accept a process identifier from the client. That way a client can name only
a handle that it holds itself.

The server MUST reject the request if any of these steps fails.

The server then reads the protocol of the duplicated handle with `getsockopt`
at level `SOL_SOCKET` and option `SO_PROTOCOL_INFOW`. The server MUST reject
the handle if:

- `getsockopt` fails, which includes a handle that does not name a socket; or
- `dwServiceFlags1` lacks `XP1_IFS_HANDLES`.

As in [PROTECT], this document places no requirement on the family, type, or
protocol of the socket. Whether the server can exempt it is up to the
exemption (Section 7).

The `XP1_IFS_HANDLES` check keeps out sockets of providers whose handles are
not kernel file handles, such as non-IFS layered providers. Such a provider keeps socket
state in the client process, and a duplicated handle does not carry that
state.

The server MUST NOT exempt a socket from a request that is not valid.

### 5.3. Reply

After a valid request, the server attempts exemption. It MUST NOT write the
reply before that attempt has finished. It then MUST write exactly one octet:

- `0x00` if exemption succeeded (Section 7).
- `0x01` if exemption failed, or if the request was not valid.

The server SHOULD close the connection after the reply. The client MUST read
exactly one octet and MUST close the connection after that read.

The client MUST treat the exchange as a success only when the octet is
`0x00`. Any other value, a read that returns no octet, or a connection that
closes before an octet arrives is a failure.

### 5.4. Handle Ownership

The duplicated handle is a new reference to the client's socket. The server
MUST close it when it no longer needs it, with `closesocket`. If `closesocket`
fails because the handle is not a socket, the server MUST close it with
`CloseHandle`. Closing the server's reference does not close the client's
socket. The server MAY close its reference before sending the reply.

The client MUST keep its socket open until it has read the reply. If the
client closed the socket earlier, the handle value could name another object
of the client by the time the server duplicates it.

### 5.5. Timeouts

Either side SHOULD bound the whole exchange with a timeout and treat its
expiry as a failure. Without one, a peer that connects and never sends holds
the connection open indefinitely.

## 6. Client Requirements

The client requirements of [PROTECT], Section 7, apply unchanged: the client
submits each socket after it exists and before `connect`, `sendto`, or
`WSASendTo`; it submits every socket whose packets would otherwise enter the
tunnel; it does not submit a socket that will only exchange packets with a
loopback address; and it opens new sockets after the default network changes.

The server needs `PROCESS_DUP_HANDLE` access to the client process. A server
running as `LocalSystem` has that access to processes of other users, except
protected processes. A server running as an ordinary user generally has it
only to processes of the same user at the same or a lower integrity level. A
client that the server cannot open receives `0x01`.

## 7. Exemption

How a server exempts a socket is outside this protocol, as in [PROTECT],
Section 8. A reply of `0x00` means that the server has finished every
operation its configuration calls for. When the configuration calls for none,
the server leaves the socket unchanged and replies `0x00`.

Current practice is to bind the socket to an interface: set `IP_UNICAST_IF`
at level `IPPROTO_IP` to the interface index in network byte order, set
`IPV6_UNICAST_IF` at level `IPPROTO_IPV6` to the interface index in host byte
order, or both. The server does not know the remote address, so for an
`AF_INET6` socket it sets both options and succeeds if either succeeds.
`IP_UNICAST_IF` fails on an `IPV6_V6ONLY` socket, and `IPV6_UNICAST_IF`
fails on an `AF_INET` socket. The interface is one the operator selected, or
otherwise the default interface at the time of the exchange.

On failure the protocol carries no reason. The server SHOULD record the
reason for its operator.

### 7.1. ICMP

Windows has no ICMP datagram sockets. A process sends ICMP through a raw
socket, which only an administrator can create and which the server accepts
like any other socket, or through `IcmpSendEcho2Ex` and related functions.
Those functions use no socket that the process could submit, so ICMP sent
through them is out of scope. The documentation of `IP_UNICAST_IF` does not
limit the option to particular socket types.

## 8. Design Rationale

### 8.1. Why Not WSADuplicateSocket

The documented way to share a socket with another process is
`WSADuplicateSocketW` in the source process, which fills a
`WSAPROTOCOL_INFOW` structure, and `WSASocketW` with that structure in the
target process. The structure carries a handle value that the provider in the
target process uses to recover the socket. A server cannot tell a structure
that `WSADuplicateSocketW` produced from one that a client composed itself,
naming a handle that the server already holds. A privileged server would then
exempt and close one of its own sockets at a client's request.

With the server-side duplication of Section 5.2, the source of every handle
is the client's own handle table, as with `SCM_RIGHTS` in [PROTECT].

### 8.2. Duplicating Socket Handles

The documentation of `DuplicateHandle` advises against duplicating socket
handles, because a layered service provider keeps per-process state that the
target process lacks. The `XP1_IFS_HANDLES` check of Section 5.2 rejects
sockets of such providers. A base provider socket is a kernel file handle,
and Winsock in the target process recognizes it the same way it recognizes a
socket handle inherited by a child process.

## 9. Compatibility

This document defines a protocol distinct from [PROTECT], and the two do not
interoperate. A client and a server agree on the variant by platform: on
Windows they use this document, and elsewhere [PROTECT].

No deployed implementation predates this document.

## 10. Security Considerations

Exemption lets a local process send packets outside the tunnel. The DACL that
Section 4 recommends lets any local user request that operation for a socket
the user holds. A deployment can restrict the DACL, place the socket in a
directory that other users cannot traverse, or reject unexpected peers after
identifying them.

Accepting a socket of any family, type, or protocol (Section 5.2) gives a
client nothing beyond exemption itself, because the client can submit only a
socket it already holds.

`PROCESS_DUP_HANDLE` access amounts to full control of the client process.
The server, not the client, acquires it, and only where the operating system
already grants it (Section 6). The server SHOULD close the process handle as
soon as the duplication has finished, and MUST NOT use it for anything else.

`SIO_AF_UNIX_GETPEERPID` returns the identifier of the process that
connected. If that process exits before the server opens it, Windows can
reuse the identifier for another process, and the server would duplicate a
handle out of that process instead. The client that caused this has already
exited and cannot use the result, but the server could exempt a socket of an
unrelated process. A server narrows this window by opening the peer
process as soon as it accepts the connection, before it reads the request
(Section 5.2).

The request carries no credential and no version. Peers recognize the
protocol by the path they were given.

The server does not learn the remote address, because the client submits the
socket before `connect`.

A local process can open many connections and leave them idle. The timeout in
Section 5.5 limits how long each one is held.

## 11. References

### 11.1. Normative References

[PROTECT] Husi, "Socket Protect Path Protocol", `docs/protect.md`.

[RFC2119] Bradner, S., "Key words for use in RFCs to Indicate Requirement
Levels", BCP 14, RFC 2119, March 1997.

[RFC8174] Leiba, B., "Ambiguity of Uppercase vs Lowercase in RFC 2119 Key
Words", BCP 14, RFC 8174, May 2017.

### 11.2. Informative References

[AFUNIX] Microsoft, "AF_UNIX comes to Windows",
<https://devblogs.microsoft.com/commandline/af_unix-comes-to-windows/>.

[DUPLICATEHANDLE] Microsoft, "DuplicateHandle function",
<https://learn.microsoft.com/windows/win32/api/handleapi/nf-handleapi-duplicatehandle>.

[WSADUPLICATESOCKET] Microsoft, "WSADuplicateSocketW function",
<https://learn.microsoft.com/windows/win32/api/winsock2/nf-winsock2-wsaduplicatesocketw>.

[PROTECT-GO] protect-go, <https://github.com/xchacha20-poly1305/protect-go>.

## Appendix A. Minimal C Implementation

The following code is a client and a server that conform to this document. It
is released under the MIT License, whose text is in [PROTECT], Appendix A. It
builds for Windows with a C99 compiler and links against `ws2_32`. The caller
initializes Winsock with `WSAStartup`. Exemption (Section 7) is left to the
`exempt` callback that the caller supplies. Creating, binding, and accepting
on the listening socket (Section 4) is also left to the caller, which passes
each accepted connection to `protect_serve`.

```c
/* SPDX-License-Identifier: MIT
 * Copyright (C) 2026 by Husi authors
 *
 * Minimal client and server of the Socket Protect Path Protocol for Windows.
 */

#include <winsock2.h>
#include <windows.h>
#include <afunix.h>
#include <stdint.h>
#include <string.h>

#define PROTECT_SUCCESS 0x00
#define PROTECT_FAILURE 0x01
#define PROTECT_REQUEST_LENGTH 8
#define PROTECT_TIMEOUT_MILLISECONDS 5000

static int set_timeout(SOCKET connection)
{
    DWORD timeout = PROTECT_TIMEOUT_MILLISECONDS;

    if (setsockopt(connection, SOL_SOCKET, SO_RCVTIMEO, (const char *)&timeout, sizeof(timeout)) != 0)
        return -1;
    return setsockopt(connection, SOL_SOCKET, SO_SNDTIMEO, (const char *)&timeout, sizeof(timeout));
}

static int receive_all(SOCKET connection, char *buffer, int length)
{
    while (length > 0) {
        int received = recv(connection, buffer, length, 0);
        if (received <= 0)
            return -1;
        buffer += received;
        length -= received;
    }
    return 0;
}

/* Client: submit target to the server listening on path. Returns 0 on success.
 * target must stay open until this function returns. */
int protect_socket(const char *path, SOCKET target)
{
    struct sockaddr_un address = { .sun_family = AF_UNIX };
    uint64_t value = (uint64_t)target;
    char request[PROTECT_REQUEST_LENGTH];
    char reply;
    SOCKET connection;
    int result = -1;

    if (strlen(path) >= sizeof(address.sun_path))
        return -1;
    strcpy(address.sun_path, path);
    for (int i = 0; i < PROTECT_REQUEST_LENGTH; i++)
        request[i] = (char)(value >> (8 * i));

    connection = WSASocketW(AF_UNIX, SOCK_STREAM, 0, NULL, 0, WSA_FLAG_NO_HANDLE_INHERIT);
    if (connection == INVALID_SOCKET)
        return -1;
    if (set_timeout(connection) == 0
        && connect(connection, (struct sockaddr *)&address, sizeof(address)) == 0
        && send(connection, request, PROTECT_REQUEST_LENGTH, 0) == PROTECT_REQUEST_LENGTH
        && recv(connection, &reply, 1, 0) == 1
        && reply == PROTECT_SUCCESS)
        result = 0;
    closesocket(connection);
    return result;
}

/* Returns the server's own handle to the socket a valid request names,
 * or INVALID_SOCKET. */
static SOCKET receive_request(SOCKET connection)
{
    char request[PROTECT_REQUEST_LENGTH];
    uint64_t value = 0;
    ULONG peer_process_id;
    DWORD returned;
    HANDLE process;
    HANDLE duplicate;
    BOOL duplicated;

    /* Open the peer before reading, while it is most likely still alive. */
    if (WSAIoctl(connection, SIO_AF_UNIX_GETPEERPID, NULL, 0, &peer_process_id,
                 sizeof(peer_process_id), &returned, NULL, NULL) != 0)
        return INVALID_SOCKET;
    process = OpenProcess(PROCESS_DUP_HANDLE, FALSE, peer_process_id);
    if (process == NULL)
        return INVALID_SOCKET;
    if (receive_all(connection, request, PROTECT_REQUEST_LENGTH) != 0) {
        CloseHandle(process);
        return INVALID_SOCKET;
    }
    for (int i = 0; i < PROTECT_REQUEST_LENGTH; i++)
        value |= (uint64_t)(unsigned char)request[i] << (8 * i);
    duplicated = DuplicateHandle(process, (HANDLE)(uintptr_t)value, GetCurrentProcess(),
                                 &duplicate, 0, FALSE, DUPLICATE_SAME_ACCESS);
    CloseHandle(process);
    return duplicated ? (SOCKET)duplicate : INVALID_SOCKET;
}

/* Fails on a handle that is not a socket. */
static int is_kernel_socket(SOCKET received)
{
    WSAPROTOCOL_INFOW info;
    int length = sizeof(info);

    if (getsockopt(received, SOL_SOCKET, SO_PROTOCOL_INFOW, (char *)&info, &length) != 0)
        return 0;
    return (info.dwServiceFlags1 & XP1_IFS_HANDLES) != 0;
}

/* A duplicated handle that is not a socket must still be closed. */
static void close_received(SOCKET received)
{
    if (closesocket(received) != 0)
        CloseHandle((HANDLE)received);
}

/* Server: handle one accepted connection. exempt returns 0 on success. */
void protect_serve(SOCKET connection, int (*exempt)(SOCKET received))
{
    char reply = PROTECT_FAILURE;
    SOCKET received;

    if (set_timeout(connection) == 0) {
        received = receive_request(connection);
        if (received != INVALID_SOCKET) {
            if (is_kernel_socket(received) && exempt(received) == 0)
                reply = PROTECT_SUCCESS;
            close_received(received);
        }
        send(connection, &reply, 1, 0);
    }
    closesocket(connection);
}
```
