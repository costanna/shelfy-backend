package com.shelfy.note;

import com.shelfy.note.dto.NoteRequest;
import com.shelfy.note.dto.NoteResponse;
import com.shelfy.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/books/{bookId}/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    public List<NoteResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable Long bookId) {
        return noteService.listByBook(principal.getId(), bookId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable Long bookId,
                               @Valid @RequestBody NoteRequest request) {
        return noteService.create(principal.getId(), bookId, request);
    }

    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable Long bookId,
                       @PathVariable Long noteId) {
        noteService.delete(principal.getId(), bookId, noteId);
    }
}
