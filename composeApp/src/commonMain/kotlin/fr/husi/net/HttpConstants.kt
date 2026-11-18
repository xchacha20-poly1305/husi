package fr.husi.net

object HttpStatus {
    const val OK = 200
    const val CREATED = 201
    const val ACCEPTED = 202
    const val NO_CONTENT = 204
    const val MULTI_STATUS = 207

    const val MOVED_PERMANENTLY = 301
    const val FOUND = 302
    const val SEE_OTHER = 303
    const val NOT_MODIFIED = 304
    const val TEMPORARY_REDIRECT = 307
    const val PERMANENT_REDIRECT = 308

    const val BAD_REQUEST = 400
    const val UNAUTHORIZED = 401
    const val FORBIDDEN = 403
    const val NOT_FOUND = 404
    const val METHOD_NOT_ALLOWED = 405
    const val CONFLICT = 409
    const val TOO_MANY_REQUESTS = 429

    const val INTERNAL_SERVER_ERROR = 500
    const val NOT_IMPLEMENTED = 501
    const val BAD_GATEWAY = 502
    const val SERVICE_UNAVAILABLE = 503
}

object HttpMethod {
    const val GET = "GET"
    const val HEAD = "HEAD"
    const val POST = "POST"
    const val PUT = "PUT"
    const val DELETE = "DELETE"
    const val CONNECT = "CONNECT"
    const val OPTIONS = "OPTIONS"
    const val TRACE = "TRACE"
    const val PATCH = "PATCH"

    /* WebDAV methods */

    const val PROPFIND = "PROPFIND"
    const val MKCOL = "MKCOL"
}
