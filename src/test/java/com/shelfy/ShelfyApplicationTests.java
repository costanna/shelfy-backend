package com.shelfy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Comprueba que el contexto de Spring arranca con toda la configuración en su sitio. */
@SpringBootTest
@ActiveProfiles("local")
class ShelfyApplicationTests {

    @Test
    void contextLoads() {
    }
}
