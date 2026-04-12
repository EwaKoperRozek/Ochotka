package com.ochotka.app.ui.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.ochotka.app.adapter.VariantAdapter
import com.ochotka.app.common.utils.gone
import com.ochotka.app.common.utils.visible
import com.ochotka.app.databinding.FragmentDishDetailBinding

class DishDetailFragment : Fragment() {

    private var _binding: FragmentDishDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDishDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dishId = arguments?.getString("dishId") ?: return
        val restaurantId = arguments?.getString("restaurantId") ?: return

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        viewModel.loadDish(dishId, restaurantId)

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DetailUiState.Loading -> {
                    binding.progressBar.visible()
                    binding.layoutContent.gone()
                }

                is DetailUiState.DishLoaded -> {
                    binding.progressBar.gone()
                    binding.layoutContent.visible()

                    val dish = state.dish
                    binding.tvDishName.text = dish.name
                    binding.tvCategory.text = dish.category
                    binding.tvDescription.text = dish.description
                    binding.tvRestaurantName.text = "@ ${state.restaurant.name}"
                    binding.tvIngredients.text = dish.ingredients.joinToString(" • ")

                    val variantAdapter = VariantAdapter()
                    binding.rvVariants.layoutManager = LinearLayoutManager(requireContext())
                    binding.rvVariants.adapter = variantAdapter
                    variantAdapter.submitList(dish.variants)
                }

                is DetailUiState.Error -> {
                    binding.progressBar.gone()
                    binding.layoutContent.visible()
                    binding.tvDishName.text = state.message
                }

                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}