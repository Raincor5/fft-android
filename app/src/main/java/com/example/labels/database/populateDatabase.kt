package com.example.labels.database

import com.example.labels.database.daos.ProductDao
import com.example.labels.database.entitties.Product

suspend fun populateDatabase(productDao: ProductDao) {
    productDao.insertProduct(Product(name = "korean red pepper"))
    productDao.insertProduct(Product(name = "soy sauce"))
    productDao.insertProduct(Product(name = "sesame oil"))
    productDao.insertProduct(Product(name = "garlic paste"))
    productDao.insertProduct(Product(name = "ginger chicken"))
    productDao.insertProduct(Product(name = "itame chicken"))
    productDao.insertProduct(Product(name = "fresh fruit & veg - cut"))
}
