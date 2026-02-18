package io.quarkiverse.qubit.it.inheritance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.quarkiverse.qubit.it.Animal;
import io.quarkiverse.qubit.it.Cat;
import io.quarkiverse.qubit.it.Dog;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests for TREAT (entity inheritance) queries.
 * Tests both pattern matching and explicit cast forms.
 */
@QuarkusTest
@DisplayName("TREAT / instanceof queries")
class TreatIT {

    // ─── instanceof Type Filtering ──────────────────────────────────

    @Test
    @DisplayName("instanceof Dog filters to dogs only")
    void instanceofDog_filtersToDogs() {
        var results = Animal.where((Animal a) -> a instanceof Dog).toList();
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allSatisfy(a -> assertThat(a).isInstanceOf(Dog.class));
    }

    @Test
    @DisplayName("instanceof Cat filters to cats only")
    void instanceofCat_filtersToCats() {
        var results = Animal.where((Animal a) -> a instanceof Cat).toList();
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allSatisfy(a -> assertThat(a).isInstanceOf(Cat.class));
    }

    // ─── Pattern Matching with Field Access ──────────────────────────

    @Test
    @DisplayName("pattern match: Dog breed filter")
    void patternMatch_dogBreedFilter() {
        var results = Animal.where((Animal a) -> a instanceof Dog d && d.breed.equals("Labrador")).toList();
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allSatisfy(a -> {
                    assertThat(a).isInstanceOf(Dog.class);
                    assertThat(((Dog) a).getBreed()).isEqualTo("Labrador");
                });
    }

    @Test
    @DisplayName("pattern match: Dog trained filter")
    void patternMatch_dogTrainedFilter() {
        var results = Animal.where((Animal a) -> a instanceof Dog d && d.trained).toList();
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allSatisfy(a -> {
                    assertThat(a).isInstanceOf(Dog.class);
                    assertThat(((Dog) a).isTrained()).isTrue();
                });
    }

    @Test
    @DisplayName("pattern match: Cat indoor filter")
    void patternMatch_catIndoorFilter() {
        var results = Animal.where((Animal a) -> a instanceof Cat c && c.indoor).toList();
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allSatisfy(a -> {
                    assertThat(a).isInstanceOf(Cat.class);
                    assertThat(((Cat) a).isIndoor()).isTrue();
                });
    }

    // ─── Explicit Cast with Field Access ─────────────────────────────

    @Test
    @DisplayName("explicit cast: Dog breed filter")
    void explicitCast_dogBreedFilter() {
        var results = Animal.where((Animal a) -> a instanceof Dog && ((Dog) a).breed.equals("Labrador")).toList();
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allSatisfy(a -> {
                    assertThat(a).isInstanceOf(Dog.class);
                    assertThat(((Dog) a).getBreed()).isEqualTo("Labrador");
                });
    }

    @Test
    @DisplayName("explicit cast: Cat color filter")
    void explicitCast_catColorFilter() {
        var results = Animal.where((Animal a) -> a instanceof Cat && ((Cat) a).color.equals("black")).toList();
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allSatisfy(a -> {
                    assertThat(a).isInstanceOf(Cat.class);
                    assertThat(((Cat) a).getColor()).isEqualTo("black");
                });
    }

    // ─── Combined Parent + Subclass Fields ───────────────────────────

    @Test
    @DisplayName("pattern match with parent field: Dog breed and weight")
    void patternMatch_withParentField() {
        var results = Animal.where((Animal a) -> a instanceof Dog d && d.breed.equals("Labrador") && a.weight > 20)
                .toList();
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allSatisfy(a -> {
                    assertThat(a).isInstanceOf(Dog.class);
                    assertThat(((Dog) a).getBreed()).isEqualTo("Labrador");
                    assertThat(a.getWeight()).isGreaterThan(20);
                });
    }

    @Test
    @DisplayName("pattern match with captured variable")
    void patternMatch_withCapturedVariable() {
        String targetBreed = "Golden Retriever";
        var results = Animal.where((Animal a) -> a instanceof Dog d && d.breed.equals(targetBreed)).toList();
        assertThat(results)
                .hasSizeGreaterThan(0)
                .allSatisfy(a -> {
                    assertThat(a).isInstanceOf(Dog.class);
                    assertThat(((Dog) a).getBreed()).isEqualTo(targetBreed);
                });
    }

    // ─── Negative Cases ──────────────────────────────────────────────

    @Test
    @DisplayName("instanceof with no matches returns empty")
    void instanceof_noMatches_returnsEmpty() {
        var results = Animal.where((Animal a) -> a instanceof Dog d && d.breed.equals("Nonexistent Breed")).toList();
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("parent-only query returns all animals")
    void parentQuery_returnsAllAnimals() {
        var allAnimals = Animal.where((Animal a) -> a.vaccinated).toList();
        assertThat(allAnimals).hasSizeGreaterThan(0);
        assertThat(allAnimals.stream().anyMatch(a -> a instanceof Dog)).isTrue();
        assertThat(allAnimals.stream().anyMatch(a -> a instanceof Cat)).isTrue();
    }
}
