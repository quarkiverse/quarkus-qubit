package io.quarkiverse.qubit.it.string;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.Qubit;
import io.quarkiverse.qubit.it.Person;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for {@code Qubit.like()} and {@code Qubit.notLike()} pattern matching.
 *
 * <p>
 * Uses the standard test data from import.sql where all persons have
 * emails ending in {@code @example.com}.
 */
@QuarkusTest
@DisplayName("Qubit.like() pattern matching")
class LikePatternIT {

    @Test
    @DisplayName("LIKE pattern matches emails with domain")
    void likePattern_matchesEmails() {
        var results = Person.where((Person p) -> Qubit.like(p.email, "%@%.com")).toList();
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allSatisfy(p -> assertThat(p.getEmail()).containsPattern("@.*\\.com"));
    }

    @Test
    @DisplayName("NOT LIKE excludes matching rows")
    void notLikePattern_excludesMatches() {
        var all = Person.where((Person p) -> p.email != null && !p.email.isEmpty()).toList();
        var filtered = Person.where((Person p) -> Qubit.notLike(p.email, "%@example.com")).toList();
        assertThat(filtered).hasSizeLessThan(all.size());
    }

    @Test
    @DisplayName("LIKE with captured variable pattern")
    void likeCaptured_matchesPattern() {
        String pattern = "%doe%";
        var results = Person.where((Person p) -> Qubit.like(p.email, pattern)).toList();
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allSatisfy(p -> assertThat(p.getEmail().toLowerCase()).contains("doe"));
    }

    @Test
    @DisplayName("LIKE with single-char wildcard")
    void likeSingleChar_matchesPattern() {
        // Matches "John" (J + any char + hn)
        var results = Person.where((Person p) -> Qubit.like(p.firstName, "J_hn")).toList();
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allSatisfy(p -> {
                    String name = p.getFirstName();
                    assertThat(name).hasSize(4);
                    assertThat(name).startsWith("J");
                    assertThat(name).endsWith("hn");
                });
    }

    @Test
    @DisplayName("LIKE combined with other conditions")
    void likeCombined_worksWithAnd() {
        var results = Person.where((Person p) -> Qubit.like(p.email, "%@%.com") && p.active).toList();
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allSatisfy(p -> {
                    assertThat(p.getEmail()).containsPattern("@.*\\.com");
                    assertThat(p.isActive()).isTrue();
                });
    }

    @Test
    @DisplayName("LIKE with no matches returns empty")
    void likeNoMatches_returnsEmpty() {
        var results = Person.where((Person p) -> Qubit.like(p.email, "%@nonexistent-domain-xyz.org")).toList();
        assertThat(results).isEmpty();
    }
}
