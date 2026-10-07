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

    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("fiction", "Ficción", "Ficció", "Fiction", "Fiction"),
            new DefaultCategory("non-fiction", "No ficción", "No-ficció", "Non-fiction", "Non-fiction"),
            new DefaultCategory("fantasy", "Fantasía", "Fantasia", "Fantasy", "Fantasy"),
            new DefaultCategory("sci-fi", "Ciencia ficción", "Ciència-ficció", "Science fiction", "Science-fiction"),
            new DefaultCategory("mystery-thriller", "Misterio y thriller", "Misteri i thriller",
                    "Mystery & thriller", "Mystère et thriller"),
            new DefaultCategory("romance", "Romance", "Romance", "Romance", "Romance"),
            new DefaultCategory("biography", "Biografía", "Biografia", "Biography", "Biographie"),
            new DefaultCategory("poetry", "Poesía", "Poesia", "Poetry", "Poésie"),
            new DefaultCategory("history", "Historia", "Història", "History", "Histoire"),
            new DefaultCategory("science", "Ciencia", "Ciència", "Science", "Sciences"),
            new DefaultCategory("children", "Infantil", "Infantil", "Children", "Jeunesse"),
            new DefaultCategory("young-adult", "Juvenil", "Juvenil", "Young adult", "Young adult"),
            new DefaultCategory("theatre", "Teatro", "Teatre", "Theatre", "Théâtre"),
            new DefaultCategory("comics-manga", "Cómic y manga", "Còmic i manga",
                    "Comics & manga", "BD et mangas"),
            new DefaultCategory("classics", "Clásicos", "Clàssics", "Classics", "Classiques"),
            new DefaultCategory("horror", "Terror", "Terror", "Horror", "Horreur")
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
    public int seedMissingDefaults(User owner) {
        List<Category> existing = categoryRepository.findByOwnerIdOrderByNameAsc(owner.getId());
        String targetLanguage = owner.getLanguagePreference() != null
                ? owner.getLanguagePreference().name()
                : "ES";

        List<Category> missing = DEFAULT_CATEGORIES.stream()
                .filter(def -> existing.stream().noneMatch(cat -> def.matches(cat.getName())))
                .map(def -> Category.builder().name(def.nameFor(targetLanguage)).owner(owner).build())
                .toList();

        if (!missing.isEmpty()) {
            categoryRepository.saveAll(missing);
        }

        // Les de fàbrica segueixen l'idioma: si el nom actual coincideix exactament
        // amb una traducció coneguda però no amb la de l'idioma actual, es reanomena.
        // Els noms personalitzats no es toquen mai.
        for (Category cat : existing) {
            for (DefaultCategory def : DEFAULT_CATEGORIES) {
                if (def.matches(cat.getName()) && !cat.getName().equals(def.nameFor(targetLanguage))) {
                    cat.setName(def.nameFor(targetLanguage));
                    break;
                }
            }
        }

        return missing.size();
    }

    @Transactional
    public List<CategoryResponse> seedMissingDefaults(Long ownerId) {
        seedMissingDefaults(userService.getEntity(ownerId));
        return listOwnedBy(ownerId);
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

    @Transactional
    public Category getOrCreate(Long ownerId, String name) {
        String trimmed = name.trim();
        return categoryRepository.findByNameIgnoreCaseAndOwnerId(trimmed, ownerId)
                .orElseGet(() -> categoryRepository.save(Category.builder()
                        .name(trimmed)
                        .owner(userService.getEntity(ownerId))
                        .build()));
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

    /**
     * Categoria de fàbrica: el nom depèn de l'idioma de l'app, però es
     * reconeix per qualsevol de les seves traduccions per no duplicar.
     */
    private record DefaultCategory(String key, String es, String ca, String en, String fr) {

        String nameFor(String language) {
            return switch (language) {
                case "CA" -> ca;
                case "EN" -> en;
                case "FR" -> fr;
                default -> es;
            };
        }

        boolean matches(String name) {
            return es.equalsIgnoreCase(name) || ca.equalsIgnoreCase(name)
                    || en.equalsIgnoreCase(name) || fr.equalsIgnoreCase(name);
        }
    }
}
