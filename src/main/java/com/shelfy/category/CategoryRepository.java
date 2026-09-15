package com.shelfy.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByOwnerIdOrderByNameAsc(Long ownerId);

    Optional<Category> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByNameIgnoreCaseAndOwnerId(String name, Long ownerId);

    Set<Category> findByIdInAndOwnerId(Set<Long> ids, Long ownerId);
}
