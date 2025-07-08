package io.nekohasekai.sagernet.fmt.gson;

import static io.nekohasekai.sagernet.ktx.MapsKt.getGson;

import androidx.room.TypeConverter;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import kotlin.collections.CollectionsKt;
import kotlin.collections.SetsKt;

public class GsonConverters {

    @TypeConverter
    public static String toJson(Object value) {
        if (value instanceof Collection) {
            if (((Collection<?>) value).isEmpty()) return "";
        }
        return getGson().toJson(value);
    }

    @TypeConverter
    public static List toList(String value) {
        if (value == null || value.isBlank()) return CollectionsKt.listOf();
        return getGson().fromJson(value, List.class);
    }

    @TypeConverter
    public static Set toSet(String value) {
        if (value == null || value.isBlank()) return SetsKt.setOf();
        return getGson().fromJson(value, Set.class);
    }

}
