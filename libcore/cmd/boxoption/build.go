package main

import (
	"reflect"
	"strings"
	"unsafe"

	F "github.com/sagernet/sing/common/format"
)

const (
	classSpace = "    "     // 4
	fieldSpace = "        " // 8

	public      = "public "
	staticClass = "static class "
	extends     = "extends "
)

const (
	extendsBox = "SingBoxOption"
)

var (
	// builder is a shared strings.Builder for building class content.
	// It is safe because we just use one thread.
	builder = &strings.Builder{}

	// builderAddr is the first field of builder.
	builderAddr **strings.Builder
	// builderBuf points to builder.buf.
	// Because builder.Reset() will clean the buf, but we want to reuse the buf.
	// Reuse the buf can reduce the stress of GC.
	builderBuf *[]byte
)

// maxBuilderSize is the maximum size of the builder.
// We found this is the max size the builder will use.
const maxBuilderSize = 2048

func init() {
	builderAddr = (**strings.Builder)(unsafe.Pointer(reflect.ValueOf(builder).Elem().Field(0).UnsafeAddr()))
	builderBuf = (*[]byte)(unsafe.Pointer(reflect.ValueOf(builder).Elem().Field(1).UnsafeAddr()))

	*builderBuf = make([]byte, maxBuilderSize)
}

// resetBuilder resets the builder but keeps its buf with large capcity.
func resetBuilder() {
	*builderAddr = nil
	clear(*builderBuf)
	*builderBuf = (*builderBuf)[:0]
}

func buildClass(opt any, belongs string) string {
	value := reflect.Indirect(reflect.ValueOf(opt))
	valueType := value.Type()

	resetBuilder()

	var fieldName string
	if belongs != extendsBox {
		fieldName = belongs + "_" + strings.ReplaceAll(valueType.Name(), belongs, "")
	} else {
		fieldName = valueType.Name()
	}
	// public static class ClashAPIOptions extends SingBoxOption {
	builder.WriteString(
		F.ToString(
			classSpace, public, staticClass,
			fieldName, " ",
			extends, belongs, " ",
			"{\n\n",
		),
	)

	// public String xxx;
	// public Boolean xxx;
	// public Integer xxx;
	builder.WriteString(buildContent(valueType))

	// }
	builder.WriteString(F.ToString(classSpace, "}\n"))

	// log.Trace("Builder cap: ", builder.Cap(), " Length: ", builder.Len())

	// builder.String() returns an unsafe point of buf, so copy a new string here.
	return string(*builderBuf)
}

func buildContent(valueType reflect.Type) string {
	builder := &strings.Builder{}
	for i := 0; i < valueType.NumField(); i++ {
		field := valueType.Field(i)

		tag := field.Tag.Get("json")
		if tag == "-" {
			continue
		}
		tag, _ = strings.CutSuffix(tag, ",omitempty")

		if field.Type.Kind() == reflect.Struct {
			builder.WriteString(F.ToString(fieldSpace, "// Generate note: nested type ", field.Name, "\n"))
			builder.WriteString(buildContent(field.Type))
			continue
		}

		if tag == reservedDefault || tag == reservedFinal {
			// @SerializedName("default")
			// public String default_;
			builder.WriteString(F.ToString(fieldSpace, "@SerializedName(\"", tag, "\")\n"))
			tag += "_"
		}

		typeName := className(field.Type)
		// Example:
		//         public String listen;
		builder.WriteString(F.ToString(fieldSpace, public, typeName, " ", tag, ";\n\n"))
	}

	return builder.String()
}

const (
	javaBoolean = "Boolean"
	javaInteger = "Integer"
	javaLong    = "Long"
	javaString  = "String"

	reservedDefault = "default"
	reservedFinal   = "final"
)

func className(valueType reflect.Type) string {
	switch valueType.Kind() {
	case reflect.Ptr:
		return className(valueType.Elem())
	case reflect.Bool:
		return javaBoolean
	case reflect.Uint16:
		if valueType.Name() == "DNSQueryType" {
			return javaString
		}
		return javaInteger
	case reflect.Int, reflect.Int32, reflect.Uint32:
		return javaInteger
	case reflect.Int64:
		if valueType.Name() == "Duration" {
			return javaString
		}
		return javaLong
	case reflect.Uint64:
		return javaLong
	case reflect.String:
		return javaString
	case reflect.Slice:
		elem := valueType.Elem()
		if elem.Kind() == reflect.Uint8 {
			// Go json save []uint8 or []byte as base64 string
			return javaString
		}
		return "Listable<" + className(elem) + ">"
	case reflect.Map:
		return "Map<" + className(valueType.Key()) + ", " + className(valueType.Elem()) + ">"
	case reflect.Struct:
		valueName := valueType.Name()
		if valueName == "ListenAddress" || valueName == "AddrPrefix" || valueName == "Prefix" {
			return javaString
		}
		return valueType.Name()
	case reflect.Uint8:
		if valueType.Name() == "DomainStrategy" {
			return javaString
		}
		return javaInteger
	default:
		panic(F.ToString("[", valueType.Name(), "] is not  included"))
	}
}
