package io.nekohasekai.sagernet.database

import androidx.room.TypeConverter

class ListConverter {
    companion object {
        @TypeConverter
        @JvmStatic
        fun fromList(list: List<String>): String {
            return list.joinToString(",")
        }

        @TypeConverter
        @JvmStatic
        fun toList(string: String): List<String> {
            return string.split(",")
        }
    }
}

