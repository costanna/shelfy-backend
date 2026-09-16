package com.shelfy.goal;

import com.shelfy.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reading_goals",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "goal_year"}),
        indexes = @Index(name = "idx_reading_goals_owner_id", columnList = "owner_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadingGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "goal_year", nullable = false)
    private int year;

    @Column(name = "target_books", nullable = false)
    private int targetBooks;
}
