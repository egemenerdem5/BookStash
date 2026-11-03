# BookStash Physical Database Schema & ER Diagram

This Entity-Relationship (ER) diagram represents the normalized SQLite database managed by Jetpack Room (`BookStashDatabase`, version 1).

```mermaid
erDiagram
    books ||--o{ notes : "has (ON DELETE CASCADE)"

    books {
        TEXT id PK "Google Books Volume ID"
        TEXT title NOT_NULL "Book Title"
        TEXT authors NOT_NULL "Comma-separated authors"
        TEXT description NULL "Book synopsis"
        TEXT thumbnailUrl NULL "HTTPS cover image URL"
        INTEGER pageCount NOT_NULL "Total pages (default 0)"
        INTEGER currentPage NOT_NULL "User reading progress (default 0)"
        TEXT status NOT_NULL "WANT_TO_READ | READING | FINISHED"
        INTEGER dateAdded NOT_NULL "Epoch timestamp (ms)"
    }

    notes {
        INTEGER id PK "Auto-generated Primary Key"
        TEXT bookId FK "References books(id), Indexed"
        TEXT contentText NOT_NULL "User text note or quote"
        TEXT imagePath NULL "Internal storage URI path for photo notes"
        INTEGER createdAt NOT_NULL "Epoch timestamp (ms)"
    }

