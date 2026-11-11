package com.egemenerdem.bookstash

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
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

    private enum class ScreenTab { LIBRARY, SEARCH, CHALLENGE }

    private lateinit var binding: ActivityMainBinding
    private val bookAdapter = BookAdapter()
    private var currentTab = ScreenTab.LIBRARY
    private var selectedStatusFilter: String = "ALL" // ALL, READING, WANT_TO_READ, FINISHED

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
        binding.rvBooks.layoutManager = GridLayoutManager(this, 2)
        binding.rvBooks.adapter = bookAdapter
    }

    private fun setupListeners() {
        binding.btnSearch.setOnClickListener { triggerSearch() }

        binding.etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                triggerSearch()
                true
            } else false
        }

        binding.btnShowLibrary.setOnClickListener { switchTab(ScreenTab.LIBRARY) }
        binding.btnShowSearch.setOnClickListener { switchTab(ScreenTab.SEARCH) }
        binding.btnShowChallenge.setOnClickListener { switchTab(ScreenTab.CHALLENGE) }
        binding.cardStatChallenge.setOnClickListener { switchTab(ScreenTab.CHALLENGE) }

        // Status Filter Tabs (Row 7 of Notion Task List)
        binding.chipAll.setOnClickListener { applyStatusFilter("ALL") }
        binding.chipReading.setOnClickListener { applyStatusFilter("READING") }
        binding.chipWishlist.setOnClickListener { applyStatusFilter("WANT_TO_READ") }
        binding.chipFinished.setOnClickListener { applyStatusFilter("FINISHED") }

        // Bottom Bar JUMP TO cycles through the screens
        binding.tvBottomBarJump.setOnClickListener {
            val next = when (currentTab) {
                ScreenTab.LIBRARY -> ScreenTab.CHALLENGE
                ScreenTab.CHALLENGE -> ScreenTab.SEARCH
                ScreenTab.SEARCH -> ScreenTab.LIBRARY
            }
            switchTab(next)
        }
    }

    private fun applyStatusFilter(status: String) {
        selectedStatusFilter = status
        updateStatusChipStyles()
        renderLibraryState(viewModel.savedBooks.value)
    }

    private fun updateStatusChipStyles() {
        val chips = listOf(
            "ALL" to binding.chipAll,
            "READING" to binding.chipReading,
            "WANT_TO_READ" to binding.chipWishlist,
            "FINISHED" to binding.chipFinished
        )
        for ((status, view) in chips) {
            if (status == selectedStatusFilter) {
                view.setBackgroundColor(Color.parseColor("#E93B81"))
                view.setTextColor(Color.WHITE)
            } else {
                view.setBackgroundColor(Color.parseColor("#FAF8F3"))
                view.setTextColor(Color.parseColor("#141413"))
            }
        }
    }

    private fun switchTab(tab: ScreenTab) {
        currentTab = tab
        updateTabStyles()
        when (tab) {
            ScreenTab.LIBRARY -> renderLibraryState(viewModel.savedBooks.value)
            ScreenTab.SEARCH -> renderSearchState(viewModel.searchUiState.value)
            ScreenTab.CHALLENGE -> renderChallengeState(viewModel.savedBooks.value)
        }
    }

    private fun triggerSearch() {
        val query = binding.etSearchQuery.text.toString()
        switchTab(ScreenTab.SEARCH)
        viewModel.searchBooks(query)
    }

    private fun updateTabStyles() {
        val ink = Color.parseColor("#141413")
        val paper = Color.parseColor("#F5F2EB")
        val border = Color.parseColor("#D5CFC2")

        binding.btnShowLibrary.setBackgroundColor(if (currentTab == ScreenTab.LIBRARY) ink else border)
        binding.btnShowLibrary.setTextColor(if (currentTab == ScreenTab.LIBRARY) paper else ink)

        binding.btnShowSearch.setBackgroundColor(if (currentTab == ScreenTab.SEARCH) ink else border)
        binding.btnShowSearch.setTextColor(if (currentTab == ScreenTab.SEARCH) paper else ink)

        binding.btnShowChallenge.setBackgroundColor(if (currentTab == ScreenTab.CHALLENGE) ink else border)
        binding.btnShowChallenge.setTextColor(if (currentTab == ScreenTab.CHALLENGE) paper else ink)

        binding.statusFilterContainer.visibility =
            if (currentTab == ScreenTab.LIBRARY) View.VISIBLE else View.GONE
        binding.challengeContainer.visibility =
            if (currentTab == ScreenTab.CHALLENGE) View.VISIBLE else View.GONE

        when (currentTab) {
            ScreenTab.LIBRARY -> {
                binding.tvIndexSubtitle.text = "SHELF • 2026 INDEX"
                binding.tvHeader.text = "READING NOW"
                binding.tvBottomBarStatus.text = "Shelf • Reading now"
            }
            ScreenTab.SEARCH -> {
                binding.tvIndexSubtitle.text = "CATALOG • GOOGLE BOOKS"
                binding.tvHeader.text = "CATALOG SEARCH"
                binding.tvBottomBarStatus.text = "Catalog • Google Books API"
            }
            ScreenTab.CHALLENGE -> {
                binding.tvIndexSubtitle.text = "2026 • ANNUAL LEDGER"
                binding.tvHeader.text = "BOOK CHALLENGE"
                binding.tvBottomBarStatus.text = "2026 challenge • ${viewModel.savedBooks.value.count { it.status == "FINISHED" }} of 12"
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.savedBooks.collect { books ->
                        updateLedgerStats(books)
                        when (currentTab) {
                            ScreenTab.LIBRARY -> renderLibraryState(books)
                            ScreenTab.CHALLENGE -> renderChallengeState(books)
                            else -> {}
                        }
                    }
                }
                launch {
                    viewModel.searchUiState.collect { state ->
                        if (currentTab == ScreenTab.SEARCH) {
                            renderSearchState(state)
                        }
                    }
                }
            }
        }
    }

    private fun updateLedgerStats(books: List<BookEntity>) {
        binding.btnShowLibrary.text = "SHELF (${books.size})"

        val activeBooks = books.filter { it.status == "READING" }
        val finishedBooks = books.filter { it.status == "FINISHED" }
        binding.tvActiveSummary.text = "${activeBooks.size} active\n${finishedBooks.size} finished"

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
        binding.challengeContainer.visibility = View.GONE

        val filtered = if (selectedStatusFilter == "ALL") {
            books
        } else {
            books.filter { it.status == selectedStatusFilter }
        }

        if (filtered.isEmpty()) {
            binding.rvBooks.visibility = View.GONE
            binding.tvStatusMessage.visibility = View.VISIBLE
            binding.tvStatusMessage.text =
                "[ + NEXT READ ]\nOPEN RULED SLOT ($selectedStatusFilter)\n\nTap API CATALOG or switch filter tabs above."
        } else {
            binding.tvStatusMessage.visibility = View.GONE
            binding.rvBooks.visibility = View.VISIBLE
            bookAdapter.submitSavedBooks(
                books = filtered,
                onBookClick = { book -> showLogPageDialog(book) },
                onDeleteClick = { book ->
                    viewModel.deleteBook(book)
                    Toast.makeText(this, "Removed from shelf", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun renderChallengeState(books: List<BookEntity>) {
        binding.progressBar.visibility = View.GONE
        binding.rvBooks.visibility = View.GONE
        binding.tvStatusMessage.visibility = View.GONE
        binding.challengeContainer.visibility = View.VISIBLE

        val goal = 12
        val finishedCount = books.count { it.status == "FINISHED" }
        val readingCount = books.count { it.status == "READING" }
        val percent = (finishedCount.toFloat() / goal.toFloat() * 100f).coerceIn(0f, 100f)

        binding.tvChallengeBigFraction.text = "$finishedCount / $goal"
        binding.tvChallengePercent.text = String.format("%.1f%%", percent)
        binding.tvBottomBarStatus.text = "2026 challenge • $finishedCount of $goal"

        val latestBook = books.firstOrNull()
        binding.tvLatestSyncedBook.text = if (latestBook != null) {
            "Synced ledger • ${latestBook.title}"
        } else {
            "Synced ledger • No books filed yet"
        }

        // Build the 2x6 12-Slot Book Ledger Grid (like Ekran Alıntısı4_2.jpg)
        binding.bookGrid12Container.removeAllViews()
        for (row in 0 until 2) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (row > 0) topMargin = 12
                }
            }

            for (col in 0 until 6) {
                val slotIndex = row * 6 + col
                val bgColor = when {
                    slotIndex < finishedCount -> Color.parseColor("#141413") // Solid Black = Finished
                    slotIndex < finishedCount + readingCount -> Color.parseColor("#E93B81") // Pink = Reading Now
                    else -> Color.parseColor("#F5F2EB") // Open Ruled Slot
                }
                val textColor = if (slotIndex < finishedCount + readingCount) {
                    Color.parseColor("#FAF8F3")
                } else {
                    Color.parseColor("#7A756C")
                }

                val slotView = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 110, 1f).apply {
                        setMargins(4, 0, 4, 0)
                    }
                    setBackgroundColor(bgColor)
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    setPadding(4, 12, 4, 4)
                    typeface = Typeface.MONOSPACE
                    textSize = 9f
                    setTextColor(textColor)
                    text = "≡≡≡\n${(slotIndex + 1).toString().padStart(2, '0')}"
                }
                rowLayout.addView(slotView)
            }
            binding.bookGrid12Container.addView(rowLayout)
        }
    }

    private fun renderSearchState(state: SearchUiState) {
        binding.challengeContainer.visibility = View.GONE
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
                        val initialStatus =
                            if (viewModel.savedBooks.value.none { it.status == "READING" }) "READING" else "WANT_TO_READ"
                        viewModel.saveVolumeToLibrary(selectedVolume, status = initialStatus)
                        Toast.makeText(this, "Filed to Index Shelf!", Toast.LENGTH_SHORT).show()
                        switchTab(ScreenTab.LIBRARY)
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

    private fun showLogPageDialog(book: BookEntity) {
        val percent = if (book.pageCount > 0) {
            String.format(
                "%.1f%%",
                (book.currentPage.toFloat() / book.pageCount.toFloat() * 100f).coerceIn(0f, 100f)
            )
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
            setText(
                (book.currentPage + 10).coerceAtMost(maxOf(book.pageCount, book.currentPage + 10))
                    .toString()
            )
            hint = "Enter new current page"
        }

        container.addView(infoText)
        container.addView(pageInput)

        AlertDialog.Builder(this)
            .setTitle("LOG PAGE • ${book.title.uppercase()}")
            .setView(container)
            .setPositiveButton("LOG PAGE") { _, _ ->
                val newPage = pageInput.text.toString().toIntOrNull() ?: book.currentPage
                val newStatus =
                    if (book.pageCount > 0 && newPage >= book.pageCount) "FINISHED" else "READING"
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