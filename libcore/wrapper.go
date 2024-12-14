package libcore

// StringWrapper is a wrapper for string.
// See: https://github.com/golang/go/issues/46893
//
// FIXME: remove after `bulkBarrierPreWrite: unaligned arguments` fixed
type StringWrapper struct {
	Value string
}

func wrapString(value string) *StringWrapper {
	return &StringWrapper{Value: value}
}
