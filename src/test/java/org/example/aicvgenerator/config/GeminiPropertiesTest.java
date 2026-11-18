package org.example.aicvgenerator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class GeminiPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(GeminiProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("cv.gemini.api-key=k", "cv.gemini.model=m", "cv.gemini.base-url=http://localhost");

    private GeminiProperties bound(AssertableApplicationContext context) {
        return context.getBean(GeminiProperties.class);
    }

    @Test
    void sendsNothingAboutThinkingUnlessConfigured() {
        runner.run(context -> assertThat(bound(context).thinkingBudget()).isNull());
    }

    @Test
    void anEmptyBudgetMeansNothingIsSent() {
        runner.withPropertyValues("cv.gemini.thinking-budget=")
                .run(context -> assertThat(bound(context).thinkingBudget()).isNull());
    }

    @Test
    void aZeroBudgetIsKeptSoThinkingCanBeSwitchedOff() {
        runner.withPropertyValues("cv.gemini.thinking-budget=0")
                .run(context -> assertThat(bound(context).thinkingBudget()).isZero());
    }

    @Test
    void timeoutsAndRetriesHaveSafeDefaults() {
        runner.run(context -> {
            GeminiProperties properties = bound(context);
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(properties.maxAttempts()).isEqualTo(3);
            assertThat(properties.retryDelay()).isEqualTo(Duration.ofSeconds(1));
        });
    }

    @Test
    void theShippedApplicationYamlSendsNothingAboutThinkingByDefault() {
        // the system property pins the placeholder to "" so a GEMINI_THINKING_BUDGET set on this machine cannot
        // make the test flaky; it still checks that the yaml placeholder parses and binds to null
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withSystemProperties("GEMINI_THINKING_BUDGET=")
                .withUserConfiguration(Config.class)
                .run(context -> {
                    GeminiProperties properties = bound(context);
                    assertThat(properties.thinkingBudget()).isNull();
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(60));
                    assertThat(properties.maxAttempts()).isEqualTo(3);
                });
    }
}
