#ifndef HUSI_ROOTCERTS_DARWIN_H
#define HUSI_ROOTCERTS_DARWIN_H

#include <CoreFoundation/CoreFoundation.h>
#include <Security/Security.h>
#include <stdbool.h>

// On success, the caller owns both concatenated-DER buffers. On failure,
// both outputs are NULL.
OSStatus husi_copy_root_certificates(bool with_user_trust,
                                    CFDataRef *certificates,
                                    CFDataRef *distrusted);

#endif
