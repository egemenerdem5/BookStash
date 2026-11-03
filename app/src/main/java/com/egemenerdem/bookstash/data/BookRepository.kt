package com.egemenerdem.bookstash.data

import com.egemenerdem.bookstash.model.BookVolumeItem
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
     * Queries the Google Books API and catches HTTP (4xx/5xx) and connectivity errors
     * so the Android UI can display a clean message instead of crashing.
     */
    suspend fun searchBooksRemote(query: String): ApiResult<List<BookVolumeItem>> {
        return try {
            val response = apiService.searchBooks(query = query)
            ApiResult.Success(response.items ?: emptyList())
        } catch (e: HttpException) {
            ApiResult.Error(e, "Server error (${e.code()}): Unable to fetch books right now.")
        } catch (e: IOException) {
            ApiResult.Error(e, "No internet connection. Please check your network and try again.")
        } catch (e: Exception) {
            ApiResult.Error(e, "An unexpected error occurred: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}