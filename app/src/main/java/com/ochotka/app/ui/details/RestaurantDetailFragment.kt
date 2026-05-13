package com.ochotka.app.ui.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ochotka.app.R
import com.ochotka.app.adapter.DishMenuAdapter
import com.ochotka.app.common.utils.gone
import com.ochotka.app.common.utils.visible
import com.ochotka.app.databinding.FragmentRestaurantDetailBinding

class RestaurantDetailFragment : Fragment() {

    private var _binding: FragmentRestaurantDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DetailViewModel by viewModels()
    private lateinit var dishAdapter: DishMenuAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRestaurantDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val restaurantId = arguments?.getString("restaurantId") ?: return

        dishAdapter = DishMenuAdapter { dish ->
            findNavController().navigate(
                R.id.action_restaurantDetail_to_dishDetail,
                bundleOf(
                    "dishId" to dish.id,
                    "restaurantId" to restaurantId
                )
            )
        }

        binding.rvDishes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDishes.adapter = dishAdapter

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack(com.ochotka.app.R.id.homeFragment, false)
        }

        binding.btnFavorite.setOnClickListener {
            viewModel.toggleFavorite(restaurantId)
        }

        viewModel.loadRestaurant(restaurantId)

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DetailUiState.Loading -> {
                    binding.progressBar.visible()
                    binding.layoutContent.gone()
                }

                is DetailUiState.RestaurantLoaded -> {
                    binding.progressBar.gone()
                    binding.layoutContent.visible()

                    binding.tvName.text = state.restaurant.name
                    binding.tvAddress.text = "${state.restaurant.address}, ${state.restaurant.city}"
                    binding.tvPhone.text = "Tel. alergenowy: ${state.restaurant.allergenPhone}"
                    binding.tvDescription.text =
                        state.restaurant.description.ifBlank { "Brak opisu." }

                    val isOpen = isRestaurantOpenNow(state.restaurant.openingHours)
                    binding.tvStatus.text = if (isOpen) "OTWARTE" else "ZAMKNIĘTE"
                    binding.tvStatus.background = ContextCompat.getDrawable(
                        requireContext(),
                        if (isOpen) R.drawable.bg_badge_open else R.drawable.bg_badge_spicy
                    )

                    binding.btnFavorite.text =
                        if (state.isFavorite) "Usuń z ulubionych" else "Dodaj do ulubionych"

                    dishAdapter.submitList(state.dishes)
                }

                is DetailUiState.Error -> {
                    binding.progressBar.gone()
                    binding.layoutContent.visible()
                    binding.tvName.text = state.message
                }

                else -> {}
            }
        }
    }

    private fun isRestaurantOpenNow(openingHours: Map<String, List<String>>): Boolean {
        return openingHours.isNotEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}