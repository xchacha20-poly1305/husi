package main

import (
	"fmt"
	"log"
	"reflect"
	"strings"

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

// TODO link extends

func buildClass(opt any, belongs string) string {
	value := reflect.Indirect(reflect.ValueOf(opt))
	valueType := value.Type()

	builder := &strings.Builder{}

	var fieldName string
	if belongs != extendsBox {
		fieldName = belongs + "_" + valueType.Name()
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

	return builder.String()
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

		typeName := getTypeName(field.Type)
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

func getTypeName(valueType reflect.Type) string {
	switch valueType.Kind() {
	case reflect.Ptr:
		return getTypeName(valueType.Elem())
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
		return "List<" + getTypeName(elem) + ">"
	case reflect.Map:
		return "Map<" + getTypeName(valueType.Key()) + ", " + getTypeName(valueType.Elem()) + ">"
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
		log.Panic("[", valueType.Name(), "] is invalid: ", fmt.Sprint(valueType))
	}
	return ""
}
