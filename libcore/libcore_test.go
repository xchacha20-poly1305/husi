package libcore

import (
	"os"
	"strconv"
)

const testInternetEnv = "HUSI_TEST_INTERNET"

var testInternet, _ = strconv.ParseBool(os.Getenv(testInternetEnv))
