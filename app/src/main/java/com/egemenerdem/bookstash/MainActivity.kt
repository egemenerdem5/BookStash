package com.egemenerdem.bookstash

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.egemenerdem.bookstash.data.BookEntity
import com.egemenerdem.bookstash.data.BookRepository
import com.egemenerdem.bookstash.data.BookStashDatabase
import com.egemenerdem.bookstash.data.GoogleBooksApiService
import com.egemenerdem.bookstash.databinding.ActivityMainBinding
import com.egemenerdem.bookstash.ui.BookAdapter
import com.egemenerdem.bookstash.ui.BookViewModel
import com.egemenerdem.bookstash.ui.BookViewModelFactory
import com.egemenerdem.bookstash.ui.SearchUiState
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val bookAdapter = BookAdapter()
    private var isShowingLibrary = true

    private val viewModel: BookViewModel by viewModels {
        val db = BookStashDatabase.getDatabase(applicationContext)
        val repository = BookRepository(
            bookDao = db.bookDao(),
            noteDao = db.noteDao(),
            apiService = GoogleBooksApiService.instance
        )
        BookViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        // 2-Column Index Card Grid matching Ekran Alıntısı6
        binding.rvBooks.layoutManager = GridLayoutManager(this, 2)
        binding.rvBooks.adapter = bookAdapter
    }

    private fun setupListeners() {
        binding.btnSearch.setOnClickListener {
            triggerSearch()
        }

        binding.etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                triggerSearch()
                true
            } else {
                false
            }
        }

        binding.btnShowLibrary.setOnClickListener {
            isShowingLibrary = true
            updateTabStyles()
            renderLibraryState(viewModel.savedBooks.value)
        }

        binding.btnShowSearch.setOnClickListener {
            isShowingLibrary = false
            updateTabStyles()
            renderSearchState(viewModel.searchUiState.value)
        }

        binding.tvBottomBarJump.setOnClickListener {
            isShowingLibrary = !isShowingLibrary
            updateTabStyles()
            if (isShowingLibrary) {
                renderLibraryState(viewModel.savedBooks.value)
            } else {
                renderSearchState(viewModel.searchUiState.value)
            }
        }
    }

    private fun triggerSearch() {
        val query = binding.etSearchQuery.text.toString()
        isShowingLibrary = false
        updateTabStyles()
        viewModel.searchBooks(query)
    }

    private fun updateTabStyles() {
        if (isShowingLibrary) {
            binding.tvHeader.text = "READING NOW"
            binding.tvBottomBarStatus.text = "Shelf • Reading now"
            binding.btnShowLibrary.setBackgroundColor(Color.parseColor("#141413"))
            binding.btnShowLibrary.setTextColor(Color.parseColor("#F5F2EB"))
            binding.btnShowSearch.setBackgroundColor(Color.parseColor("#D5CFC2"))
            binding.btnShowSearch.setTextColor(Color.parseColor("#141413"))
        } else {
            binding.tvHeader.text = "CATALOG SEARCH"
            binding.tvBottomBarStatus.text = "Catalog • Google Books API"
            binding.btnShowSearch.setBackgroundColor(Color.parseColor("#141413"))
            binding.btnShowSearch.setTextColor(Color.parseColor("#F5F2EB"))
            binding.btnShowLibrary.setBackgroundColor(Color.parseColor("#D5CFC2"))
            binding.btnShowLibrary.setTextColor(Color.parseColor("#141413"))
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.savedBooks.collect { books ->
                        updateLedgerStats(books)
                        if (isShowingLibrary) {
                            renderLibraryState(books)
                        }
                    }
                }
                launch {
                    viewModel.searchUiState.collect { state ->
                        if (!isShowingLibrary) {
                            renderSearchState(state)
                        }
                    }
                }
            }
        }
    }

    private fun updateLedgerStats(books: List<BookEntity>) {
        binding.btnShowLibrary.text = "INDEX SHELF (${books.size})"

        val activeBooks = books.filter { it.status == "READING" }
        val finishedBooks = books.filter { it.status == "FINISHED" }
        binding.tvActiveSummary.text = "${activeBooks.size} active\n${finishedBooks.size} finished"

        // Current active book page stat (e.g., 214 / 401)
        val primaryBook = activeBooks.firstOrNull() ?: books.firstOrNull()
        if (primaryBook != null) {
            binding.tvStatPages.text = "${primaryBook.currentPage} / ${primaryBook.pageCount}"
        } else {
            binding.tvStatPages.text = "0 / 0"
        }

        binding.tvStatStreak.text = "${books.size} FILED"
        binding.tvStatChallenge.text = "${finishedBooks.size} / 12"
    }

    private fun renderLibraryState(books: List<BookEntity>) {
        binding.progressBar.visibility = View.GONE
        if (books.isEmpty()) {
            binding.rvBooks.visibility = View.GONE
            binding.tvStatusMessage.visibility = View.VISIBLE
            binding.tvStatusMessage.text =
                "[ + NEXT READ ]\nOPEN RULED SLOT\n\nQuery the catalog above to file your first book."
        } else {
            binding.tvStatusMessage.visibility = View.GONE
            binding.rvBooks.visibility = View.VISIBLE
            bookAdapter.submitSavedBooks(
                books = books,
                onBookClick = { book -> showLogPageDialog(book) },
                onDeleteClick = { book ->
                    viewModel.deleteBook(book)
                    Toast.makeText(this, "Removed from shelf", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun renderSearchState(state: SearchUiState) {
        when (state) {
            is SearchUiState.Idle -> {
                binding.progressBar.visibility = View.GONE
                binding.rvBooks.visibility = View.GONE
                binding.tvStatusMessage.visibility = View.VISIBLE
                binding.tvStatusMessage.text = "Type a book title above and tap FIND."
            }
            is SearchUiState.Loading -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.rvBooks.visibility = View.GONE
                binding.tvStatusMessage.visibility = View.GONE
            }
            is SearchUiState.Success -> {
                binding.progressBar.visibility = View.GONE
                if (state.books.isEmpty()) {
                    binding.rvBooks.visibility = View.GONE
                    binding.tvStatusMessage.visibility = View.VISIBLE
                    binding.tvStatusMessage.text = "No catalog records found."
                } else {
                    binding.tvStatusMessage.visibility = View.GONE
                    binding.rvBooks.visibility = View.VISIBLE
                    bookAdapter.submitSearchResults(state.books) { selectedVolume ->
                        // First book filed defaults to READING so the pink highlight card shows immediately!
                        val initialStatus = if (viewModel.savedBooks.value.isEmpty()) "READING" else "WANT_TO_READ"
                        viewModel.saveVolumeToLibrary(selectedVolume, status = initialStatus)
                        Toast.makeText(this, "Filed to Index Shelf!", Toast.LENGTH_SHORT).show()
                        isShowingLibrary = true
                        updateTabStyles()
                    }
                }
            }
            is SearchUiState.Error -> {
                binding.progressBar.visibility = View.GONE
                binding.rvBooks.visibility = View.GONE
                binding.tvStatusMessage.visibility = View.VISIBLE
                binding.tvStatusMessage.text = state.message
            }
        }
    }

    /**
     * Archival "LOG PAGE" dialog inspired by Ekran Alıntısı2.jpg
     */
    private fun showLogPageDialog(book: BookEntity) {
        val percent = if (book.pageCount > 0) {
            String.format("%.1f%%", (book.currentPage.toFloat() / book.pageCount.toFloat() * 100f).coerceIn(0f, 100f))
        } else "0.0%"

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val infoText = TextView(this).apply {
            text = "PAGE ${book.currentPage} OF ${book.pageCount} ($percent)\nStatus: ${book.status}"
            textSize = 14f
        }

        val pageInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((book.currentPage + 10).coerceAtMost(maxOf(book.pageCount, book.currentPage + 10)).toString())
            hint = "Enter new current page"
        }

        container.addView(infoText)
        container.addView(pageInput)

        AlertDialog.Builder(this)
            .setTitle("LOG PAGE • ${book.title.uppercase()}")
            .setView(container)
            .setPositiveButton("LOG PAGE") { _, _ ->
                val newPage = pageInput.text.toString().toIntOrNull() ?: book.currentPage
                val newStatus = if (book.pageCount > 0 && newPage >= book.pageCount) "FINISHED" else "READING"
                viewModel.updateBookProgress(book, newPage, newStatus)
            }
            .setNeutralButton("MARK FINISHED") { _, _ ->
                val finalPage = if (book.pageCount > 0) book.pageCount else book.currentPage
                viewModel.updateBookProgress(book, finalPage, "FINISHED")
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
}