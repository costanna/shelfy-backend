package com.shelfy.category;

import com.shelfy.category.dto.CategoryRequest;
import com.shelfy.category.dto.CategoryResponse;
import com.shelfy.common.exception.DuplicateResourceException;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.user.User;
import com.shelfy.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final List<String> DEFAULT_CATEGORY_NAMES = List.of(
            "Ficción", "No ficción", "Fantasía", "Ciencia ficción",
            "Misterio y thriller", "Romance", "Biografía", "Poesía"
    );

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listOwnedBy(Long ownerId) {
        return categoryRepository.findByOwnerIdOrderByNameAsc(ownerId).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional
    public void seedDefaults(User owner) {
        List<Category> defaults = DEFAULT_CATEGORY_NAMES.stream()
                .map(name -> Category.builder().name(name).owner(owner).build())
                .toList();
        categoryRepository.saveAll(defaults);
    }

    @Transactional
    public CategoryResponse create(Long ownerId, CategoryRequest request) {
        requireUniqueName(request.name(), ownerId);

        Category category = Category.builder()
                .name(request.name().trim())
                .owner(userService.getEntity(ownerId))
                .build();

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long ownerId, Long id, CategoryRequest request) {
        Category category = findOwned(ownerId, id);

        if (!category.getName().equalsIgnoreCase(request.name().trim())) {
            requireUniqueName(request.name(), ownerId);
        }
        category.setName(request.name().trim());

        return categoryMapper.toResponse(category);
    }

    @Transactional
    public void delete(Long ownerId, Long id) {
        categoryRepository.delete(findOwned(ownerId, id));
    }

    @Transactional(readOnly = true)
    public Set<Category> resolveOwned(Long ownerId, Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return categoryRepository.findByIdInAndOwnerId(ids, ownerId);
    }

    private Category findOwned(Long ownerId, Long id) {
        return categoryRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría", id));
    }

    private void requireUniqueName(String name, Long ownerId) {
        if (categoryRepository.existsByNameIgnoreCaseAndOwnerId(name.trim(), ownerId)) {
            throw new DuplicateResourceException("Ya tienes una categoría con ese nombre");
        }
    }
}
