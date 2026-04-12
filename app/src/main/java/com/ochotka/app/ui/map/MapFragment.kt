package com.ochotka.app.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.ochotka.app.R
import com.ochotka.app.common.utils.gone
import com.ochotka.app.common.utils.visible
import com.ochotka.app.data.model.Restaurant
import com.ochotka.app.databinding.FragmentMapBinding

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapViewModel by viewModels()

    private var googleMap: GoogleMap? = null
    private val markerRestaurantMap = mutableMapOf<Marker, Restaurant>()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            enableMapLocation()
            viewModel.refreshLocation()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapContainer) as SupportMapFragment?
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction()
                    .replace(R.id.mapContainer, it)
                    .commit()
            }
        mapFragment.getMapAsync(this)

        binding.fabMyLocation.setOnClickListener {
            requestLocationOrMove()
        }

        observeViewModel()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Centrum Poznania jako start
        val poznan = LatLng(52.4064, 16.9252)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(poznan, 13f))

        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false // własny FAB

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
            viewModel.refreshLocation()
        }

        // Jeśli dane już załadowane, narysuj markery
        val state = viewModel.uiState.value
        if (state != null && !state.isLoading && state.restaurants.isNotEmpty()) {
            addRestaurantMarkers(state.restaurants)
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
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
                // bounds mogą być puste przy starcie
            }
        }
    }

    private fun showRestaurantInfoCard(restaurant: Restaurant) {
        binding.cardRestaurantInfo.visible()
        binding.tvRestaurantName.text = restaurant.name
        binding.tvRestaurantAddress.text = restaurant.address

        binding.btnRestaurantDetails.setOnClickListener {
            findNavController().navigate(
                R.id.action_map_to_restaurantDetail,
                bundleOf("restaurantId" to restaurant.id)
            )
        }
    }

    private fun enableMapLocation() {
        val map = googleMap ?: return
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            try {
                map.isMyLocationEnabled = true
            } catch (_: SecurityException) {}
        }
    }

    private fun requestLocationOrMove() {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.refreshLocation()
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
