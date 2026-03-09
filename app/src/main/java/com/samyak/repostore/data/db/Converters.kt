package com.samyak.repostore.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.samyak.repostore.data.model.GitHubRepo

class Converters {
    
    private val gson = Gson()
    
    @TypeConverter
    fun fromOwner(owner: GitHubRepo.Owner): String {
        return gson.toJson(owner)
    }
    
    @TypeConverter
    fun toOwner(json: String): GitHubRepo.Owner {
        return gson.fromJson(json, GitHubRepo.Owner::class.java)
    }
    
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toStringList(json: String?): List<String>? {
        return json?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(it, type)
        }
    }
}
