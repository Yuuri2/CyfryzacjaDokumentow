package com.hacknation.cyfryzacja;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "EDENAI_API_KEY=test-key")
class CyfryzacjaApplicationTests {

    @Test
    void contextLoads() {
    }
}