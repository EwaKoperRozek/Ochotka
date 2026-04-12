package com.ochotka.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ochotka.app.common.search.SearchResultItem
import com.ochotka.app.common.utils.formatPrice
import com.ochotka.app.databinding.ItemSearchResultBinding

class SearchResultAdapter(
    private val onDishClick: (SearchResultItem) -> Unit,
    private val onRestaurantClick: (SearchResultItem) -> Unit
) : ListAdapter<SearchResultItem, SearchResultAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchResultItem) {
            binding.tvDishName.text = item.dish.name
            binding.tvDescription.text = item.dish.description
            binding.tvRestaurantName.text = item.restaurant.name
            binding.tvPrice.text = item.dish.formatPrice()
            binding.tvCategory.text = item.dish.category
            binding.tvIngredients.text = item.dish.ingredients.take(4).joinToString(", ")

            binding.root.setOnClickListener { onDishClick(item) }
            binding.tvRestaurantName.setOnClickListener { onRestaurantClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SearchResultItem>() {
        override fun areItemsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem) =
            oldItem.dish.id == newItem.dish.id

        override fun areContentsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem) =
            oldItem == newItem
    }
}
