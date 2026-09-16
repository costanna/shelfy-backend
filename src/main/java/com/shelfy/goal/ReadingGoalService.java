package com.shelfy.goal;

import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.goal.dto.ReadingGoalRequest;
import com.shelfy.goal.dto.ReadingGoalResponse;
import com.shelfy.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

@Service
@RequiredArgsConstructor
public class ReadingGoalService {

    private final ReadingGoalRepository goalRepository;
    private final BookRepository bookRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public ReadingGoalResponse getCurrent(Long ownerId) {
        int year = Year.now().getValue();
        Integer target = goalRepository.findByOwnerIdAndYear(ownerId, year)
                .map(ReadingGoal::getTargetBooks)
                .orElse(null);

        return new ReadingGoalResponse(year, target, booksReadInYear(ownerId, year));
    }

    @Transactional
    public ReadingGoalResponse setCurrent(Long ownerId, ReadingGoalRequest request) {
        int year = Year.now().getValue();
        ReadingGoal goal = goalRepository.findByOwnerIdAndYear(ownerId, year)
                .orElseGet(() -> ReadingGoal.builder()
                        .owner(userService.getEntity(ownerId))
                        .year(year)
                        .build());

        goal.setTargetBooks(request.targetBooks());
        goalRepository.save(goal);

        return new ReadingGoalResponse(year, goal.getTargetBooks(), booksReadInYear(ownerId, year));
    }

    private long booksReadInYear(Long ownerId, int year) {
        return bookRepository.findByOwnerId(ownerId).stream()
                .filter(book -> book.getStatus() == BookStatus.READ
                        && book.getFinishedAt() != null
                        && book.getFinishedAt().getYear() == year)
                .count();
    }
}
