package fr.husi.ktx

expect object Logs {

    fun d(message: String)

    fun d(message: String, exception: Throwable)

    fun i(message: String)

    fun i(message: String, exception: Throwable)

    fun w(message: String)

    fun w(message: String, exception: Throwable)

    fun w(exception: Throwable)

    fun e(message: String)

    fun e(message: String, exception: Throwable)

    fun e(exception: Throwable)

    /** Empties the log file that bug reports attach. */
    fun clearFile()

}
