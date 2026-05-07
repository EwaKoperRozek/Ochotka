package com.ochotka.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ochotka.app.common.search.SearchResultItem
import com.ochotka.app.common.utils.formatPrice
import com.ochotka.app.databinding.ItemMatchedDishBinding

class MatchedDishAdapter(
    private val onDishClick: (SearchResultItem) -> Unit
) : ListAdapter<SearchResultItem, MatchedDishAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMatchedDishBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemMatchedDishBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchResultItem) {
            binding.tvDishName.text = item.dish.name
            binding.tvDishMeta.text = listOf(
                item.dish.category,
                item.dish.ingredients.take(3).joinToString(", ")
            ).filter { it.isNotBlank() }.joinToString(" • ")
            binding.tvDishPrice.text = item.dish.formatPrice()
            binding.root.setOnClickListener { onDishClick(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SearchResultItem>() {
        override fun areItemsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem) =
            oldItem.dish.id == newItem.dish.id

        override fun areContentsTheSame(oldItem: SearchResultItem, newItem: SearchResultItem) =
            oldItem == newItem
    }
}
