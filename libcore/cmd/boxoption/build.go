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

	public        = "public "
	staticClass   = "static class "
	singBoxOption = "SingBoxOption"
	extends       = "extends "
)

func buildClass(opt any) string {
	value := reflect.Indirect(reflect.ValueOf(opt))
	valueType := value.Type()

	builder := &strings.Builder{}
	// public static class ClashAPIOptions extends SingBoxOption {
	builder.WriteString(
		F.ToString(
			classSpace, public, staticClass,
			addSuffix(valueType.Name(), "Options"), " ",
			extends, singBoxOption,
			"{\n\n",
		),
	)
	builder.WriteString(buildContent(valueType))
	builder.WriteString(F.ToString(classSpace, "}\n"))
	return builder.String()
}

func buildContent(valueType reflect.Type) string {
	builder := &strings.Builder{}
	for i := 0; i < valueType.NumField(); i++ {
		field := valueType.Field(i)
		tag := field.Tag.Get("json")
		if tag == "-" || strings.HasPrefix(tag, "$") {
			continue
		}
		tag, _ = strings.CutSuffix(tag, ",omitempty")
		if field.Type.Kind() == reflect.Struct {
			builder.WriteString(F.ToString(fieldSpace, "// generate note: nested type ", field.Name, "\n"))
			builder.WriteString(buildContent(field.Type))
			continue
		}
		typeName := getTypeName(field.Type)
		// Example:
		//         public String listen;
		builder.WriteString(F.ToString(fieldSpace, public, typeName, " ", tag, ";\n\n"))
	}

	return builder.String()
}

func getTypeName(valueType reflect.Type) string {
	switch valueType.Kind() {
	case reflect.Ptr:
		return getTypeName(valueType.Elem())
	case reflect.Bool:
		return "Boolean"
	case reflect.Int, reflect.Int32, reflect.Uint16, reflect.Uint32:
		return "Integer"
	case reflect.Int64, reflect.Uint64:
		return "Long"
	case reflect.String:
		return "String"
	case reflect.Slice:
		elem := valueType.Elem()
		if elem.Kind() == reflect.Uint8 {
			// Go json save []uint8 or []byte as base64 string
			return "String"
		}
		return "List<" + getTypeName(elem) + ">"
	case reflect.Map:
		return "Map<" + getTypeName(valueType.Key()) + ", " + getTypeName(valueType.Elem()) + ">"
	case reflect.Struct:
		return valueType.Name()
	case reflect.Uint8:
		if valueType.Name() == "DomainStrategy" {
			return "String"
		}
		return "Integer"
	default:
		log.Panic("[", valueType.Name(), "] is invalid: ", fmt.Sprint(valueType))
	}
	return ""
}

func addSuffix(origin, suffix string) string {
	if strings.HasSuffix(origin, suffix) {
		return origin
	}
	return origin + suffix
}
