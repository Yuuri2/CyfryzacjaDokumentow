package com.hacknation.cyfryzacja;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "edenai.api-key=test-key")
class CyfryzacjaApplicationTests {

    @Test
    void contextLoads() {
    }
}