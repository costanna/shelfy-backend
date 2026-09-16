package com.shelfy.note;

import com.shelfy.note.dto.NoteResponse;
import org.springframework.stereotype.Component;

@Component
public class NoteMapper {

    public NoteResponse toResponse(Note note) {
        return new NoteResponse(
                note.getId(),
                note.getContent(),
                note.getPageReference(),
                note.getCreatedAt(),
                note.getBook().getId()
        );
    }
}
