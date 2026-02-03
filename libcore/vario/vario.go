package vario

import (
	"encoding/binary"
	"io"
	"unsafe"

	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/varbin"
)

var (
	_ io.ByteReader = byteReadWriter{}
	_ io.ByteWriter = byteReadWriter{}
)

type byteReadWriter struct {
	io.Reader
	io.Writer
}

func (b byteReadWriter) ReadByte() (byte, error) {
	buffer := make([]byte, 1)
	_, err := b.Read(buffer)
	return buffer[0], err
}

func (b byteReadWriter) WriteByte(c byte) (err error) {
	_, err = b.Write([]byte{c})
	return err
}

func newByteReader(reader io.Reader) io.ByteReader {
	if byteReader, isByteReader := reader.(io.ByteReader); isByteReader {
		return byteReader
	}
	return byteReadWriter{Reader: reader}
}

func newByteWriter(writer io.Writer) io.ByteWriter {
	if byteWriter, isByteWriter := writer.(io.ByteWriter); isByteWriter {
		return byteWriter
	}
	return byteReadWriter{Writer: writer}
}

func WriteUvarint(writer io.Writer, v uint64) (err error) {
	byteWriter := newByteWriter(writer)
	_, err = varbin.WriteUvarint(byteWriter, v)
	return
}

func ReadUvarint(r io.Reader) (uint64, error) {
	byteReader := newByteReader(r)
	return binary.ReadUvarint(byteReader)
}

func WriteUint8(writer io.Writer, v uint8) error {
	_, err := writer.Write([]byte{v})
	return err
}

func ReadUint8(reader io.Reader) (uint8, error) {
	var buffer [1]byte
	_, err := io.ReadFull(reader, buffer[:])
	return buffer[0], err
}

func WriteBool(writer io.Writer, v bool) error {
	if v {
		return WriteUint8(writer, 1)
	}
	return WriteUint8(writer, 0)
}

func ReadBool(reader io.Reader) (bool, error) {
	value, err := ReadUint8(reader)
	if err != nil {
		return false, err
	}
	return value != 0, nil
}

func WriteInt16(writer io.Writer, v int16) error {
	var buffer [2]byte
	binary.BigEndian.PutUint16(buffer[:], uint16(v))
	_, err := writer.Write(buffer[:])
	return err
}

func ReadInt16(reader io.Reader) (int16, error) {
	var buffer [2]byte
	_, err := io.ReadFull(reader, buffer[:])
	if err != nil {
		return 0, err
	}
	return int16(binary.BigEndian.Uint16(buffer[:])), nil
}

func WriteInt32(writer io.Writer, v int32) error {
	var buffer [4]byte
	binary.BigEndian.PutUint32(buffer[:], uint32(v))
	_, err := writer.Write(buffer[:])
	return err
}

func ReadInt32(reader io.Reader) (int32, error) {
	var buffer [4]byte
	_, err := io.ReadFull(reader, buffer[:])
	if err != nil {
		return 0, err
	}
	return int32(binary.BigEndian.Uint32(buffer[:])), nil
}

func WriteInt64(writer io.Writer, v int64) error {
	var buffer [8]byte
	binary.BigEndian.PutUint64(buffer[:], uint64(v))
	_, err := writer.Write(buffer[:])
	return err
}

func ReadInt64(reader io.Reader) (int64, error) {
	var buffer [8]byte
	_, err := io.ReadFull(reader, buffer[:])
	if err != nil {
		return 0, err
	}
	return int64(binary.BigEndian.Uint64(buffer[:])), nil
}

func WriteString(writer io.Writer, s string) (err error) {
	err = WriteUvarint(writer, uint64(len(s)))
	if err != nil {
		return E.Cause(err, "write string length")
	}
	_, err = io.WriteString(writer, s)
	return
}

func ReadString(reader io.Reader) (string, error) {
	length, err := ReadUvarint(reader)
	if err != nil {
		return "", E.Cause(err, "read string length")
	}
	if length == 0 {
		return "", nil
	}
	buffer := make([]byte, int(length))
	_, err = io.ReadFull(reader, buffer)
	if err != nil {
		return "", err
	}
	return unsafe.String(unsafe.SliceData(buffer), length), nil
}

func WriteStringSlice(writer io.Writer, data []string) error {
	err := WriteUvarint(writer, uint64(len(data)))
	if err != nil {
		return E.Cause(err, "write string slice length")
	}
	for i := range data {
		err = WriteString(writer, data[i])
		if err != nil {
			return E.Cause(err, "write string ", i)
		}
	}
	return nil
}

func ReadStringSlice(reader io.Reader) ([]string, error) {
	length, err := ReadUvarint(reader)
	if err != nil {
		return nil, E.Cause(err, "read string slice length")
	}
	if length == 0 {
		return []string{}, nil
	}
	intLength := int(length)
	data := make([]string, intLength)
	for i := 0; i < intLength; i++ {
		data[i], err = ReadString(reader)
		if err != nil {
			return nil, E.Cause(err, "read string ", i)
		}
	}
	return data, nil
}

func WriteBytes(writer io.Writer, data []byte) (err error) {
	err = WriteUvarint(writer, uint64(len(data)))
	if err != nil {
		return E.Cause(err, "write bytes length")
	}
	_, err = writer.Write(data)
	return
}

func ReadBytes(reader io.Reader) ([]byte, error) {
	length, err := ReadUvarint(reader)
	if err != nil {
		return nil, E.Cause(err, "read bytes length")
	}
	buffer := make([]byte, int(length))
	_, err = io.ReadFull(reader, buffer)
	return buffer, err
}

type ReaderFromBinaryFunc[T any] func(reader io.Reader) (T, error)

type WriterToBinary interface {
	WriteToBinary(writer io.Writer) error
}

func WriteSlices[T WriterToBinary](writer io.Writer, data []T) (err error) {
	err = WriteUvarint(writer, uint64(len(data)))
	if err != nil {
		return E.Cause(err, "write slices length")
	}
	for i := range data {
		err := data[i].WriteToBinary(writer)
		if err != nil {
			return E.Cause(err, "write data ", i)
		}
	}
	return
}

func ReadSlices[T any](reader io.Reader, readFromBinary ReaderFromBinaryFunc[T]) ([]T, error) {
	length, err := ReadUvarint(reader)
	if err != nil {
		return nil, E.Cause(err, "read slices length")
	}
	if length == 0 {
		return []T{}, nil
	}
	intLength := int(length)
	data := make([]T, intLength)
	for i := 0; i < intLength; i++ {
		data[i], err = readFromBinary(reader)
		if err != nil {
			return nil, E.Cause(err, "read data ", i)
		}
	}
	return data, nil
}
