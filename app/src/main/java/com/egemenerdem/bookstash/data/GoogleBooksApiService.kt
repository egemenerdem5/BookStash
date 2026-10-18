package com.egemenerdem.bookstash.data

import com.egemenerdem.bookstash.model.BookSearchResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleBooksApiService {

    /**
     * Searches books on Google Books API.
     * Retrofit automatically URL-encodes the @Query parameter, preventing URL injection bugs.
     */
    @GET("volumes")
    suspend fun searchBooks(
        @Query("q") query: String,
        @Query("maxResults") maxResults: Int = 20,
        @Query("printType") printType: String = "books"
    ): BookSearchResponse

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/books/v1/"

        val instance: GoogleBooksApiService by lazy {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GoogleBooksApiService::class.java)
        }
    }


}