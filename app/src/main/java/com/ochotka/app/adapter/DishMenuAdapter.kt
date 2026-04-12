package com.ochotka.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ochotka.app.common.utils.formatPrice
import com.ochotka.app.data.model.Dish
import com.ochotka.app.databinding.ItemDishMenuBinding

class DishMenuAdapter(
    private val onDishClick: (Dish) -> Unit
) : ListAdapter<Dish, DishMenuAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDishMenuBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemDishMenuBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(dish: Dish) {
            binding.tvDishName.text = dish.name
            binding.tvDishDescription.text = dish.description
            binding.tvDishPrice.text = dish.formatPrice()
            binding.tvDishCategory.text = dish.category
            binding.tvDishIngredients.text = dish.ingredients.take(4).joinToString(", ")
            binding.root.setOnClickListener { onDishClick(dish) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Dish>() {
        override fun areItemsTheSame(old: Dish, new: Dish) = old.id == new.id
        override fun areContentsTheSame(old: Dish, new: Dish) = old == new
    }
}