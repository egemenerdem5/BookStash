package com.egemenerdem.bookstash.model

import com.squareup.moshi.Json

/**
 * Top-level response from GET https://www.googleapis.com/books/v1/volumes?q=...
 */
data class BookSearchResponse(
    @Json(name = "items")
    val items: List<BookVolumeItem>? = emptyList()
)

data class BookVolumeItem(
    @Json(name = "id")
    val id: String,
    @Json(name = "volumeInfo")
    val volumeInfo: VolumeInfo?
)

data class VolumeInfo(
    @Json(name = "title")
    val title: String? = "Unknown Title",
    @Json(name = "authors")
    val authors: List<String>? = emptyList(),
    @Json(name = "description")
    val description: String? = null,
    @Json(name = "pageCount")
    val pageCount: Int? = 0,
    @Json(name = "imageLinks")
    val imageLinks: ImageLinks? = null
)

data class ImageLinks(
    @Json(name = "smallThumbnail")
    val smallThumbnail: String? = null,
    @Json(name = "thumbnail")
    val thumbnail: String? = null
) {
    /**
     * Google Books API often returns "http://" URLs for thumbnails.
     * Upgrading to "https://" allows Coil to load images without enabling
     * insecure cleartext HTTP traffic in the AndroidManifest.
     */
    val httpsThumbnail: String?
        get() = (thumbnail ?: smallThumbnail)?.replace("http://", "https://")


}