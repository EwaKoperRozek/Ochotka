package com.ochotka.app.ui.home

import com.ochotka.app.common.search.SearchResultItem

sealed class HomeUiState {
    data object Idle : HomeUiState()

    data class Success(
        val searchResults: List<SearchResultItem>?,
        val selectedCategory: String?
    ) : HomeUiState()

    data class Error(val message: String) : HomeUiState()
}
