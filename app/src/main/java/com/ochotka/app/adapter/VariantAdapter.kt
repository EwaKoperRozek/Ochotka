package com.ochotka.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ochotka.app.data.model.Variant
import com.ochotka.app.databinding.ItemVariantBinding

class VariantAdapter : ListAdapter<Variant, VariantAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVariantBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemVariantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(variant: Variant) {
            binding.tvSize.text = variant.size.ifBlank { "Cena" }
            binding.tvPrice.text = "%.0f zł".format(variant.price)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Variant>() {
        override fun areItemsTheSame(old: Variant, new: Variant) =
            old.size == new.size && old.price == new.price
        override fun areContentsTheSame(old: Variant, new: Variant) = old == new
    }
}
