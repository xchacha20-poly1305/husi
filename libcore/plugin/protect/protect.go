package protect

type Protector interface {
	Protect(fileDescriptor int) error
}

// ProtectorFunc adapts a plain function into a Protector.
type ProtectorFunc func(fileDescriptor int) error

func (protectorFunc ProtectorFunc) Protect(fileDescriptor int) error {
	return protectorFunc(fileDescriptor)
}
