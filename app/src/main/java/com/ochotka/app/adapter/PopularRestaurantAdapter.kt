package com.ochotka.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ochotka.app.R
import com.ochotka.app.common.utils.formatDistance
import com.ochotka.app.common.utils.isOpenNow
import com.ochotka.app.data.model.Restaurant
import com.ochotka.app.databinding.ItemRestaurantPopularBinding

class PopularRestaurantAdapter(
    private val onRestaurantClick: (Restaurant) -> Unit
) : ListAdapter<Restaurant, PopularRestaurantAdapter.ViewHolder>(DiffCallback()) {

    private var userLat: Double = 52.4064
    private var userLng: Double = 16.9252

    fun submitList(list: List<Restaurant>, lat: Double, lng: Double) {
        userLat = lat
        userLng = lng
        submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRestaurantPopularBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRestaurantPopularBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(restaurant: Restaurant) {
            binding.tvName.text = restaurant.name
            binding.tvAddress.text = restaurant.address
            binding.tvDistance.text = restaurant.formatDistance(userLat, userLng)

            val isOpen = restaurant.isOpenNow()
            binding.tvStatus.text = if (isOpen) "OTWARTE" else "ZAMKNIĘTE"
            binding.tvStatus.setTextColor(
                ContextCompat.getColor(
                    binding.root.context,
                    if (isOpen) R.color.green_open else R.color.red_closed
                )
            )

            binding.root.setOnClickListener { onRestaurantClick(restaurant) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Restaurant>() {
        override fun areItemsTheSame(oldItem: Restaurant, newItem: Restaurant) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Restaurant, newItem: Restaurant) =
            oldItem == newItem
    }
}
