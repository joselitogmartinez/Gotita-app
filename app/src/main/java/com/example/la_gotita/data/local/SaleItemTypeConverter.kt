package com.example.la_gotita.data.local

import androidx.room.TypeConverter
import com.example.la_gotita.data.model.SaleItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SaleItemTypeConverter {

    private val gson = Gson()

    @TypeConverter
    fun fromSaleItemList(saleItems: List<SaleItem>?): String? {
        if (saleItems == null) {
            return null
        }
        val type = object : TypeToken<List<SaleItem>>() {}.type
        return gson.toJson(saleItems, type)
    }

    @TypeConverter
    fun toSaleItemList(saleItemsString: String?): List<SaleItem>? {
        if (saleItemsString == null) {
            return null
        }
        val type = object : TypeToken<List<SaleItem>>() {}.type
        return gson.fromJson(saleItemsString, type)
    }
}