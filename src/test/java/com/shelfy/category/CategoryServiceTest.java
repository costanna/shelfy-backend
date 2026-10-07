package com.shelfy.category;

import com.shelfy.category.dto.CategoryRequest;
import com.shelfy.category.dto.CategoryResponse;
import com.shelfy.common.exception.DuplicateResourceException;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.user.User;
import com.shelfy.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long OWNER_ID = 1L;

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserService userService;

    @Captor
    private ArgumentCaptor<List<Category>> savedCategoriesCaptor;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categoryRepository, new CategoryMapper(), userService);
    }

    private User owner() {
        return User.builder().id(OWNER_ID).build();
    }

    private Category category(Long id, String name) {
        return Category.builder().id(id).name(name).owner(owner()).build();
    }

    @Test
    void seedMissingDefaults_onlyAddsThoseNotAlreadyPresentCaseInsensitively() {
        when(categoryRepository.findByOwnerIdOrderByNameAsc(OWNER_ID))
                .thenReturn(List.of(category(1L, "ficción"), category(2L, "Poesía")));

        int added = service.seedMissingDefaults(owner());

        verify(categoryRepository).saveAll(savedCategoriesCaptor.capture());
        List<String> addedNames = savedCategoriesCaptor.getValue().stream().map(Category::getName).toList();

        assertThat(added).isEqualTo(14);
        assertThat(addedNames)
                .contains("No ficción", "Fantasía", "Terror", "Historia")
                .doesNotContain("Ficción", "Poesía");
    }

    @Test
    void seedMissingDefaults_addsOnlyNewOnesWhenOldDefaultsAlreadyExist() {
        List<Category> allOldDefaults = List.of(
                category(1L, "Ficción"), category(2L, "No ficción"), category(3L, "Fantasía"),
                category(4L, "Ciencia ficción"), category(5L, "Misterio y thriller"),
                category(6L, "Romance"), category(7L, "Biografía"), category(8L, "Poesía"));
        when(categoryRepository.findByOwnerIdOrderByNameAsc(OWNER_ID)).thenReturn(allOldDefaults);

        int added = service.seedMissingDefaults(owner());

        assertThat(added).isEqualTo(8);
        verify(categoryRepository).saveAll(savedCategoriesCaptor.capture());
        assertThat(savedCategoriesCaptor.getValue().stream().map(Category::getName).toList())
                .containsExactlyInAnyOrder("Historia", "Ciencia", "Infantil", "Juvenil",
                        "Teatro", "Cómic y manga", "Clásicos", "Terror");
    }

    @Test
    void seedMissingDefaults_seedsInFrenchWhenThatIsTheUserLanguage() {
        User frenchOwner = User.builder().id(OWNER_ID).languagePreference(com.shelfy.user.LanguagePreference.FR).build();
        when(categoryRepository.findByOwnerIdOrderByNameAsc(OWNER_ID)).thenReturn(List.of());

        int added = service.seedMissingDefaults(frenchOwner);

        assertThat(added).isEqualTo(16);
        verify(categoryRepository).saveAll(savedCategoriesCaptor.capture());
        assertThat(savedCategoriesCaptor.getValue().stream().map(Category::getName).toList())
                .contains("Fiction", "Biographie", "Horreur", "Jeunesse");
    }

    @Test
    void seedMissingDefaults_renamesFactoryCategoriesToTheCurrentLanguage() {
        User catalanOwner = User.builder().id(OWNER_ID).languagePreference(com.shelfy.user.LanguagePreference.CA).build();
        Category legacy = category(1L, "Fantasía");
        Category custom = category(2L, "Mis pendientes");
        when(categoryRepository.findByOwnerIdOrderByNameAsc(OWNER_ID))
                .thenReturn(List.of(legacy, custom));

        service.seedMissingDefaults(catalanOwner);

        assertThat(legacy.getName()).isEqualTo("Fantasia");
        assertThat(custom.getName()).isEqualTo("Mis pendientes");
    }

    @Test
    void create_savesCategoryOwnedByUser() {
        when(categoryRepository.existsByNameIgnoreCaseAndOwnerId("Clásicos", OWNER_ID)).thenReturn(false);
        when(userService.getEntity(OWNER_ID)).thenReturn(owner());
        when(categoryRepository.save(org.mockito.ArgumentMatchers.any(Category.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = service.create(OWNER_ID, new CategoryRequest("Clásicos"));

        assertThat(response.name()).isEqualTo("Clásicos");
    }

    @Test
    void create_throwsWhenNameAlreadyExistsForThatOwner() {
        when(categoryRepository.existsByNameIgnoreCaseAndOwnerId("Fantasía", OWNER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(OWNER_ID, new CategoryRequest("Fantasía")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void update_renamesCategoryWhenNewNameIsFree() {
        Category category = category(5L, "Vieja");
        when(categoryRepository.findByIdAndOwnerId(5L, OWNER_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCaseAndOwnerId("Nueva", OWNER_ID)).thenReturn(false);

        CategoryResponse response = service.update(OWNER_ID, 5L, new CategoryRequest("Nueva"));

        assertThat(response.name()).isEqualTo("Nueva");
        assertThat(category.getName()).isEqualTo("Nueva");
    }

    @Test
    void update_allowsKeepingTheSameNameWithDifferentCasingWithoutUniquenessCheck() {
        Category category = category(5L, "Fantasía");
        when(categoryRepository.findByIdAndOwnerId(5L, OWNER_ID)).thenReturn(Optional.of(category));

        service.update(OWNER_ID, 5L, new CategoryRequest("FANTASÍA"));

        verify(categoryRepository, never())
                .existsByNameIgnoreCaseAndOwnerId(org.mockito.ArgumentMatchers.anyString(), anyLong());
        assertThat(category.getName()).isEqualTo("FANTASÍA");
    }

    @Test
    void update_throwsWhenRenamingToAnAlreadyUsedName() {
        Category category = category(5L, "Vieja");
        when(categoryRepository.findByIdAndOwnerId(5L, OWNER_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCaseAndOwnerId("Fantasía", OWNER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.update(OWNER_ID, 5L, new CategoryRequest("Fantasía")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void update_throwsWhenCategoryNotOwnedByCaller() {
        when(categoryRepository.findByIdAndOwnerId(5L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(OWNER_ID, 5L, new CategoryRequest("Nueva")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesOwnedCategory() {
        Category category = category(5L, "Fantasía");
        when(categoryRepository.findByIdAndOwnerId(5L, OWNER_ID)).thenReturn(Optional.of(category));

        service.delete(OWNER_ID, 5L);

        verify(categoryRepository, times(1)).delete(category);
    }

    @Test
    void delete_throwsWhenCategoryNotOwnedByCaller() {
        when(categoryRepository.findByIdAndOwnerId(5L, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(OWNER_ID, 5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolveOwned_returnsEmptySetWithoutQueryingWhenIdsIsNullOrEmpty() {
        assertThat(service.resolveOwned(OWNER_ID, null)).isEmpty();
        assertThat(service.resolveOwned(OWNER_ID, Set.of())).isEmpty();
        verify(categoryRepository, never()).findByIdInAndOwnerId(org.mockito.ArgumentMatchers.anySet(), anyLong());
    }

    @Test
    void getOrCreate_returnsExistingCategoryWithoutSavingANewOne() {
        Category existing = category(3L, "Fantasía");
        when(categoryRepository.findByNameIgnoreCaseAndOwnerId("Fantasía", OWNER_ID))
                .thenReturn(Optional.of(existing));

        Category result = service.getOrCreate(OWNER_ID, "Fantasía");

        assertThat(result).isSameAs(existing);
        verify(categoryRepository, never()).save(org.mockito.ArgumentMatchers.any(Category.class));
    }

    @Test
    void getOrCreate_createsNewCategoryWhenNoneMatches() {
        when(categoryRepository.findByNameIgnoreCaseAndOwnerId("Terror", OWNER_ID)).thenReturn(Optional.empty());
        when(userService.getEntity(OWNER_ID)).thenReturn(owner());
        when(categoryRepository.save(org.mockito.ArgumentMatchers.any(Category.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Category result = service.getOrCreate(OWNER_ID, "  Terror  ");

        assertThat(result.getName()).isEqualTo("Terror");
    }
}
