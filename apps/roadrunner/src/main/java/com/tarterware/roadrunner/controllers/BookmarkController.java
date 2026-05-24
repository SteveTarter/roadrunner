package com.tarterware.roadrunner.controllers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tarterware.roadrunner.models.Bookmark;
import com.tarterware.roadrunner.ports.BookmarkRepository;
import com.tarterware.roadrunner.security.UserPrincipal;
import com.tarterware.roadrunner.security.UserPrincipalFactory;

@RestController
@RequestMapping("/api")
public class BookmarkController
{
    private final BookmarkRepository bookmarkRepository;
    private final UserPrincipalFactory userPrincipalFactory;

    private static final Logger log = LoggerFactory.getLogger(BookmarkController.class);

    public BookmarkController(
            BookmarkRepository bookmarkRepository,
            UserPrincipalFactory userPrincipalFactory)
    {
        this.bookmarkRepository = bookmarkRepository;
        this.userPrincipalFactory = userPrincipalFactory;
    }

    @PostMapping("/bookmarks")
    public ResponseEntity<Bookmark> createBookmark(
            @RequestBody
            Bookmark bookmark,
            @AuthenticationPrincipal
            Jwt jwt)
    {
        // First, ensure user is allowed to create a bookmark.
        UserPrincipal user = userPrincipalFactory.fromJwt(jwt);
        if (!user.isSuperuser())
        {
            throw new AccessDeniedException("User " + user.email() + " must be superuser to create bookmarks!");
        }

        log.info("Creating Bookmark: {}", bookmark);
        bookmark = bookmarkRepository.saveBookmark(bookmark);

        return new ResponseEntity<Bookmark>(bookmark, HttpStatus.OK);
    }

    @PutMapping("/bookmarks")
    public ResponseEntity<Bookmark> updateBookmark(
            @RequestBody
            Bookmark bookmark,
            @AuthenticationPrincipal
            Jwt jwt)
    {
        // First, ensure user is allowed to update a bookmark.
        UserPrincipal user = userPrincipalFactory.fromJwt(jwt);
        if (!user.isSuperuser())
        {
            throw new AccessDeniedException("User " + user.email() + " must be superuser to update bookmarks!");
        }

        log.info("Updating Bookmark: {}", bookmark);
        bookmark = bookmarkRepository.updateBookmark(bookmark);

        return new ResponseEntity<Bookmark>(bookmark, HttpStatus.OK);
    }

    @DeleteMapping("/bookmarks/{vehicleId}")
    public ResponseEntity<Void> deleteBookmark(
            @PathVariable
            String vehicleId,
            @AuthenticationPrincipal
            Jwt jwt)
    {
        // First, ensure user is allowed to delete a bookmark.
        UserPrincipal user = userPrincipalFactory.fromJwt(jwt);
        if (!user.isSuperuser())
        {
            throw new AccessDeniedException("User " + user.email() + " must be superuser to update bookmarks!");
        }

        log.info("Deleting Bookmark ID {}", vehicleId);

        bookmarkRepository.deleteBookmark(vehicleId);

        return new ResponseEntity<Void>(HttpStatus.OK);
    }

    @GetMapping("/bookmarks")
    public ResponseEntity<List<Bookmark>> getAllBookmarks()
    {
        log.info("Returning list of all Bookmarks");

        List<Bookmark> bookmarks = bookmarkRepository.getAllBookmarks();

        return new ResponseEntity<List<Bookmark>>(bookmarks, HttpStatus.OK);
    }

    @GetMapping("/bookmarks/{vehicleId}")
    public ResponseEntity<Bookmark> getSingleBookmarks(
            @PathVariable
            String vehicleId)
    {
        log.info("Returning specified Bookmark");

        Bookmark bookmark = bookmarkRepository.getBookmark(vehicleId);

        return new ResponseEntity<Bookmark>(bookmark, HttpStatus.OK);
    }
}
