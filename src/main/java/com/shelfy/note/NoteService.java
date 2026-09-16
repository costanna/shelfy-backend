package com.shelfy.note;

import com.shelfy.book.Book;
import com.shelfy.book.BookService;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.note.dto.NoteRequest;
import com.shelfy.note.dto.NoteResponse;
import com.shelfy.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final NoteMapper noteMapper;
    private final BookService bookService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<NoteResponse> listByBook(Long userId, Long bookId) {
        bookService.findOwned(userId, bookId);

        return noteRepository.findByBookIdOrderByCreatedAtDesc(bookId).stream()
                .map(noteMapper::toResponse)
                .toList();
    }

    @Transactional
    public NoteResponse create(Long userId, Long bookId, NoteRequest request) {
        Book book = bookService.findOwned(userId, bookId);

        Note note = Note.builder()
                .book(book)
                .user(userService.getEntity(userId))
                .content(request.content().trim())
                .pageReference(request.pageReference())
                .build();

        return noteMapper.toResponse(noteRepository.save(note));
    }

    @Transactional
    public void delete(Long userId, Long bookId, Long noteId) {
        bookService.findOwned(userId, bookId);

        Note note = noteRepository.findByIdAndBookId(noteId, bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Nota", noteId));

        noteRepository.delete(note);
    }
}
