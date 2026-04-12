package com.ochotka.app.ui.profile

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import java.io.File
import java.io.FileOutputStream

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()
    private lateinit var favoritesAdapter: PopularRestaurantAdapter
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            saveProfileImage(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupProfileActions()
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

    private fun setupProfileActions() {
        binding.btnProfileSettings.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            popup.menu.add(0, 1, 0, "Zmień zdjęcie")
            popup.menu.add(0, 2, 1, "Zmień imię")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        imagePickerLauncher.launch("image/*")
                        true
                    }
                    2 -> {
                        showEditNameDialog()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvUserName.text = state.userName.ifBlank { "Mój profil" }
                    loadProfileImage(state.profileImagePath)
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

    private fun showEditNameDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Wpisz swoje imię"
            setText(viewModel.uiState.value.userName)
            setSelection(text.length)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Zmień imię")
            .setView(input)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                viewModel.saveUserName(name)
                Toast.makeText(requireContext(), "Zapisano imię", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun saveProfileImage(uri: Uri) {
        runCatching {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
                ?: error("Nie udało się otworzyć zdjęcia")
            val profileDir = File(requireContext().filesDir, "profile").apply { mkdirs() }
            val imageFile = File(profileDir, "profile_photo.jpg")
            inputStream.use { input ->
                FileOutputStream(imageFile).use { output ->
                    input.copyTo(output)
                }
            }
            viewModel.saveProfileImagePath(imageFile.absolutePath)
            loadProfileImage(imageFile.absolutePath)
        }.onFailure {
            Toast.makeText(requireContext(), "Nie udało się zapisać zdjęcia", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun loadProfileImage(path: String?) {
        if (path.isNullOrBlank()) {
            binding.ivProfilePhoto.setImageDrawable(null)
            return
        }

        val file = File(path)
        if (!file.exists()) {
            binding.ivProfilePhoto.setImageDrawable(null)
            return
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        binding.ivProfilePhoto.setImageBitmap(bitmap)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
