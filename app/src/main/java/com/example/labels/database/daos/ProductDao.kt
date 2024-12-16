package com.example.labels.database.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.labels.database.entitties.Product

@Dao
interface ProductDao {

    // Insert a single product
    @Insert
    fun insertProduct(product: Product)

    // Retrieve all products
    @Query("SELECT * FROM products")
    fun getAllProducts(): List<Product>

    // Retrieve a product by name
    @Query("SELECT * FROM products WHERE name LIKE :productName LIMIT 1")
    fun getProductByName(productName: String): Product?
}