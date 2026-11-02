package com.example.shortener.config;

import com.example.shortener.service.ShortCodeGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CodePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    void validConfigurationBinds() {
        contextRunner
                .withPropertyValues(
                        "app.code.multiplier=9132277",
                        "app.code.offset=4861105",
                        "app.code.reserved=docs,help")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CodeProperties properties = context.getBean(CodeProperties.class);
                    assertThat(properties.multiplier()).isEqualTo(9_132_277L);
                    assertThat(properties.offset()).isEqualTo(4_861_105L);
                    assertThat(properties.reserved()).containsExactlyInAnyOrder("docs", "help");
                });
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 2L, 31L})
    void invalidMultiplierFailsValidation(long multiplier) {
        contextRunner
                .withPropertyValues(
                        "app.code.multiplier=" + multiplier,
                        "app.code.offset=0",
                        "app.code.reserved=docs")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void multiplierAtOrAboveKeyspaceFailsValidation() {
        contextRunner
                .withPropertyValues(
                        "app.code.multiplier=" + ShortCodeGenerator.KEYSPACE,
                        "app.code.offset=0",
                        "app.code.reserved=docs")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void negativeOffsetFailsValidation() {
        contextRunner
                .withPropertyValues(
                        "app.code.multiplier=9132277",
                        "app.code.offset=-1",
                        "app.code.reserved=docs")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void offsetAtOrAboveKeyspaceFailsValidation() {
        contextRunner
                .withPropertyValues(
                        "app.code.multiplier=9132277",
                        "app.code.offset=" + ShortCodeGenerator.KEYSPACE,
                        "app.code.reserved=docs")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(CodeProperties.class)
    static class TestConfig {
    }
}
