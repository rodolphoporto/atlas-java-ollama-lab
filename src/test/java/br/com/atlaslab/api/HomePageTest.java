package br.com.atlaslab.api;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HomePageTest {

    @Test
    void packagesChatInterfaceAtTheSpringBootWelcomePageLocation() throws Exception {
        var page = new ClassPathResource("static/index.html");

        assertThat(page.exists()).isTrue();
        assertThat(page.getContentAsString(StandardCharsets.UTF_8))
                .contains("ATLAS · Local AI Lab")
                .contains("/styles.css")
                .contains("/app.js");
    }
}
