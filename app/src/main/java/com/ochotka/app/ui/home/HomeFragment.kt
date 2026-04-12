package com.ochotka.app.ui.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.ochotka.app.R
import com.ochotka.app.adapter.FeaturedDishAdapter
import com.ochotka.app.adapter.PopularRestaurantAdapter
import com.ochotka.app.adapter.SearchResultAdapter
import com.ochotka.app.common.utils.gone
import com.ochotka.app.common.utils.visible
import com.ochotka.app.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var featuredAdapter: FeaturedDishAdapter
    private lateinit var popularAdapter: PopularRestaurantAdapter
    private lateinit var searchAdapter: SearchResultAdapter

    // Kategorie wyświetlane jako chipy
    private val categories = listOf("Pizza", "Sushi", "Ramen", "Burgery", "Makaron", "Zupy")


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupCategoryChips()
        setupSearchBar()
        observeViewModel()
    }

    private fun setupAdapters() {
        featuredAdapter = FeaturedDishAdapter { dish, restaurantId ->
            findNavController().navigate(
                R.id.action_home_to_dishDetail,
                bundleOf("dishId" to dish.id, "restaurantId" to restaurantId)
            )
        }
        binding.rvFeatured.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = featuredAdapter
        }

        popularAdapter = PopularRestaurantAdapter(
            onRestaurantClick = { restaurant ->
                findNavController().navigate(
                    R.id.action_home_to_restaurantDetail,
                    bundleOf("restaurantId" to restaurant.id)
                )
            }
        )
        binding.rvPopular.apply {
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
            adapter = popularAdapter
        }

        searchAdapter = SearchResultAdapter(
            onDishClick = { item ->
                findNavController().navigate(
                    R.id.action_home_to_dishDetail,
                    bundleOf("dishId" to item.dish.id, "restaurantId" to item.restaurant.id)
                )
            },
            onRestaurantClick = { item ->
                findNavController().navigate(
                    R.id.action_home_to_restaurantDetail,
                    bundleOf("restaurantId" to item.restaurant.id)
                )
            }
        )
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
            adapter = searchAdapter
        }
    }

    private fun setupCategoryChips() {
        binding.chipGroupCategories.removeAllViews()
        categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                chipBackgroundColor = resources.getColorStateList(
                    R.color.chip_background_selector, requireActivity().theme
                )
                setTextColor(resources.getColorStateList(
                    R.color.chip_text_selector, requireActivity().theme
                ))
                chipStrokeWidth = 1.5f
                chipStrokeColor = resources.getColorStateList(
                    R.color.chip_stroke_selector, requireActivity().theme
                )
                setOnClickListener {
                    if (isChecked) {
                        viewModel.filterByCategory(category)
                    } else {
                        binding.etSearch.text?.clear()
                        viewModel.clearSearch()
                    }
                }
            }
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun setupSearchBar() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                viewModel.search(query)

                if (query.isNotEmpty()) {
                    binding.chipGroupCategories.clearCheck()
                }
            }
        })

        binding.ivClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            binding.chipGroupCategories.clearCheck()
            viewModel.clearSearch()
        }
    }


    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HomeUiState.Loading -> showLoading()
                is HomeUiState.Success -> showSuccess(state)
                is HomeUiState.Error   -> showError(state.message)
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visible()
        binding.layoutNormalContent.gone()
        binding.layoutSearchContent.gone()
        binding.tvError.gone()
    }

    private fun showSuccess(state: HomeUiState.Success) {
        binding.progressBar.gone()
        binding.tvError.gone()

        if (state.searchResults != null) {
            // Tryb wyszukiwania
            binding.layoutNormalContent.gone()
            binding.layoutSearchContent.visible()
            binding.ivClearSearch.visible()

            if (state.searchResults.isEmpty()) {
                binding.tvSearchEmpty.visible()
                binding.rvSearchResults.gone()
            } else {
                binding.tvSearchEmpty.gone()
                binding.rvSearchResults.visible()
                searchAdapter.submitList(state.searchResults)
            }
        } else {
            // Tryb główny
            binding.layoutSearchContent.gone()
            binding.layoutNormalContent.visible()
            binding.ivClearSearch.gone()

            featuredAdapter.submitList(state.featuredDishes)
            popularAdapter.submitList(
                state.popularRestaurants,
                state.userLat,
                state.userLng
            )
        }
    }

    private fun showError(message: String) {
        binding.progressBar.gone()
        binding.layoutNormalContent.gone()
        binding.layoutSearchContent.gone()
        binding.tvError.visible()
        binding.tvError.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
