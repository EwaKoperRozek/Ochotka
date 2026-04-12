package com.ochotka.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ochotka.app.R
import com.ochotka.app.adapter.PopularRestaurantAdapter
import com.ochotka.app.common.utils.LocationHelper
import com.ochotka.app.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var favoritesAdapter: PopularRestaurantAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProfile()
    }

    private fun setupRecyclerView() {
        favoritesAdapter = PopularRestaurantAdapter { restaurant ->
            findNavController().navigate(
                R.id.restaurantDetailFragment,
                Bundle().apply { putString("restaurantId", restaurant.id) }
            )
        }

        binding.rvFavoriteRestaurants.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = favoritesAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvFavCount.text = state.favoriteCount.toString()
                    binding.tvRestaurantCount.text = "Restauracji w bazie: ${state.restaurantCount}"
                    binding.tvDishCount.text = "Dań w bazie: ${state.dishCount}"
                    binding.tvAppVersion.text = state.appVersion

                    if (state.favoriteRestaurants.isEmpty()) {
                        binding.rvFavoriteRestaurants.visibility = View.GONE
                        binding.tvNoFavorites.visibility = View.VISIBLE
                    } else {
                        binding.rvFavoriteRestaurants.visibility = View.VISIBLE
                        binding.tvNoFavorites.visibility = View.GONE
                        favoritesAdapter.submitList(
                            state.favoriteRestaurants,
                            LocationHelper.POZNAN_LAT,
                            LocationHelper.POZNAN_LNG
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}