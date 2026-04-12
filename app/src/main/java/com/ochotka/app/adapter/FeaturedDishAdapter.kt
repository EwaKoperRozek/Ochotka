package com.ochotka.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ochotka.app.common.utils.formatPrice
import com.ochotka.app.data.model.Dish
import com.ochotka.app.data.model.Restaurant
import com.ochotka.app.databinding.ItemFeaturedDishBinding

class FeaturedDishAdapter(
    private val onDishClick: (Dish, String) -> Unit
) : ListAdapter<Pair<Dish, Restaurant>, FeaturedDishAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFeaturedDishBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemFeaturedDishBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Pair<Dish, Restaurant>) {
            val (dish, restaurant) = item
            binding.tvDishName.text = dish.name
            binding.tvRestaurantName.text = restaurant.name
            binding.tvPrice.text = dish.formatPrice()
            binding.tvCategory.text = dish.category

            binding.root.setOnClickListener {
                onDishClick(dish, restaurant.id)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Pair<Dish, Restaurant>>() {
        override fun areItemsTheSame(
            oldItem: Pair<Dish, Restaurant>, newItem: Pair<Dish, Restaurant>
        ) = oldItem.first.id == newItem.first.id

        override fun areContentsTheSame(
            oldItem: Pair<Dish, Restaurant>, newItem: Pair<Dish, Restaurant>
        ) = oldItem == newItem
    }
}
