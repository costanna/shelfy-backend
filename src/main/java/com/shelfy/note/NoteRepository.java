package com.shelfy.note;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByBookIdOrderByCreatedAtDesc(Long bookId);

    Optional<Note> findByIdAndBookId(Long id, Long bookId);

    void deleteByBookId(Long bookId);

    List<Note> findByUserId(Long userId);
}
