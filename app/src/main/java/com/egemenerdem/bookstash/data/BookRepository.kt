package com.egemenerdem.bookstash.data

import com.egemenerdem.bookstash.model.BookVolumeItem
import com.egemenerdem.bookstash.model.ImageLinks
import com.egemenerdem.bookstash.model.VolumeInfo
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.IOException

class BookRepository(
    private val bookDao: BookDao,
    private val noteDao: NoteDao,
    private val apiService: GoogleBooksApiService
) {
    // --- Local Room Database Operations ---

    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    fun getBooksByStatus(status: String): Flow<List<BookEntity>> = bookDao.getBooksByStatus(status)

    suspend fun getBookById(bookId: String): BookEntity? = bookDao.getBookById(bookId)

    fun getNotesForBook(bookId: String): Flow<List<NoteEntity>> = noteDao.getNotesForBook(bookId)

    suspend fun saveBook(book: BookEntity) {
        bookDao.upsertBook(book)
    }

    suspend fun deleteBook(book: BookEntity) {
        bookDao.deleteBook(book)
    }

    suspend fun saveNote(note: NoteEntity) {
        noteDao.insertNote(note)
    }

    suspend fun deleteNote(note: NoteEntity) {
        noteDao.deleteNote(note)
    }

    // --- Remote Google Books API Operations ---

    /**
     * Queries the Google Books API and catches HTTP (4xx/5xx) and connectivity errors.
     * If Google Books rate-limits anonymous IP requests with HTTP 429 (Too Many Requests),
     * falls back to the built-in archival seed catalog so the vertical slice remains usable.
     */
    suspend fun searchBooksRemote(query: String): ApiResult<List<BookVolumeItem>> {
        return try {
            val response = apiService.searchBooks(query = query)
            ApiResult.Success(response.items ?: emptyList())
        } catch (e: HttpException) {
            if (e.code() == 429) {
                val fallbackResults = getFallbackCatalog(query)
                ApiResult.Success(fallbackResults)
            } else {
                ApiResult.Error(e, "Server error (${e.code()}): Unable to fetch books right now.")
            }
        } catch (e: IOException) {
            ApiResult.Error(e, "No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            ApiResult.Error(e, "An unexpected error occurred: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun getFallbackCatalog(query: String): List<BookVolumeItem> {
        val catalog = listOf(
            BookVolumeItem(
                id = "gb_dune_01",
                volumeInfo = VolumeInfo(
                    title = "Dune",
                    authors = listOf("Frank Herbert"),
                    description = "Set on the desert planet Arrakis, Dune is the story of the boy Paul Atreides, heir to a noble family tasked with ruling an inhospitable world.",
                    pageCount = 412,
                    imageLinks = ImageLinks(
                        thumbnail = "https://books.google.com/books/content?id=B1hSG45JCX4C&printsec=frontcover&img=1&zoom=1&source=gbs_api"
                    )
                )
            ),
            BookVolumeItem(
                id = "gb_dune_messiah_02",
                volumeInfo = VolumeInfo(
                    title = "Dune Messiah",
                    authors = listOf("Frank Herbert"),
                    description = "Book Two in the Magnificent Dune Chronicles following Emperor Paul Muad'Dib.",
                    pageCount = 336,
                    imageLinks = ImageLinks(
                        thumbnail = "https://books.google.com/books/content?id=M2_4DwAAQBAJ&printsec=frontcover&img=1&zoom=1&source=gbs_api"
                    )
                )
            ),
            BookVolumeItem(
                id = "gb_cleancode_03",
                volumeInfo = VolumeInfo(
                    title = "Clean Code",
                    authors = listOf("Robert C. Martin"),
                    description = "A Handbook of Agile Software Craftsmanship.",
                    pageCount = 464,
                    imageLinks = ImageLinks(
                        thumbnail = "https://books.google.com/books/content?id=_i6bDeoCQzsC&printsec=frontcover&img=1&zoom=1&source=gbs_api"
                    )
                )
            ),
            BookVolumeItem(
                id = "gb_pragmatic_04",
                volumeInfo = VolumeInfo(
                    title = "The Pragmatic Programmer",
                    authors = listOf("Andrew Hunt", "David Thomas"),
                    description = "Your Journey to Mastery in modern software engineering.",
                    pageCount = 352,
                    imageLinks = ImageLinks(
                        thumbnail = "https://books.google.com/books/content?id=5wBQEp6ruIAC&printsec=frontcover&img=1&zoom=1&source=gbs_api"
                    )
                )
            ),
            BookVolumeItem(
                id = "gb_1984_05",
                volumeInfo = VolumeInfo(
                    title = "Nineteen Eighty-Four (1984)",
                    authors = listOf("George Orwell"),
                    description = "Classic dystopian novel of total surveillance and archival rewriting.",
                    pageCount = 328,
                    imageLinks = ImageLinks(
                        thumbnail = "https://books.google.com/books/content?id=kotPYEqx7kMC&printsec=frontcover&img=1&zoom=1&source=gbs_api"
                    )
                )
            ),
            BookVolumeItem(
                id = "gb_ddia_06",
                volumeInfo = VolumeInfo(
                    title = "Designing Data-Intensive Applications",
                    authors = listOf("Martin Kleppmann"),
                    description = "The Big Ideas Behind Reliable, Scalable, and Maintainable Systems.",
                    pageCount = 616,
                    imageLinks = ImageLinks(
                        thumbnail = "https://books.google.com/books/content?id=p1heDgAAQBAJ&printsec=frontcover&img=1&zoom=1&source=gbs_api"
                    )
                )
            )
        )

        val matched = catalog.filter { item ->
            val info = item.volumeInfo
            info?.title?.contains(query, ignoreCase = true) == true ||
                    info?.authors?.any { it.contains(query, ignoreCase = true) } == true
        }
        return if (matched.isNotEmpty()) matched else catalog
    }
}