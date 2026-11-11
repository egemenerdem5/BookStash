package com.egemenerdem.bookstash.ui

import android.graphics.Color
import android.view.LayoutInflater
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
    val metaText: String,
    val coverUrl: String?,
    val isHighlightedPink: Boolean,
    val actionLabel: String,
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
                    statusBadge = "CATALOG",
                    title = info?.title ?: "Unknown Title",
                    authors = info?.authors?.joinToString(", ") ?: "Unknown Author",
                    metaText = "${info?.pageCount ?: 0} pages",
                    coverUrl = info?.imageLinks?.httpsThumbnail,
                    isHighlightedPink = false,
                    actionLabel = "+ FILE",
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
        onDeleteClick: (BookEntity) -> Unit
    ) {
        items.clear()
        items.addAll(
            books.mapIndexed { index, book ->
                val isReading = book.status == "READING"
                val code = "A-${(index + 1).toString().padStart(2, '0')} / LOG"
                val badge = when (book.status) {
                    "READING" -> "READING"
                    "FINISHED" -> "FILED"
                    else -> "QUEUED"
                }
                val percent = if (book.pageCount > 0) {
                    val p = (book.currentPage.toFloat() / book.pageCount.toFloat() * 100f)
                    String.format("%.0f%%", p.coerceIn(0f, 100f))
                } else {
                    "0%"
                }

                BookUiRow(
                    id = book.id,
                    catalogCode = code,
                    statusBadge = badge,
                    title = book.title,
                    authors = book.authors,
                    metaText = "${book.currentPage}/${book.pageCount} • $percent",
                    coverUrl = book.coverUrl,
                    isHighlightedPink = isReading,
                    actionLabel = "DEL",
                    onCardClick = { onBookClick(book) },
                    onActionClick = { onDeleteClick(book) }
                )
            }
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
            binding.tvBookMeta.text = row.metaText
            binding.btnAction.text = row.actionLabel

            // Apply Pink Active Card theme (like "THE ATLAS OF SMALL HOURS" in Ekran Alıntısı6)
            if (row.isHighlightedPink) {
                binding.cardBook.setCardBackgroundColor(Color.parseColor("#E93B81"))
                binding.tvCatalogCode.setTextColor(Color.parseColor("#141413"))
                binding.tvBookTitle.setTextColor(Color.parseColor("#FAF8F3"))
                binding.tvBookAuthors.setTextColor(Color.parseColor("#141413"))
                binding.tvBookMeta.setTextColor(Color.parseColor("#141413"))
                binding.hole1.setTextColor(Color.parseColor("#FAF8F3"))
                binding.hole2.setTextColor(Color.parseColor("#FAF8F3"))
                binding.hole3.setTextColor(Color.parseColor("#FAF8F3"))
            } else {
                binding.cardBook.setCardBackgroundColor(Color.parseColor("#FAF8F3"))
                binding.tvCatalogCode.setTextColor(Color.parseColor("#7A756C"))
                binding.tvBookTitle.setTextColor(Color.parseColor("#141413"))
                binding.tvBookAuthors.setTextColor(Color.parseColor("#7A756C"))
                binding.tvBookMeta.setTextColor(Color.parseColor("#141413"))
                binding.hole1.setTextColor(Color.parseColor("#7A756C"))
                binding.hole2.setTextColor(Color.parseColor("#7A756C"))
                binding.hole3.setTextColor(Color.parseColor("#7A756C"))
            }

            binding.ivBookCover.load(row.coverUrl) {
                crossfade(true)
            }

            binding.root.setOnClickListener {
                row.onCardClick()
            }

            binding.btnAction.setOnClickListener {
                row.onActionClick()
            }
        }
    }
}