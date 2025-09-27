package com.example.test2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asLiveData
import com.example.test2.databinding.ActivityMainBinding

class AddProductsActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val db = MainDb.getDb(this)
        db.tempProductDebugDao().getAllProducts1().asLiveData().observe(this){
            binding.tvList.text = ""
            it.forEach {
                val text = "Id: ${it.id} Name: ${it.ProductName} Calories: ${it.ProductCalories} Proteins: ${it.ProductProteins} Fats: ${it.ProductFats} Carbohydrates: ${it.ProductCarbohydrates}\n"
                binding.tvList.append(text)
            }
        }
        binding.button2.setOnClickListener {
            val product = Product(null,
                binding.edName?.text.toString(),
                binding.edKalories.text.toString().toInt(),
                binding.edProteins?.text.toString().toInt(),
                binding.edFats?.text.toString().toInt(),
                binding.edCarbohydrates?.text.toString().toInt())
            Thread {
                db.tempProductDebugDao().insertProduct(product)
            }.start()
        }
    }
}