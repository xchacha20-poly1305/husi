package io.nekohasekai.sagernet.database

import androidx.room.TypeConverter

class ListConverter {
    companion object {
        @TypeConverter
        @JvmStatic
        fun fromList(list: List<String>): String {
            return if (list.isEmpty()) {
                ""
            } else {
                list.joinToString(",")
            }
        }

        @TypeConverter
        @JvmStatic
        fun toList(str: String): List<String> {
            return if (str.isBlank()) {
                listOf()
            } else {
                str.split(",")
            }
        }
    }
}

