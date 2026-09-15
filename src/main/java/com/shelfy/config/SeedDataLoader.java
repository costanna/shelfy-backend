package com.shelfy.config;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.category.Category;
import com.shelfy.category.CategoryRepository;
import com.shelfy.user.User;
import com.shelfy.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

/**
 * Crea un usuario de prueba con algunos libros cuando shelfy.seed.enabled=true
 * (solo en el perfil local). Credenciales: demo@shelfy.app / shelfy123
 */
@Configuration
@ConditionalOnProperty(name = "shelfy.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SeedDataLoader {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    ApplicationRunner seedDatabase() {
        return args -> {
            if (userRepository.existsByEmailIgnoreCase("demo@shelfy.app")) {
                return;
            }

            User demo = userRepository.save(User.builder()
                    .email("demo@shelfy.app")
                    .password(passwordEncoder.encode("shelfy123"))
                    .name("Demo")
                    .build());

            Category fantasy = categoryRepository.save(
                    Category.builder().name("Fantasía").owner(demo).build());
            Category essay = categoryRepository.save(
                    Category.builder().name("Ensayo").owner(demo).build());

            bookRepository.save(Book.builder()
                    .title("El nombre del viento")
                    .author("Patrick Rothfuss")
                    .pageCount(662)
                    .status(BookStatus.READ)
                    .owner(demo)
                    .categories(Set.of(fantasy))
                    .build());

            bookRepository.save(Book.builder()
                    .title("Sapiens")
                    .author("Yuval Noah Harari")
                    .pageCount(496)
                    .status(BookStatus.READING)
                    .owner(demo)
                    .categories(Set.of(essay))
                    .build());

            bookRepository.save(Book.builder()
                    .title("Piranesi")
                    .author("Susanna Clarke")
                    .pageCount(272)
                    .status(BookStatus.WANT_TO_BUY)
                    .owner(demo)
                    .categories(Set.of(fantasy))
                    .build());

            log.info("Datos de ejemplo cargados — login: demo@shelfy.app / shelfy123");
        };
    }
}
