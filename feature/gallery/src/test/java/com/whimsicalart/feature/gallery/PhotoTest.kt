package com.whimsicalart.feature.gallery

import android.net.Uri
import com.whimsicalart.feature.gallery.domain.Photo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PhotoTest {

    @Test
    fun `photo data class stores values correctly`() {
        val photo = Photo(
            id = 123L,
            uri = Uri.parse("content://media/123"),
            displayName = "photo.jpg",
            dateAdded = 1700000000L,
            width = 1920,
            height = 1080
        )

        assertEquals(123L, photo.id)
        assertEquals(Uri.parse("content://media/123"), photo.uri)
        assertEquals("photo.jpg", photo.displayName)
        assertEquals(1700000000L, photo.dateAdded)
        assertEquals(1920, photo.width)
        assertEquals(1080, photo.height)
    }

    @Test
    fun `photo equality works correctly`() {
        val photo1 = Photo(
            id = 1L,
            uri = Uri.parse("content://media/1"),
            displayName = "test.jpg",
            dateAdded = 100L,
            width = 100,
            height = 100
        )

        val photo2 = Photo(
            id = 1L,
            uri = Uri.parse("content://media/1"),
            displayName = "test.jpg",
            dateAdded = 100L,
            width = 100,
            height = 100
        )

        assertEquals(photo1, photo2)
    }

    @Test
    fun `photo inequality works correctly`() {
        val photo1 = Photo(
            id = 1L,
            uri = Uri.parse("content://media/1"),
            displayName = "test.jpg",
            dateAdded = 100L,
            width = 100,
            height = 100
        )

        val photo2 = Photo(
            id = 2L,
            uri = Uri.parse("content://media/2"),
            displayName = "other.jpg",
            dateAdded = 200L,
            width = 200,
            height = 200
        )

        assertNotEquals(photo1, photo2)
    }

    @Test
    fun `photo copy creates independent instance`() {
        val original = Photo(
            id = 1L,
            uri = Uri.parse("content://media/1"),
            displayName = "test.jpg",
            dateAdded = 100L,
            width = 100,
            height = 100
        )

        val copy = original.copy(displayName = "modified.jpg")

        assertEquals("test.jpg", original.displayName)
        assertEquals("modified.jpg", copy.displayName)
    }

    @Test
    fun `photo with zero dimensions`() {
        val photo = Photo(
            id = 1L,
            uri = Uri.parse("content://media/1"),
            displayName = "test.jpg",
            dateAdded = 100L,
            width = 0,
            height = 0
        )

        assertEquals(0, photo.width)
        assertEquals(0, photo.height)
    }
}
