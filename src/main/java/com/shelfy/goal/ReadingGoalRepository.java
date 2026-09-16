package com.shelfy.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReadingGoalRepository extends JpaRepository<ReadingGoal, Long> {

    Optional<ReadingGoal> findByOwnerIdAndYear(Long ownerId, int year);
}
