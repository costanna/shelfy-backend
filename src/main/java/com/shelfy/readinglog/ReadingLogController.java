package com.shelfy.readinglog;

import com.shelfy.readinglog.dto.MarkReadingDayRequest;
import com.shelfy.readinglog.dto.ReadingCalendarResponse;
import com.shelfy.readinglog.dto.ReadingStreakResponse;
import com.shelfy.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reading-log")
@RequiredArgsConstructor
public class ReadingLogController {

    private final ReadingLogService readingLogService;

    @GetMapping
    public ReadingCalendarResponse calendar(@AuthenticationPrincipal UserPrincipal principal,
                                            @RequestParam int year,
                                            @RequestParam int month) {
        return readingLogService.calendar(principal.getId(), year, month);
    }

    @GetMapping("/streak")
    public ReadingStreakResponse streak(@AuthenticationPrincipal UserPrincipal principal) {
        return readingLogService.streak(principal.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mark(@AuthenticationPrincipal UserPrincipal principal,
                     @Valid @RequestBody MarkReadingDayRequest request) {
        readingLogService.mark(principal.getId(), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmark(@AuthenticationPrincipal UserPrincipal principal,
                       @RequestParam Long bookId,
                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        readingLogService.unmark(principal.getId(), bookId, date);
    }
}
