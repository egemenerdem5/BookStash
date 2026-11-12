package com.egemenerdem.bookstash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.egemenerdem.bookstash.data.ApiResult
import com.egemenerdem.bookstash.data.BookEntity
import com.egemenerdem.bookstash.data.BookRepository
import com.egemenerdem.bookstash.data.NoteEntity
import com.egemenerdem.bookstash.model.BookVolumeItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val books: List<BookVolumeItem>) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class BookViewModel(private val repository: BookRepository) : ViewModel() {

    val savedBooks: StateFlow<List<BookEntity>> = repository.getAllBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    private val _selectedBookId = MutableStateFlow<String?>(null)
    val selectedBookId: StateFlow<String?> = _selectedBookId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentBookNotes: StateFlow<List<NoteEntity>> = _selectedBookId
        .flatMapLatest { bookId ->
            if (bookId == null) flowOf(emptyList())
            else repository.getNotesForBook(bookId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectBook(bookId: String) {
        _selectedBookId.value = bookId
    }

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
        val maxPage = if (book.pageCount > 0) book.pageCount else maxOf(1, newPage)
        val safePage = newPage.coerceIn(0, maxPage)
        viewModelScope.launch {
            repository.saveBook(book.copy(currentPage = safePage, status = newStatus))
        }
    }

    fun saveQuoteNote(bookId: String, quoteText: String, readerNote: String, tag: String, pageNumber: Int, imagePath: String? = null) {
        val formattedContent = buildString {
            append("\"${quoteText.trim()}\"")
            if (readerNote.isNotBlank()) {
                append("\nNOTE: ${readerNote.trim()}")
            }
            append("\n[p. $pageNumber • $tag]")
        }
        viewModelScope.launch {
            repository.saveNote(
                NoteEntity(
                    bookId = bookId,
                    contentText = formattedContent,
                    imagePath = imagePath
                )
            )
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun deleteBook(book: BookEntity) {
        viewModelScope.launch {
            repository.deleteBook(book)
        }
    }
}

class BookViewModelFactory(private val repository: BookRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BookViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BookViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}