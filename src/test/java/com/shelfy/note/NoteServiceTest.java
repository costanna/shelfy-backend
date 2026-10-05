package com.shelfy.note;

import com.shelfy.book.Book;
import com.shelfy.book.BookService;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.note.dto.NoteRequest;
import com.shelfy.note.dto.NoteResponse;
import com.shelfy.user.User;
import com.shelfy.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long BOOK_ID = 10L;
    private static final Long NOTE_ID = 100L;

    @Mock
    private NoteRepository noteRepository;
    @Mock
    private BookService bookService;
    @Mock
    private UserService userService;

    private NoteService service;

    @BeforeEach
    void setUp() {
        service = new NoteService(noteRepository, new NoteMapper(), bookService, userService);
    }

    private Book book() {
        return Book.builder().id(BOOK_ID).build();
    }

    @Test
    void listByBook_checksOwnershipBeforeReturningTheNotes() {
        when(bookService.findOwned(USER_ID, BOOK_ID)).thenReturn(book());
        Note note = Note.builder().id(1L).content("Nota").book(book()).build();
        when(noteRepository.findByBookIdOrderByCreatedAtDesc(BOOK_ID)).thenReturn(List.of(note));

        List<NoteResponse> result = service.listByBook(USER_ID, BOOK_ID);

        assertThat(result).hasSize(1);
        verify(bookService).findOwned(USER_ID, BOOK_ID);
    }

    @Test
    void listByBook_throwsWhenTheBookIsNotOwnedByTheCaller() {
        when(bookService.findOwned(USER_ID, BOOK_ID))
                .thenThrow(new ResourceNotFoundException("Libro", BOOK_ID));

        assertThatThrownBy(() -> service.listByBook(USER_ID, BOOK_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_trimsTheContentAndKeepsThePageReference() {
        when(bookService.findOwned(USER_ID, BOOK_ID)).thenReturn(book());
        when(userService.getEntity(USER_ID)).thenReturn(User.builder().id(USER_ID).build());
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        NoteResponse response = service.create(USER_ID, BOOK_ID, new NoteRequest("  Una nota  ", 42));

        assertThat(response.content()).isEqualTo("Una nota");
        assertThat(response.pageReference()).isEqualTo(42);
    }

    @Test
    void delete_removesTheNoteWhenItBelongsToTheBook() {
        when(bookService.findOwned(USER_ID, BOOK_ID)).thenReturn(book());
        Note note = Note.builder().id(NOTE_ID).build();
        when(noteRepository.findByIdAndBookId(NOTE_ID, BOOK_ID)).thenReturn(Optional.of(note));

        service.delete(USER_ID, BOOK_ID, NOTE_ID);

        verify(noteRepository).delete(note);
    }

    @Test
    void delete_throwsWhenTheNoteDoesNotBelongToThatBook() {
        when(bookService.findOwned(USER_ID, BOOK_ID)).thenReturn(book());
        when(noteRepository.findByIdAndBookId(NOTE_ID, BOOK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(USER_ID, BOOK_ID, NOTE_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
