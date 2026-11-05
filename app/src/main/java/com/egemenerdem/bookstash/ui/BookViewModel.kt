package com.egemenerdem.bookstash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.egemenerdem.bookstash.data.ApiResult
import com.egemenerdem.bookstash.data.BookEntity
import com.egemenerdem.bookstash.data.BookRepository
import com.egemenerdem.bookstash.model.BookVolumeItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Represents the UI state for the Google Books API search feature.
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val books: List<BookVolumeItem>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class BookViewModel(private val repository: BookRepository) : ViewModel() {

    // Reactive stream of all books saved in the local Room database
    val savedBooks: StateFlow<List<BookEntity>> = repository.getAllBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Reactive stream for the remote Google Books API search state
    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    fun searchBooks(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            _searchUiState.value = SearchUiState.Error("Please enter a book title or author.")
            return
        }

        viewModelScope.launch {
            _searchUiState.value = SearchUiState.Loading
            when (val result = repository.searchBooksRemote(trimmedQuery)) {
                is ApiResult.Success -> {
                    _searchUiState.value = SearchUiState.Success(result.data)
                }
                is ApiResult.Error -> {
                    _searchUiState.value = SearchUiState.Error(result.message)
                }
            }
        }
    }

    /**
     * Converts a searched Google Books API item into a local BookEntity and saves it to Room.
     */
    fun saveVolumeToLibrary(item: BookVolumeItem, status: String = "WANT_TO_READ") {
        val info = item.volumeInfo
        val bookEntity = BookEntity(
            id = item.id,
            title = info?.title ?: "Unknown Title",
            authors = info?.authors?.joinToString(", ") ?: "Unknown Author",
            description = info?.description,
            coverUrl = info?.imageLinks?.httpsThumbnail,
            pageCount = info?.pageCount ?: 0,
            currentPage = 0,
            status = status
        )
        viewModelScope.launch {
            repository.saveBook(bookEntity)
        }
    }

    fun updateBookProgress(book: BookEntity, newPage: Int, newStatus: String) {
        val safePage = newPage.coerceIn(0, maxOf(book.pageCount, newPage))
        viewModelScope.launch {
            repository.saveBook(book.copy(currentPage = safePage, status = newStatus))
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            repository.deleteBook(book)
        }
    }
}

/**
 * Factory required to instantiate BookViewModel with a constructor parameter (BookRepository).
 */
class BookViewModelFactory(private val repository: BookRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BookViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BookViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}