package com.egemenerdem.bookstash

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import com.egemenerdem.bookstash.data.BookEntity
import com.egemenerdem.bookstash.data.BookRepository
import com.egemenerdem.bookstash.data.BookStashDatabase
import com.egemenerdem.bookstash.data.GoogleBooksApiService
import com.egemenerdem.bookstash.data.NoteEntity
import com.egemenerdem.bookstash.databinding.ActivityMainBinding
import com.egemenerdem.bookstash.ui.BookAdapter
import com.egemenerdem.bookstash.ui.BookViewModel
import com.egemenerdem.bookstash.ui.BookViewModelFactory
import com.egemenerdem.bookstash.ui.SearchUiState
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private enum class ArchivalScreen {
        READING_NOW,
        LOG_PAGE,
        KEEP_THE_LINE,
        BOOK_CHALLENGE,
        READING_DAY
    }

    private lateinit var binding: ActivityMainBinding
    private val bookAdapter = BookAdapter()
    private var currentScreen = ArchivalScreen.READING_NOW
    private var isSearchDrawerOpen = false
    private var selectedTag: String = "#character"
    private var draftPageNumber: Int = 0
    private var dailyReadingMinutes: Int = 34

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
        setupBackNavigation()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.rvBooks.layoutManager = GridLayoutManager(this, 2)
        binding.rvBooks.adapter = bookAdapter
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSearchDrawerOpen) {
                    closeSearchDrawer()
                } else if (currentScreen != ArchivalScreen.READING_NOW) {
                    navigateTo(ArchivalScreen.READING_NOW)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupListeners() {
        // 3-Box Metric Strip Contextual Shortcuts on READING NOW
        binding.cardStatPage.setOnClickListener { openBookLogPage(getActiveOrFirstBook()) }
        binding.cardStatStreak.setOnClickListener { navigateTo(ArchivalScreen.READING_DAY) }
        binding.cardStatChallenge.setOnClickListener { navigateTo(ArchivalScreen.BOOK_CHALLENGE) }

        // Search Drawer Listeners
        binding.btnSearch.setOnClickListener { triggerSearch() }
        binding.btnCloseSearch.setOnClickListener { closeSearchDrawer() }
        binding.etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                triggerSearch()
                true
            } else false
        }

        // LOG PAGE Controls (-1, +1, Log page, Session File Done)
        binding.btnStepMinus1.setOnClickListener { adjustDraftPage(-1) }
        binding.btnStepPlus1.setOnClickListener { adjustDraftPage(1) }

        binding.btnLogPageCommit.setOnClickListener {
            val book = getActiveOrFirstBook() ?: return@setOnClickListener
            val newStatus = if (book.pageCount > 0 && draftPageNumber >= book.pageCount) "FINISHED" else "READING"
            viewModel.updateBookProgress(book, draftPageNumber, newStatus)
            Toast.makeText(this, "Logged page $draftPageNumber", Toast.LENGTH_SHORT).show()
        }

        binding.btnMarkFinishedDirect.setOnClickListener {
            val book = getActiveOrFirstBook() ?: return@setOnClickListener
            val finalPage = if (book.pageCount > 0) book.pageCount else maxOf(book.currentPage, draftPageNumber)
            draftPageNumber = finalPage
            viewModel.updateBookProgress(book, finalPage, "FINISHED")
            Toast.makeText(this, "Filed ${book.title} as FINISHED", Toast.LENGTH_SHORT).show()
        }

        // KEEP THE LINE Tag Selection & Save Quote
        binding.tagCharacter.setOnClickListener { selectNoteTag("#character") }
        binding.tagTurningPoint.setOnClickListener { selectNoteTag("#turning-point") }
        binding.tagReread.setOnClickListener { selectNoteTag("#reread") }

        binding.btnSaveQuote.setOnClickListener {
            val book = getActiveOrFirstBook()
            if (book == null) {
                Toast.makeText(this, "File a book to your shelf first!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val quote = binding.etQuoteInput.text.toString().trim()
            val note = binding.etReaderNoteInput.text.toString().trim()
            if (quote.isEmpty()) {
                Toast.makeText(this, "Enter a quote line first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveQuoteNote(
                bookId = book.id,
                quoteText = quote,
                readerNote = note,
                tag = selectedTag,
                pageNumber = book.currentPage
            )
            binding.etQuoteInput.text?.clear()
            binding.etReaderNoteInput.text?.clear()
            Toast.makeText(this, "Saved • p. ${book.currentPage}", Toast.LENGTH_SHORT).show()
        }

        // READING DAY Interactive Session Timer (+5 min tap) & Jump to Log Page
        binding.readingDayPanel.cardReadingTimerToggle.setOnClickListener {
            dailyReadingMinutes += 5
            renderReadingDayState(getActiveOrFirstBook())
        }
        binding.readingDayPanel.cardSessionJumpToLog.setOnClickListener {
            openBookLogPage(getActiveOrFirstBook())
        }

        // Bottom Archival Bar Navigation: '#' opens index menu, 'JUMP TO ->' cycles or opens index
        binding.btnBottomIndexIcon.setOnClickListener { showArchivalJumpMenu() }
        binding.tvBottomBarStatus.setOnClickListener { showArchivalJumpMenu() }
        binding.tvBottomBarJump.setOnClickListener { showArchivalJumpMenu() }
    }

    private fun showArchivalJumpMenu() {
        val options = arrayOf(
            "1. READING NOW (Shelf Index)",
            "2. LOG PAGE (Page Progress)",
            "3. KEEP THE LINE (Quotes & Notes)",
            "4. BOOK CHALLENGE (2026 Annual Ledger)",
            "5. READING DAY (18-Day Streak)",
            "+ QUERY GOOGLE BOOKS CATALOG"
        )
        AlertDialog.Builder(this)
            .setTitle("JUMP TO • ARCHIVAL INDEX")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        closeSearchDrawer()
                        navigateTo(ArchivalScreen.READING_NOW)
                    }
                    1 -> openBookLogPage(getActiveOrFirstBook())
                    2 -> openBookNotes(getActiveOrFirstBook())
                    3 -> navigateTo(ArchivalScreen.BOOK_CHALLENGE)
                    4 -> navigateTo(ArchivalScreen.READING_DAY)
                    5 -> openSearchDrawer()
                }
            }
            .show()
    }

    private fun openSearchDrawer() {
        currentScreen = ArchivalScreen.READING_NOW
        isSearchDrawerOpen = true
        binding.searchCard.visibility = View.VISIBLE
        binding.etSearchQuery.requestFocus()
        updateScreenVisibilityAndHeaders()
        renderSearchState(viewModel.searchUiState.value)
    }

    private fun closeSearchDrawer() {
        isSearchDrawerOpen = false
        binding.searchCard.visibility = View.GONE
        binding.tvStatusMessage.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        updateScreenVisibilityAndHeaders()
        renderLibraryState(viewModel.savedBooks.value)
    }

    private fun getActiveOrFirstBook(): BookEntity? {
        val books = viewModel.savedBooks.value
        val selectedId = viewModel.selectedBookId.value
        return books.find { it.id == selectedId }
            ?: books.firstOrNull { it.status == "READING" }
            ?: books.firstOrNull()
    }

    private fun openBookLogPage(book: BookEntity?) {
        if (book != null) {
            viewModel.selectBook(book.id)
            draftPageNumber = book.currentPage
        }
        navigateTo(ArchivalScreen.LOG_PAGE)
    }

    private fun openBookNotes(book: BookEntity?) {
        if (book != null) {
            viewModel.selectBook(book.id)
        }
        navigateTo(ArchivalScreen.KEEP_THE_LINE)
    }

    private fun adjustDraftPage(delta: Int) {
        val book = getActiveOrFirstBook() ?: return
        val maxPage = if (book.pageCount > 0) book.pageCount else 9999
        draftPageNumber = (draftPageNumber + delta).coerceIn(0, maxPage)
        renderLogPageState(book)
    }

    private fun selectNoteTag(tag: String) {
        selectedTag = tag
        val tags = listOf(
            "#character" to binding.tagCharacter,
            "#turning-point" to binding.tagTurningPoint,
            "#reread" to binding.tagReread
        )
        for ((t, view) in tags) {
            if (t == selectedTag) {
                view.setBackgroundColor(Color.parseColor("#141413"))
                view.setTextColor(Color.parseColor("#F5F2EB"))
            } else {
                view.setBackgroundColor(Color.parseColor("#FAF8F3"))
                view.setTextColor(Color.parseColor("#141413"))
            }
        }
    }

    private fun navigateTo(screen: ArchivalScreen) {
        currentScreen = screen
        if (screen != ArchivalScreen.READING_NOW) {
            isSearchDrawerOpen = false
            binding.searchCard.visibility = View.GONE
        }
        val activeBook = getActiveOrFirstBook()
        if (activeBook != null && viewModel.selectedBookId.value == null) {
            viewModel.selectBook(activeBook.id)
            draftPageNumber = activeBook.currentPage
        }
        updateScreenVisibilityAndHeaders()
        when (screen) {
            ArchivalScreen.READING_NOW -> {
                if (isSearchDrawerOpen) renderSearchState(viewModel.searchUiState.value)
                else renderLibraryState(viewModel.savedBooks.value)
            }
            ArchivalScreen.LOG_PAGE -> renderLogPageState(activeBook)
            ArchivalScreen.KEEP_THE_LINE -> renderNotesState(activeBook, viewModel.currentBookNotes.value)
            ArchivalScreen.BOOK_CHALLENGE -> renderChallengeState(viewModel.savedBooks.value)
            ArchivalScreen.READING_DAY -> renderReadingDayState(activeBook)
        }
    }

    private fun triggerSearch() {
        val query = binding.etSearchQuery.text.toString()
        isSearchDrawerOpen = true
        viewModel.searchBooks(query)
    }

    private fun updateScreenVisibilityAndHeaders() {
        binding.readingNowContainer.visibility =
            if (currentScreen == ArchivalScreen.READING_NOW) View.VISIBLE else View.GONE
        binding.logPageContainer.visibility =
            if (currentScreen == ArchivalScreen.LOG_PAGE) View.VISIBLE else View.GONE
        binding.keepTheLineContainer.visibility =
            if (currentScreen == ArchivalScreen.KEEP_THE_LINE) View.VISIBLE else View.GONE
        binding.challengeContainer.visibility =
            if (currentScreen == ArchivalScreen.BOOK_CHALLENGE) View.VISIBLE else View.GONE
        binding.readingDayPanel.root.visibility =
            if (currentScreen == ArchivalScreen.READING_DAY) View.VISIBLE else View.GONE

        val books = viewModel.savedBooks.value
        val activeCount = books.count { it.status == "READING" }
        val finishedCount = books.count { it.status == "FINISHED" }
        val book = getActiveOrFirstBook()
        val notesCount = viewModel.currentBookNotes.value.size

        // Match Exact Top Headers & Bottom Bar Text from Reference Screenshots!
        when (currentScreen) {
            ArchivalScreen.READING_NOW -> {
                binding.tvIndexSubtitle.text = "SHELF • 2026 INDEX"
                binding.tvHeader.text = "READING NOW"
                binding.tvActiveSummary.text = "$activeCount active\n$finishedCount finished"
                binding.tvBottomBarStatus.text = "Shelf • Reading now"
            }
            ArchivalScreen.LOG_PAGE -> {
                binding.tvIndexSubtitle.text = "A-01 • CURRENT RECORD"
                binding.tvHeader.text = "LOG PAGE"
                binding.tvActiveSummary.text = "Started Nov\nBook"
                binding.tvBottomBarStatus.text = "Log page • $draftPageNumber of ${book?.pageCount ?: 0}"
            }
            ArchivalScreen.KEEP_THE_LINE -> {
                binding.tvIndexSubtitle.text = "QUOTES • $notesCount SAVED"
                binding.tvHeader.text = "KEEP THE LINE"
                binding.tvActiveSummary.text = "filtered\n$selectedTag"
                binding.tvBottomBarStatus.text = "Quotes • $notesCount saved"
            }
            ArchivalScreen.BOOK_CHALLENGE -> {
                binding.tvIndexSubtitle.text = "2026 • ANNUAL LEDGER"
                binding.tvHeader.text = "BOOK CHALLENGE"
                binding.tvActiveSummary.text = "On pace\nOct 19"
                binding.tvBottomBarStatus.text = "2026 challenge • $finishedCount of 12"
            }
            ArchivalScreen.READING_DAY -> {
                binding.tvIndexSubtitle.text = "TODAY • NOV 2026"
                binding.tvHeader.text = "READING DAY"
                binding.tvActiveSummary.text = "482 min\nthis month"
                binding.tvBottomBarStatus.text = "Today • 18-day streak"
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.savedBooks.collect { books ->
                        updateLedgerStats(books)
                        val active = getActiveOrFirstBook()
                        if (active != null && viewModel.selectedBookId.value == null) {
                            viewModel.selectBook(active.id)
                            draftPageNumber = active.currentPage
                        }
                        updateScreenVisibilityAndHeaders()
                        when (currentScreen) {
                            ArchivalScreen.READING_NOW -> {
                                if (!isSearchDrawerOpen) renderLibraryState(books)
                            }
                            ArchivalScreen.LOG_PAGE -> renderLogPageState(getActiveOrFirstBook())
                            ArchivalScreen.KEEP_THE_LINE -> renderNotesState(getActiveOrFirstBook(), viewModel.currentBookNotes.value)
                            ArchivalScreen.BOOK_CHALLENGE -> renderChallengeState(books)
                            ArchivalScreen.READING_DAY -> renderReadingDayState(getActiveOrFirstBook())
                        }
                    }
                }
                launch {
                    viewModel.searchUiState.collect { state ->
                        if (currentScreen == ArchivalScreen.READING_NOW && isSearchDrawerOpen) {
                            renderSearchState(state)
                        }
                    }
                }
                launch {
                    viewModel.currentBookNotes.collect { notes ->
                        if (currentScreen == ArchivalScreen.KEEP_THE_LINE) {
                            updateScreenVisibilityAndHeaders()
                            renderNotesState(getActiveOrFirstBook(), notes)
                        }
                    }
                }
            }
        }
    }

    private fun updateLedgerStats(books: List<BookEntity>) {
        val finishedBooks = books.filter { it.status == "FINISHED" }
        val primaryBook = getActiveOrFirstBook()
        if (primaryBook != null) {
            binding.tvStatPages.text = "${primaryBook.currentPage} / ${primaryBook.pageCount}"
        } else {
            binding.tvStatPages.text = "0 / 0"
        }
        binding.tvStatStreak.text = "18 DAYS"
        binding.tvStatChallenge.text = "${finishedBooks.size} / 12"
    }

    private fun renderLibraryState(books: List<BookEntity>) {
        binding.progressBar.visibility = View.GONE
        binding.tvStatusMessage.visibility = View.GONE
        binding.rvBooks.visibility = View.VISIBLE

        bookAdapter.submitSavedBooks(
            books = books,
            onBookClick = { book -> openBookLogPage(book) },
            onDeleteLongClick = { book ->
                AlertDialog.Builder(this)
                    .setTitle(book.title)
                    .setItems(arrayOf("Open Log Page", "Open Keep The Line (Quotes)", "Delete Book from Shelf")) { _, which ->
                        when (which) {
                            0 -> openBookLogPage(book)
                            1 -> openBookNotes(book)
                            2 -> {
                                viewModel.deleteBook(book)
                                Toast.makeText(this, "Removed from shelf", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .show()
            },
            onNextReadClick = { openSearchDrawer() }
        )
    }

    private fun renderLogPageState(book: BookEntity?) {
        if (book == null) {
            binding.tvLogBookTitle.text = "NO BOOK SELECTED"
            binding.tvLogPageFraction.text = "PAGE 0\nOF 0"
            binding.tvLogPagePercent.text = "0.0%"
            binding.btnLogPageCommit.text = "Log page • 0"
            return
        }

        val total = maxOf(book.pageCount, 1)
        val percent = (draftPageNumber.toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f)

        binding.ivLogBookCover.load(book.coverUrl) { crossfade(true) }
        binding.tvLogBookTitle.text = book.title.uppercase()
        binding.tvLogPageFraction.text = "PAGE $draftPageNumber\nOF ${book.pageCount}"
        binding.tvLogPagePercent.text = String.format("%.1f%%", percent)
        binding.tvStripMaxPage.text = "${book.pageCount}"
        binding.btnLogPageCommit.text = "Log page • $draftPageNumber"
        binding.tvSessionLastLogged.text = "+9 min over goal • est. 5 sessions left\nLast logged page ${book.currentPage}"
        binding.tvBottomBarStatus.text = "Log page • $draftPageNumber of ${book.pageCount}"

        // Build 10-tick visual page strip (Exact match to Ekran Alıntısı2_3.jpg)
        val activeTickCount = ((percent / 10f).toInt()).coerceIn(1, 10)
        binding.pageStrip10Container.removeAllViews()
        for (i in 1..10) {
            val isCurrentHead = (i == activeTickCount && draftPageNumber > 0)
            val isFilled = (i < activeTickCount && draftPageNumber > 0)
            val bgColor = when {
                isCurrentHead -> Color.parseColor("#E93B81")
                isFilled -> Color.parseColor("#F5C2D7")
                else -> Color.parseColor("#FAF8F3")
            }
            val tickView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(1, 0, 1, 0)
                }
                setBackgroundColor(bgColor)
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
                textSize = 8f
                setTextColor(Color.parseColor("#141413"))
                text = if (isCurrentHead) "$draftPageNumber" else ""
            }
            binding.pageStrip10Container.addView(tickView)
        }
    }

    private fun renderNotesState(book: BookEntity?, notes: List<NoteEntity>) {
        val page = book?.currentPage ?: 0
        binding.btnSaveQuote.text = "Save quote • page $page"
        binding.tvSavedBadgePage.text = "SAVED • P. $page"

        val latestNote = notes.firstOrNull()
        if (latestNote != null) {
            binding.tvFeaturedQuoteText.text = latestNote.contentText.lines().firstOrNull() ?: latestNote.contentText
            binding.tvFeaturedQuoteBook.text = "${book?.title ?: "Book"} • p. $page"
        } else {
            binding.tvFeaturedQuoteText.text = "\"Stories are a\nrehearsal for\nfeeling.\""
            binding.tvFeaturedQuoteBook.text = "${book?.title ?: "The Atlas of Small Hours"} • p. $page"
        }

        binding.savedNotesListContainer.removeAllViews()
        for (note in notes) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(Color.parseColor("#FAF8F3"))
                setPadding(24, 18, 24, 18)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10 }
            }

            val textView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                typeface = Typeface.MONOSPACE
                textSize = 10f
                setTextColor(Color.parseColor("#141413"))
                text = note.contentText
            }

            val delBtn = TextView(this).apply {
                typeface = Typeface.MONOSPACE
                textSize = 9f
                setTextColor(Color.parseColor("#FAF8F3"))
                setBackgroundColor(Color.parseColor("#141413"))
                setPadding(14, 6, 14, 6)
                text = "DEL"
                setOnClickListener { viewModel.deleteNote(note) }
            }

            row.addView(textView)
            row.addView(delBtn)
            binding.savedNotesListContainer.addView(row)
        }
    }

    private fun renderChallengeState(books: List<BookEntity>) {
        val goal = 12
        val finishedCount = books.count { it.status == "FINISHED" }
        val readingCount = books.count { it.status == "READING" }
        val percent = (finishedCount.toFloat() / goal.toFloat() * 100f).coerceIn(0f, 100f)

        binding.tvChallengeBigFraction.text = "$finishedCount / $goal"
        binding.tvChallengePercent.text = String.format("%.1f%%", percent)

        val latestBook = books.firstOrNull { it.status == "FINISHED" } ?: books.firstOrNull()
        binding.tvLatestSyncedBook.text = if (latestBook != null) {
            "Synced finish • ${latestBook.title}"
        } else {
            "Synced finish • Paper Suns"
        }

        binding.bookGrid12Container.removeAllViews()
        for (row in 0 until 2) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (row > 0) topMargin = 10
                }
            }

            for (col in 0 until 6) {
                val slotIndex = row * 6 + col
                val bgColor = when {
                    slotIndex < finishedCount -> Color.parseColor("#141413")
                    slotIndex < finishedCount + readingCount -> Color.parseColor("#E93B81")
                    else -> Color.parseColor("#FAF8F3")
                }
                val textColor = if (slotIndex < finishedCount + readingCount) {
                    Color.parseColor("#FAF8F3")
                } else {
                    Color.parseColor("#7A756C")
                }

                val slotView = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 104, 1f).apply {
                        setMargins(3, 0, 3, 0)
                    }
                    setBackgroundColor(bgColor)
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    setPadding(4, 10, 4, 4)
                    typeface = Typeface.MONOSPACE
                    textSize = 9f
                    setTextColor(textColor)
                    text = "≡≡≡"
                }
                rowLayout.addView(slotView)
            }
            binding.bookGrid12Container.addView(rowLayout)
        }
    }

    private fun renderReadingDayState(book: BookEntity?) {
        val currentPage = book?.currentPage ?: 214
        val startPage = (currentPage - 33).coerceAtLeast(0)
        val overGoal = (dailyReadingMinutes - 25).coerceAtLeast(0)

        binding.readingDayPanel.tvDailyMinutesBig.text = "$dailyReadingMinutes / 25"
        binding.readingDayPanel.tvReadingDaySessionPages.text = "Page $startPage → $currentPage"
        binding.readingDayPanel.tvReadingDaySessionBook.text =
            "$dailyReadingMinutes min • ${book?.title ?: "The Atlas of Small Hours"}"
        binding.readingDayPanel.tvStatOverGoal.text = "+$overGoal"
        binding.readingDayPanel.tvStatMonthMinutes.text = "${448 + dailyReadingMinutes}"
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
                        val initialStatus =
                            if (viewModel.savedBooks.value.none { it.status == "READING" }) "READING" else "WANT_TO_READ"
                        viewModel.saveVolumeToLibrary(selectedVolume, status = initialStatus)
                        Toast.makeText(this, "Filed to Index Shelf!", Toast.LENGTH_SHORT).show()
                        closeSearchDrawer()
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
}