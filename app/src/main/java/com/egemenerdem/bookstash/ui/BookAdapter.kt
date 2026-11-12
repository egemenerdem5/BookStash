package com.egemenerdem.bookstash.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.egemenerdem.bookstash.data.BookEntity
import com.egemenerdem.bookstash.databinding.ItemBookBinding
import com.egemenerdem.bookstash.model.BookVolumeItem

data class BookUiRow(
    val id: String,
    val catalogCode: String,
    val statusBadge: String,
    val title: String,
    val authors: String,
    val metaLeft: String,
    val metaRight: String,
    val coverUrl: String?,
    val isHighlightedPink: Boolean,
    val isNextReadSlot: Boolean = false,
    val onCardClick: () -> Unit,
    val onActionClick: () -> Unit
)

class BookAdapter : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    private val items = mutableListOf<BookUiRow>()

    fun submitSearchResults(
        volumes: List<BookVolumeItem>,
        onSaveClick: (BookVolumeItem) -> Unit
    ) {
        items.clear()
        items.addAll(
            volumes.mapIndexed { index, item ->
                val info = item.volumeInfo
                val code = "C-${(index + 1).toString().padStart(2, '0')} / API"
                BookUiRow(
                    id = item.id,
                    catalogCode = code,
                    statusBadge = "+ FILE",
                    title = info?.title ?: "Unknown Title",
                    authors = info?.authors?.joinToString(", ") ?: "Unknown Author",
                    metaLeft = "${info?.pageCount ?: 0} pages",
                    metaRight = "TAP TO FILE",
                    coverUrl = info?.imageLinks?.httpsThumbnail,
                    isHighlightedPink = false,
                    isNextReadSlot = false,
                    onCardClick = { onSaveClick(item) },
                    onActionClick = { onSaveClick(item) }
                )
            }
        )
        notifyDataSetChanged()
    }

    fun submitSavedBooks(
        books: List<BookEntity>,
        onBookClick: (BookEntity) -> Unit,
        onDeleteLongClick: (BookEntity) -> Unit,
        onNextReadClick: () -> Unit
    ) {
        items.clear()
        items.addAll(
            books.mapIndexed { index, book ->
                val isReading = book.status == "READING"
                val code = "A-${(index + 1).toString().padStart(2, '0')} / BOOK"
                val badge = when (book.status) {
                    "READING" -> "READING"
                    "FINISHED" -> "FILED"
                    else -> "QUEUED"
                }
                val percent = if (book.pageCount > 0) {
                    val p = (book.currentPage.toFloat() / book.pageCount.toFloat() * 100f)
                    String.format("%.1f%%", p.coerceIn(0f, 100f))
                } else {
                    "0.0%"
                }

                val leftMeta = if (book.status == "FINISHED") "Finished" else "${book.currentPage} / ${book.pageCount}"
                val rightMeta = if (book.status == "FINISHED") "Filed" else percent

                BookUiRow(
                    id = book.id,
                    catalogCode = code,
                    statusBadge = badge,
                    title = book.title,
                    authors = book.authors,
                    metaLeft = leftMeta,
                    metaRight = rightMeta,
                    coverUrl = book.coverUrl,
                    isHighlightedPink = isReading,
                    isNextReadSlot = false,
                    onCardClick = { onBookClick(book) },
                    onActionClick = { onDeleteLongClick(book) }
                )
            }
        )

        // Append the "+ NEXT READ / OPEN RULED SLOT" card at the end of the shelf (Matches Ekran Alıntısı6_2.jpg)
        items.add(
            BookUiRow(
                id = "next_read_slot",
                catalogCode = "SLOT / OPEN",
                statusBadge = "+",
                title = "+\nNEXT READ",
                authors = "OPEN RULED SLOT",
                metaLeft = "Catalog search",
                metaRight = "→",
                coverUrl = null,
                isHighlightedPink = false,
                isNextReadSlot = true,
                onCardClick = onNextReadClick,
                onActionClick = onNextReadClick
            )
        )
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val binding = ItemBookBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BookViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class BookViewHolder(private val binding: ItemBookBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: BookUiRow) {
            binding.tvCatalogCode.text = row.catalogCode
            binding.tvStatusBadge.text = row.statusBadge
            binding.tvBookTitle.text = row.title
            binding.tvBookAuthors.text = row.authors
            binding.tvBookMeta.text = row.metaLeft
            binding.btnAction.text = row.metaRight

            if (row.isNextReadSlot) {
                binding.cardBook.setCardBackgroundColor(Color.parseColor("#F5F2EB"))
                binding.cardBook.strokeColor = Color.parseColor("#7A756C")
                binding.ivBookCover.visibility = View.GONE
                binding.tvStatusBadge.visibility = View.GONE
                binding.tvBookTitle.setTextColor(Color.parseColor("#141413"))
                binding.tvBookAuthors.setTextColor(Color.parseColor("#7A756C"))
                binding.tvBookMeta.setTextColor(Color.parseColor("#7A756C"))
                binding.btnAction.setBackgroundColor(Color.TRANSPARENT)
                binding.btnAction.setTextColor(Color.parseColor("#141413"))
                binding.hole1.setTextColor(Color.parseColor("#7A756C"))
                binding.hole2.setTextColor(Color.parseColor("#7A756C"))
                binding.hole3.setTextColor(Color.parseColor("#7A756C"))
            } else if (row.isHighlightedPink) {
                // Active Reading Card: Pink outer card with ink-black inner block (Matches Ekran Alıntısı6_2.jpg)
                binding.cardBook.setCardBackgroundColor(Color.parseColor("#E93B81"))
                binding.cardBook.strokeColor = Color.parseColor("#141413")
                binding.ivBookCover.visibility = View.VISIBLE
                binding.tvStatusBadge.visibility = View.VISIBLE
                binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#141413"))
                binding.tvStatusBadge.setTextColor(Color.parseColor("#FAF8F3"))
                binding.tvCatalogCode.setTextColor(Color.parseColor("#141413"))
                binding.tvBookTitle.setTextColor(Color.parseColor("#FAF8F3"))
                binding.tvBookAuthors.setTextColor(Color.parseColor("#141413"))
                binding.tvBookMeta.setTextColor(Color.parseColor("#141413"))
                binding.btnAction.setBackgroundColor(Color.TRANSPARENT)
                binding.btnAction.setTextColor(Color.parseColor("#141413"))
                binding.hole1.setTextColor(Color.parseColor("#FAF8F3"))
                binding.hole2.setTextColor(Color.parseColor("#FAF8F3"))
                binding.hole3.setTextColor(Color.parseColor("#FAF8F3"))
            } else {
                // Filed / Queued White Archival Card
                binding.cardBook.setCardBackgroundColor(Color.parseColor("#FAF8F3"))
                binding.cardBook.strokeColor = Color.parseColor("#D5CFC2")
                binding.ivBookCover.visibility = View.VISIBLE
                binding.tvStatusBadge.visibility = View.VISIBLE
                binding.tvStatusBadge.setBackgroundColor(
                    if (row.statusBadge == "FILED" || row.statusBadge == "+ FILE") Color.parseColor("#141413")
                    else Color.parseColor("#EDEAE2")
                )
                binding.tvStatusBadge.setTextColor(
                    if (row.statusBadge == "FILED" || row.statusBadge == "+ FILE") Color.parseColor("#FAF8F3")
                    else Color.parseColor("#141413")
                )
                binding.tvCatalogCode.setTextColor(Color.parseColor("#7A756C"))
                binding.tvBookTitle.setTextColor(Color.parseColor("#141413"))
                binding.tvBookAuthors.setTextColor(Color.parseColor("#7A756C"))
                binding.tvBookMeta.setTextColor(Color.parseColor("#7A756C"))
                binding.btnAction.setBackgroundColor(Color.TRANSPARENT)
                binding.btnAction.setTextColor(Color.parseColor("#7A756C"))
                binding.hole1.setTextColor(Color.parseColor("#7A756C"))
                binding.hole2.setTextColor(Color.parseColor("#7A756C"))
                binding.hole3.setTextColor(Color.parseColor("#7A756C"))
            }

            if (!row.isNextReadSlot) {
                binding.ivBookCover.load(row.coverUrl) { crossfade(true) }
            }

            binding.root.setOnClickListener { row.onCardClick() }
            binding.root.setOnLongClickListener {
                row.onActionClick()
                true
            }
            binding.btnAction.setOnClickListener { row.onActionClick() }
        }
    }
}