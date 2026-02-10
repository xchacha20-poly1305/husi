package fr.husi.database

import androidx.room.TypeConverter
import fr.husi.ktx.kxs
import kotlinx.serialization.encodeToString

class StringCollectionConverter {
    companion object {
        const val SPLIT_FLAG = ","

        /*
        @TypeConverter
        @JvmStatic
        fun fromList(list: List<String>): String = if (list.isEmpty()) {
            ""
        } else {
            list.joinToString(SPLIT_FLAG)
        }

        @TypeConverter
        @JvmStatic
        fun toList(str: String): List<String> = if (str.isBlank()) {
            emptyList()
        } else {
            str.split(SPLIT_FLAG)
        }
        */


        @TypeConverter
        @JvmStatic
        fun fromSet(set: Set<String>): String = if (set.isEmpty()) {
            ""
        } else {
            set.joinToString(SPLIT_FLAG)
        }

        @TypeConverter
        @JvmStatic
        fun toSet(str: String): Set<String> = if (str.isBlank()) {
            emptySet()
        } else {
            str.split(",").toSet()
        }

        @TypeConverter
        @JvmStatic
        fun fromLinkedHashMap(map: LinkedHashMap<String, String>): String = if (map.isEmpty()) {
            ""
        } else {
            kxs.encodeToString(map)
        }

        @TypeConverter
        @JvmStatic
        fun toLinkedHashMap(str: String): LinkedHashMap<String, String> = if (str.isBlank()) {
            LinkedHashMap()
        } else {
            kxs.decodeFromString<LinkedHashMap<String, String>>(str)
        }
    }
}

