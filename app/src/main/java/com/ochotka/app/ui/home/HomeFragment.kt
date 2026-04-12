package com.ochotka.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.chip.Chip
import com.ochotka.app.R
import com.ochotka.app.adapter.SearchResultAdapter
import com.ochotka.app.common.utils.gone
import com.ochotka.app.common.utils.visible
import com.ochotka.app.data.model.Restaurant
import com.ochotka.app.databinding.FragmentHomeBinding
import com.ochotka.app.ui.map.MapViewModel


class HomeFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private val mapViewModel: MapViewModel by viewModels()

    private lateinit var searchAdapter: SearchResultAdapter

    private var googleMap: GoogleMap? = null
    private val markerRestaurantMap = mutableMapOf<Marker, Restaurant>()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            enableMapLocation()
            mapViewModel.refreshLocation()
        }
    }


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
        observeHomeViewModel()
        observeMapViewModel()

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.homeMapContainer) as SupportMapFragment?
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction()
                    .replace(R.id.homeMapContainer, it)
                    .commit()
            }

        mapFragment.getMapAsync(this)
        binding.fabMyLocation.setOnClickListener {
            requestLocationOrMove()
        }
        binding.homeMapContainer.setOnTouchListener { _, _ ->
            binding.homeMapContainer.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }

    }

    private fun setupAdapters() {
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


    private fun observeHomeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HomeUiState.Idle -> showIdle()
                is HomeUiState.Success -> showSuccess(state)
                is HomeUiState.Error   -> showError(state.message)
            }
        }
    }

    private fun observeMapViewModel() {
        mapViewModel.uiState.observe(viewLifecycleOwner) { state ->
            if (state.isLoading) {
                binding.progressBarMap.visible()
            } else {
                binding.progressBarMap.gone()
                if (state.error != null) {
                    binding.tvMapError.visible()
                    binding.tvMapError.text = state.error
                } else {
                    binding.tvMapError.gone()
                    addRestaurantMarkers(state.restaurants)
                    state.userLocation?.let { loc ->
                        val userLatLng = LatLng(loc.latitude, loc.longitude)
                        googleMap?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(userLatLng, 14f)
                        )
                    }
                }
            }
        }
    }

    private fun showIdle() {
        binding.progressBar.gone()
        binding.tvError.gone()
        binding.layoutMapContent.gone()
        binding.layoutSearchContent.gone()
        binding.layoutMapContent.visible()
    }

    private fun showSuccess(state: HomeUiState.Success) {
        binding.progressBar.gone()
        binding.tvError.gone()

        if (state.searchResults != null) {
            // Tryb wyszukiwania
            binding.layoutMapContent.gone()
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
            binding.layoutMapContent.visible()
            binding.ivClearSearch.gone()
        }
    }

    private fun showError(message: String) {
        binding.progressBar.gone()
        binding.layoutMapContent.gone()
        binding.layoutSearchContent.gone()
        binding.tvError.visible()
        binding.tvError.text = message
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        val poznan = LatLng(52.4064, 16.9252)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(poznan, 13f))

        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isScrollGesturesEnabled = true
        map.uiSettings.isZoomGesturesEnabled = true

        map.setOnCameraMoveStartedListener {
            binding.homeMapContainer.parent?.requestDisallowInterceptTouchEvent(true)
        }

        map.setOnCameraIdleListener {
            binding.homeMapContainer.parent?.requestDisallowInterceptTouchEvent(false)
        }

        map.setOnMarkerClickListener { marker ->
            val restaurant = markerRestaurantMap[marker]
            if (restaurant != null) {
                showRestaurantInfoCard(restaurant)
            }
            true
        }

        map.setOnMapClickListener {
            binding.cardRestaurantInfo.gone()
        }

        enableMapLocation()

        if (
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mapViewModel.refreshLocation()
        }

        val state = mapViewModel.uiState.value
        if (state != null && !state.isLoading && state.restaurants.isNotEmpty()) {
            addRestaurantMarkers(state.restaurants)
        }
    }

    private fun addRestaurantMarkers(restaurants: List<Restaurant>) {
        val map = googleMap ?: return
        map.clear()
        markerRestaurantMap.clear()

        val boundsBuilder = LatLngBounds.Builder()

        restaurants.forEach { restaurant ->
            val position = LatLng(restaurant.lat, restaurant.lng)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(restaurant.name)
                    .snippet(restaurant.address)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            if (marker != null) {
                markerRestaurantMap[marker] = restaurant
            }
            boundsBuilder.include(position)
        }

        if (restaurants.size > 1) {
            try {
                val bounds = boundsBuilder.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            } catch (_: Exception) {
            }
        }
    }

    private fun showRestaurantInfoCard(restaurant: Restaurant) {
        binding.cardRestaurantInfo.visible()
        binding.tvRestaurantName.text = restaurant.name
        binding.tvRestaurantAddress.text = restaurant.address

        binding.btnRestaurantDetails.setOnClickListener {
            findNavController().navigate(
                R.id.action_home_to_restaurantDetail,
                bundleOf("restaurantId" to restaurant.id)
            )
        }
    }

    private fun enableMapLocation() {
        val map = googleMap ?: return
        val hasPermission =
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            try {
                map.isMyLocationEnabled = true
            } catch (_: SecurityException) {
            }
        }
    }

    private fun requestLocationOrMove() {
        val hasPermission =
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            mapViewModel.refreshLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
