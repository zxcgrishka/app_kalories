package com.example.test2

fun make_product_by_person(): MutableMap<String, Int> {
    val map = mutableMapOf<String, Int>()
    println("Enter the number of couples key-value: ")
    val size = readLine()?.toIntOrNull()
    if (size == null || size <= 0) {
        println("Error")
        return map
    }
    for (i in 1..size) {
        println("Enter the couple №$i (key and number to space): ")
        val input = readLine()?.split(" ", limit = 2)
        if (input != null && input.size == 2 && input[0].isNotBlank() && input[1].toIntOrNull() != null) {
            val value = input[1].toIntOrNull()!!
            map[input[0]] = value
            println("Added pair: key = ${input[0]}, value = $value")
        }
        else {
            println("Error: Invalid input (key must be a non-empty string, value must be a number), skipping.")
        }
    }
    return map
}

val dictBl: MutableMap<String, Double> = mutableMapOf()
fun create_dish(products: MutableMap<String, Int>, sumCalories: Double = 0.0,
                productsUsed: MutableList<Pair<String, Int>> = mutableListOf()) {
    println("Available products (calories per 100g): ")
    products.keys.forEachIndexed { index, product ->
        println("${index + 1}. $product - ${products[product]} kcal/100g") }
    println("\nEnter product number: ")
    val productIndex = readLine()?.toIntOrNull()
    println("Enter product weight (in grams): ")
    val productWeight = readLine()?.toIntOrNull()

    if (productIndex == null || productWeight == null) {
        println("Error: Please enter valid numbers.")
        create_dish(products, sumCalories, productsUsed)
        return
    }

    val keysList = products.keys.toList() //toList?

    if (productIndex in 1..keysList.size) {
        val productName = keysList[productIndex - 1]
        val caloriesPer100g = products[productName]!!
        val productCalories = (productWeight / 100.0) * caloriesPer100g

        println("$productName: ${productCalories.toInt()} kcal")

        productsUsed.add(Pair(productName, productWeight))

        println("\nAdd more products to the dish? (Yes/No): ")
        val continueAdding = readLine()?.trim()?.lowercase()
        if (continueAdding == "yes") {
            create_dish(products, sumCalories + productCalories, productsUsed)
        } else {
            println("\nName your dish: ")
            val dishName = readLine()?.trim()
            if (dishName.isNullOrBlank()) {
                println("Error: Dish name cannot be empty.")
                create_dish(products, sumCalories + productCalories, productsUsed)
                return
            }
            val totalCalories = sumCalories + productCalories
            dictBl[dishName] = totalCalories
            println("Dish '$dishName' saved! Total calories: ${totalCalories.toInt()} kcal")
        }
    }
    else {
        println("Invalid product number!")
        create_dish(products, sumCalories, productsUsed)
    }
}
