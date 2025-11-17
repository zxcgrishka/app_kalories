package com.example.test2.ui.home.AddDish.CreateDish.AddProduct

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product (
    @PrimaryKey(autoGenerate = true) var id: Int? = null,
    @ColumnInfo(name = "name") var ProductName: String,
    @ColumnInfo(name = "calories") var ProductCalories: Int,
    @ColumnInfo(name = "proteins") var ProductProteins: Int,
    @ColumnInfo(name = "fats") var ProductFats: Int,
    @ColumnInfo(name = "carbohydrates") var ProductCarbohydrates: Int
)