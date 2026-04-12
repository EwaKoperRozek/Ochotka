package com.ochotka.app.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ochotka.app.R
import com.ochotka.app.adapter.PopularRestaurantAdapter
import com.ochotka.app.common.utils.LocationHelper
import com.ochotka.app.common.utils.gone
import com.ochotka.app.common.utils.visible
import com.ochotka.app.databinding.FragmentFavoritesBinding

class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FavoritesViewModel by viewModels()
    private lateinit var adapter: PopularRestaurantAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = PopularRestaurantAdapter { restaurant ->
            findNavController().navigate(
                R.id.action_favorites_to_restaurantDetail,
                bundleOf("restaurantId" to restaurant.id)
            )
        }

        binding.rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFavorites.adapter = adapter

        viewModel.favorites.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) {
                binding.tvEmpty.visible()
                binding.rvFavorites.gone()
            } else {
                binding.tvEmpty.gone()
                binding.rvFavorites.visible()
                adapter.submitList(list, LocationHelper.POZNAN_LAT, LocationHelper.POZNAN_LNG)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadFavorites()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}