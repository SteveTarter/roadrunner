package com.tarterware.roadrunner.ports;

import java.util.List;

import com.tarterware.roadrunner.models.Bookmark;

public interface BookmarkRepository
{
    /**
     * Save bookmark to the BookmarkRepository
     * 
     * @param bookmark Bookmark to persist.
     * @return Bookmark added to BookmarkRepository
     */
    Bookmark saveBookmark(Bookmark bookmark);

    /**
     * Update bookmark to the BookmarkRepository
     * 
     * @param bookmark Bookmark to persist.
     * @return Bookmark updated to BookmarkRepository
     */
    Bookmark updateBookmark(Bookmark bookmark);

    /**
     * Delete Bookmark for Vehicle ID in the BookmarkRepository
     * 
     * @param vehicleId Vehicle ID to delete.
     */
    void deleteBookmark(String vehicleId);

    /**
     * Retrieve all Bookmarks.
     *
     * @return The list of all Bookmarks.
     */
    List<Bookmark> getAllBookmarks();

    /**
     * Retrieve Bookmark for Vehicle by its ID.
     *
     * @param vehicleId The ID of the Vehicle.
     * @return The corresponding Bookmark, or null if not found.
     */
    Bookmark getBookmark(String vehicleId);

    /**
     * Reset the BookmarkRepository, clearing all resources.
     */
    void reset();
}
