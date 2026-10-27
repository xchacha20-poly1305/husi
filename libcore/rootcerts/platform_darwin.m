//go:build darwin && cgo

#include "platform_darwin.h"

static OSStatus append_certificate(CFMutableDataRef output, SecCertificateRef certificate) {
    CFDataRef data = SecCertificateCopyData(certificate);
    if (data == NULL) {
        return errSecAllocate;
    }
    CFDataAppendBytes(output, CFDataGetBytePtr(data), CFDataGetLength(data));
    CFRelease(data);
    return errSecSuccess;
}

// A root pool cannot represent per-host or per-application denials. Exclude
// certificates with any explicit deny, conservatively retaining that policy.
static OSStatus append_denied_certificates(SecTrustSettingsDomain domain, CFMutableDataRef output) {
    CFArrayRef certificates = NULL;
    OSStatus status = SecTrustSettingsCopyCertificates(domain, &certificates);
    if (status == errSecNoTrustSettings) {
        return errSecSuccess;
    }
    if (status != errSecSuccess) {
        return status;
    }
    for (CFIndex i = 0; i < CFArrayGetCount(certificates); i++) {
        SecCertificateRef certificate = (SecCertificateRef)CFArrayGetValueAtIndex(certificates, i);
        CFArrayRef settings = NULL;
        status = SecTrustSettingsCopyTrustSettings(certificate, domain, &settings);
        if (status == errSecItemNotFound) {
            status = errSecSuccess;
            continue;
        }
        if (status != errSecSuccess) {
            break;
        }
        if (settings == NULL) {
            continue;
        }
        for (CFIndex j = 0; j < CFArrayGetCount(settings); j++) {
            CFDictionaryRef setting = (CFDictionaryRef)CFArrayGetValueAtIndex(settings, j);
            CFNumberRef result = (CFNumberRef)CFDictionaryGetValue(setting, kSecTrustSettingsResult);
            int value = kSecTrustSettingsResultUnspecified;
            if (result != NULL && CFNumberGetValue(result, kCFNumberIntType, &value) &&
                value == kSecTrustSettingsResultDeny) {
                status = append_certificate(output, certificate);
                break;
            }
        }
        CFRelease(settings);
        if (status != errSecSuccess) {
            break;
        }
    }
    CFRelease(certificates);
    return status;
}

static OSStatus append_keychain_certificates(const char *path, CFMutableDataRef output) {
    SecKeychainRef keychain = NULL;
    OSStatus status = SecKeychainOpen(path, &keychain);
    if (status != errSecSuccess) {
        return status;
    }
    const void *keychains[] = {keychain};
    CFArrayRef search_list = CFArrayCreate(NULL, keychains, 1, &kCFTypeArrayCallBacks);
    CFRelease(keychain);
    if (search_list == NULL) {
        return errSecAllocate;
    }
    const void *keys[] = {kSecClass, kSecMatchSearchList, kSecMatchLimit, kSecReturnRef};
    const void *values[] = {kSecClassCertificate, search_list, kSecMatchLimitAll, kCFBooleanTrue};
    CFDictionaryRef query = CFDictionaryCreate(NULL, keys, values, 4,
                                              &kCFTypeDictionaryKeyCallBacks,
                                              &kCFTypeDictionaryValueCallBacks);
    CFRelease(search_list);
    if (query == NULL) {
        return errSecAllocate;
    }
    CFTypeRef result = NULL;
    status = SecItemCopyMatching(query, &result);
    CFRelease(query);
    if (status == errSecItemNotFound) {
        return errSecSuccess;
    }
    if (status != errSecSuccess) {
        return status;
    }
    CFArrayRef certificates = (CFArrayRef)result;
    for (CFIndex i = 0; i < CFArrayGetCount(certificates); i++) {
        status = append_certificate(output, (SecCertificateRef)CFArrayGetValueAtIndex(certificates, i));
        if (status != errSecSuccess) {
            break;
        }
    }
    CFRelease(certificates);
    return status;
}

OSStatus husi_copy_root_certificates(bool with_user_trust,
                                    CFDataRef *certificates,
                                    CFDataRef *distrusted) {
    *certificates = NULL;
    *distrusted = NULL;
    CFMutableDataRef roots = CFDataCreateMutable(NULL, 0);
    CFMutableDataRef denied = CFDataCreateMutable(NULL, 0);
    OSStatus status = errSecAllocate;
    if (roots == NULL || denied == NULL) {
        goto cleanup;
    }

    // User denials apply even when user-added anchors are not requested.
    const SecTrustSettingsDomain domains[] = {
        kSecTrustSettingsDomainSystem,
        kSecTrustSettingsDomainAdmin,
        kSecTrustSettingsDomainUser,
    };
    for (size_t i = 0; i < sizeof(domains) / sizeof(domains[0]); i++) {
        status = append_denied_certificates(domains[i], denied);
        if (status != errSecSuccess) {
            goto cleanup;
        }
    }
    status = append_keychain_certificates(
        "/System/Library/Keychains/SystemRootCertificates.keychain", roots);
    if (status != errSecSuccess) {
        goto cleanup;
    }
    if (with_user_trust) {
        status = append_keychain_certificates("/Library/Keychains/System.keychain", roots);
        if (status != errSecSuccess) {
            goto cleanup;
        }
    }
    *certificates = roots;
    *distrusted = denied;
    return errSecSuccess;

cleanup:
    if (roots != NULL) {
        CFRelease(roots);
    }
    if (denied != NULL) {
        CFRelease(denied);
    }
    return status;
}
