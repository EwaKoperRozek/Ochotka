package com.ochotka.app.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
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
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.ochotka.app.R
import com.ochotka.app.adapter.MatchedDishAdapter
import com.ochotka.app.common.search.SearchResultItem
import com.ochotka.app.common.utils.LocationHelper
import com.ochotka.app.common.utils.gone
import com.ochotka.app.common.utils.visible
import com.ochotka.app.data.model.Restaurant
import com.ochotka.app.databinding.FragmentHomeBinding
import com.ochotka.app.ui.map.MapViewModel


class HomeFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by activityViewModels()
    private val mapViewModel: MapViewModel by viewModels()

    private lateinit var matchedDishAdapter: MatchedDishAdapter
    private val locationHelper by lazy { LocationHelper(requireContext().applicationContext) }

    private var googleMap: GoogleMap? = null
    private val markerRestaurantMap = mutableMapOf<Marker, RestaurantMarkerGroup>()
    private var currentMarkerGroups: List<RestaurantMarkerGroup> = emptyList()
    private var shouldCenterOnUser = true
    private var centerOnUserRequested = false
    private var awaitingLocationSettings = false
    private var suppressSearchCallback = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            if (!locationHelper.hasFinePermission()) {
                showEnablePreciseLocationMessage()
                return@registerForActivityResult
            }
            if (!locationHelper.isLocationEnabled()) {
                showEnableLocationMessage()
                return@registerForActivityResult
            }
            centerOnUserRequested = true
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
        bringOverlaysToFront()

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
        binding.btnCloseRestaurantCard.setOnClickListener {
            viewModel.clearSelectedRestaurant()
            binding.cardRestaurantInfo.gone()
        }
        binding.homeMapContainer.setOnTouchListener { _, _ ->
            binding.homeMapContainer.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }

    }

    private fun bringOverlaysToFront() {
        binding.layoutTopOverlay.bringToFront()
        binding.fabMyLocation.bringToFront()
        binding.cardRestaurantInfo.bringToFront()
        binding.progressBarMap.bringToFront()
        binding.tvMapError.bringToFront()

        ViewCompat.setTranslationZ(binding.layoutTopOverlay, 8f)
        ViewCompat.setTranslationZ(binding.cardRestaurantInfo, 10f)
        ViewCompat.setTranslationZ(binding.fabMyLocation, 12f)
        ViewCompat.setTranslationZ(binding.progressBarMap, 14f)
        ViewCompat.setTranslationZ(binding.tvMapError, 14f)
    }

    override fun onResume() {
        super.onResume()
        viewModel.restoreLastSuccessStateIfNeeded()
        viewModel.replayLastSuccessState()
        restoreCurrentUiState()
        if (awaitingLocationSettings && locationHelper.isLocationEnabled()) {
            awaitingLocationSettings = false
            centerOnUserRequested = true
            enableMapLocation()
            mapViewModel.refreshLocation()
        }
    }

    private fun restoreCurrentUiState() {
        when (val state = viewModel.uiState.value) {
            is HomeUiState.Idle -> showIdle()
            is HomeUiState.Loading -> showLoading(state)
            is HomeUiState.Success -> showSuccess(state)
            is HomeUiState.Error -> showError(state.message)
            null -> Unit
        }
    }

    private data class RestaurantMarkerGroup(
        val restaurant: Restaurant,
        val matchedDishes: List<SearchResultItem>
    )

    private fun setupAdapters() {
        matchedDishAdapter = MatchedDishAdapter { item ->
            findNavController().navigate(
                R.id.action_home_to_dishDetail,
                bundleOf("dishId" to item.dish.id, "restaurantId" to item.restaurant.id)
            )
        }
        binding.rvMatchedDishes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = true
            adapter = matchedDishAdapter
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
                        if (!binding.etSearch.text.isNullOrBlank()) {
                            suppressSearchCallback = true
                            binding.etSearch.text?.clear()
                            suppressSearchCallback = false
                        }
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
                if (suppressSearchCallback) return
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
                is HomeUiState.Loading -> showLoading(state)
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
                    renderMarkerGroups()
                    state.userLocation?.let { loc ->
                        val userLatLng = LatLng(loc.latitude, loc.longitude)
                        val hasActiveResults = viewModel.hasActiveSearchResults()
                        if (centerOnUserRequested || (!hasActiveResults && currentMarkerGroups.isEmpty() && shouldCenterOnUser)) {
                            googleMap?.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(userLatLng, 16f)
                            )
                            shouldCenterOnUser = false
                            centerOnUserRequested = false
                        }
                    }
                }
            }
        }
    }

    private fun showIdle() {
        binding.progressBar.gone()
        binding.tvError.gone()
        binding.layoutMapContent.visible()
        currentMarkerGroups = emptyList()
        renderMarkerGroups()
        binding.cardRestaurantInfo.gone()
        binding.tvMapError.gone()
    }

    private fun showLoading(state: HomeUiState.Loading) {
        binding.progressBar.visible()
        binding.tvError.gone()
        binding.layoutMapContent.visible()
        syncTopBar(state.selectedCategory, state.activeQuery)
    }

    private fun showSuccess(state: HomeUiState.Success) {
            binding.progressBar.gone()
            binding.tvError.gone()
            syncTopBar(state.selectedCategory, state.activeQuery)
            shouldCenterOnUser = false

            if (state.searchResults != null) {
                currentMarkerGroups = state.searchResults
                .groupBy { it.restaurant.id }
                .values
                .map { items ->
                    RestaurantMarkerGroup(
                        restaurant = items.first().restaurant,
                        matchedDishes = items.distinctBy { it.dish.id }
                    )
                }
                .sortedBy { it.restaurant.name }

            renderMarkerGroups()
            restoreSelectedRestaurantCard()
            binding.ivClearSearch.visible()
            binding.layoutMapContent.visible()
            if (state.searchResults.isEmpty()) {
                viewModel.clearSelectedRestaurant()
                binding.cardRestaurantInfo.gone()
                binding.tvMapError.gone()
                showNoResultsMessage()
            } else {
                binding.tvMapError.gone()
            }
        } else {
            binding.layoutMapContent.visible()
            binding.ivClearSearch.gone()
            binding.cardRestaurantInfo.gone()
            currentMarkerGroups = emptyList()
            renderMarkerGroups()
        }
    }

    private fun showError(message: String) {
        binding.progressBar.gone()
        binding.layoutMapContent.visible()
        currentMarkerGroups = emptyList()
        renderMarkerGroups()
        binding.tvError.visible()
        binding.tvError.text = message
    }

    private fun showNoResultsMessage() {
        Snackbar.make(
            binding.root,
            "Nie znaleziono dań. Spróbuj innego zapytania.",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        bringOverlaysToFront()

        val savedTarget = viewModel.getSavedCameraTarget()
        val savedZoom = viewModel.getSavedCameraZoom()
        if (savedTarget != null && savedZoom != null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(savedTarget, savedZoom))
            shouldCenterOnUser = false
        } else {
            val poznan = LatLng(52.4064, 16.9252)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(poznan, 13f))
        }

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
            val position = map.cameraPosition
            viewModel.saveCameraState(position.target, position.zoom)
            renderMarkerGroups()
        }

        map.setOnMarkerClickListener { marker ->
            val restaurant = markerRestaurantMap[marker]
            if (restaurant != null) {
                viewModel.selectRestaurant(restaurant.restaurant.id)
                showRestaurantInfoCard(restaurant)
            }
            true
        }

        map.setOnMapClickListener {
            viewModel.clearSelectedRestaurant()
            binding.cardRestaurantInfo.gone()
        }

        restoreCurrentUiState()
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
            renderMarkerGroups()
        }
    }

    private fun renderMarkerGroups() {
        addRestaurantMarkers(currentMarkerGroups)
    }

    private fun addRestaurantMarkers(groups: List<RestaurantMarkerGroup>) {
        val map = googleMap ?: return
        map.clear()
        markerRestaurantMap.clear()

        if (groups.isEmpty()) {
            binding.cardRestaurantInfo.gone()
            return
        }

        val visibleBounds = map.projection?.visibleRegion?.latLngBounds

        groups.forEach { group ->
            val restaurant = group.restaurant
            val position = LatLng(restaurant.lat, restaurant.lng)
            if (visibleBounds != null && !visibleBounds.contains(position)) {
                return@forEach
            }

            val leadDish = group.matchedDishes.firstOrNull()?.dish
            val marker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(leadDish?.name ?: restaurant.name)
                    .snippet(restaurant.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            if (marker != null) {
                markerRestaurantMap[marker] = group
            }
        }
    }

    private fun restoreSelectedRestaurantCard() {
        val selectedRestaurantId = viewModel.getSelectedRestaurantId()
        if (selectedRestaurantId == null) {
            binding.cardRestaurantInfo.gone()
            return
        }

        val selectedGroup = currentMarkerGroups.firstOrNull {
            it.restaurant.id == selectedRestaurantId
        }

        if (selectedGroup == null) {
            viewModel.clearSelectedRestaurant()
            binding.cardRestaurantInfo.gone()
            return
        }

        ensureSelectedRestaurantVisible(selectedGroup)
        showRestaurantInfoCard(selectedGroup)
    }

    private fun ensureSelectedRestaurantVisible(group: RestaurantMarkerGroup) {
        val map = googleMap ?: return
        val position = LatLng(group.restaurant.lat, group.restaurant.lng)
        val visibleBounds = map.projection?.visibleRegion?.latLngBounds

        if (visibleBounds == null || !visibleBounds.contains(position)) {
            val zoom = viewModel.getSavedCameraZoom() ?: 14f
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoom))
            renderMarkerGroups()
        }
    }

    private fun showRestaurantInfoCard(group: RestaurantMarkerGroup) {
        val restaurant = group.restaurant
        viewModel.selectRestaurant(restaurant.id)
        binding.cardRestaurantInfo.visible()
        binding.tvRestaurantName.text = restaurant.name
        binding.tvRestaurantAddress.text = restaurant.address
        binding.tvMatchedDishesLabel.text =
            "Pasujące dania (${group.matchedDishes.size})"
        matchedDishAdapter.submitList(group.matchedDishes.take(8))

        binding.btnRestaurantDetails.setOnClickListener {
            findNavController().navigate(
                R.id.action_home_to_restaurantDetail,
                bundleOf("restaurantId" to restaurant.id)
            )
        }
    }

    private fun syncTopBar(selectedCategory: String?, activeQuery: String) {
        suppressSearchCallback = true

        if (selectedCategory != null) {
            if (!binding.etSearch.text.isNullOrBlank()) {
                binding.etSearch.text?.clear()
            }
            checkCategoryChip(selectedCategory)
            binding.ivClearSearch.gone()
        } else {
            binding.chipGroupCategories.clearCheck()
            val query = activeQuery
            val currentText = binding.etSearch.text?.toString().orEmpty()
            if (query != currentText) {
                binding.etSearch.setText(query)
                binding.etSearch.setSelection(binding.etSearch.text?.length ?: 0)
            }
            if (query.isNotBlank()) {
                binding.ivClearSearch.visible()
            } else {
                binding.ivClearSearch.gone()
            }
        }

        suppressSearchCallback = false
    }

    private fun checkCategoryChip(category: String) {
        for (index in 0 until binding.chipGroupCategories.childCount) {
            val chip = binding.chipGroupCategories.getChildAt(index) as? Chip ?: continue
            chip.isChecked = chip.text.toString().equals(category, ignoreCase = true)
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
            if (!locationHelper.hasFinePermission()) {
                showEnablePreciseLocationMessage()
                return
            }
            if (!locationHelper.isLocationEnabled()) {
                showEnableLocationMessage()
                return
            }
            centerOnUserRequested = true
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

    private fun showEnableLocationMessage() {
        Snackbar.make(
            binding.root,
            "Włącz lokalizację w telefonie, aby pobrać Twoją pozycję.",
            Snackbar.LENGTH_LONG
        ).setAction("Ustawienia") {
            awaitingLocationSettings = true
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }.show()
    }

    private fun showEnablePreciseLocationMessage() {
        Snackbar.make(
            binding.root,
            "Włącz dokładną lokalizację dla Ochotki, aby pokazać Twoją pozycję.",
            Snackbar.LENGTH_LONG
        ).setAction("Ustawienia") {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
