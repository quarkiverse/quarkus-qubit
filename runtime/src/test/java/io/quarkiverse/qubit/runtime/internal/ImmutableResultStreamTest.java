package io.quarkiverse.qubit.runtime.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.NoResultException;
import jakarta.persistence.NonUniqueResultException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link ImmutableResultStream}.
 * <p>
 * Tests the immutable stream wrapper for pre-computed projection results,
 * covering supported operations (skip, limit, distinct, terminal ops)
 * and unsupported operations (where, select, sort, join, group, aggregation).
 */
@DisplayName("ImmutableResultStream")
class ImmutableResultStreamTest {

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("creates defensive copy of input list")
        void createsDefensiveCopy() {
            List<String> original = new ArrayList<>(List.of("a", "b", "c"));
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(original);

            // Modify original list
            original.add("d");

            // Stream should not be affected
            assertThat(stream.toList()).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("handles null input as empty list")
        void handlesNullInput() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(null);

            assertThat(stream.toList()).isEmpty();
            assertThat(stream.count()).isZero();
        }

        @Test
        @DisplayName("uses default context when not specified")
        void usesDefaultContext() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a"));

            // Verify default context by checking exception message
            assertThatThrownBy(() -> stream.where(s -> true))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("projection");
        }

        @Test
        @DisplayName("uses custom context when specified")
        void usesCustomContext() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a"), "join");

            assertThatThrownBy(() -> stream.where(s -> true))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("join");
        }
    }

    @Nested
    @DisplayName("skip()")
    class SkipTests {

        @Test
        @DisplayName("skips first n elements")
        void skipsFirstNElements() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a", "b", "c", "d"));

            List<String> result = stream.skip(2).toList();

            assertThat(result).containsExactly("c", "d");
        }

        @Test
        @DisplayName("returns empty for skip >= size")
        void returnsEmptyForSkipGreaterThanOrEqualSize() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a", "b"));

            assertThat(stream.skip(2).toList()).isEmpty();
            assertThat(stream.skip(5).toList()).isEmpty();
        }

        @Test
        @DisplayName("returns all for skip(0)")
        void returnsAllForSkipZero() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a", "b", "c"));

            assertThat(stream.skip(0).toList()).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("throws for negative skip count")
        void throwsForNegativeSkipCount() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a"));

            assertThatThrownBy(() -> stream.skip(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("skip count");
        }

        @Test
        @DisplayName("creates independent copy (GC-friendly)")
        void createsIndependentCopy() {
            List<String> original = new ArrayList<>(List.of("a", "b", "c", "d"));
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(original);

            // The skipped stream should have an independent copy
            List<String> skippedResult = stream.skip(1).toList();
            assertThat(skippedResult).containsExactly("b", "c", "d");
        }
    }

    @Nested
    @DisplayName("limit()")
    class LimitTests {

        @Test
        @DisplayName("limits to first n elements")
        void limitsToFirstNElements() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a", "b", "c", "d"));

            List<String> result = stream.limit(2).toList();

            assertThat(result).containsExactly("a", "b");
        }

        @Test
        @DisplayName("returns all for limit >= size")
        void returnsAllForLimitGreaterThanOrEqualSize() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a", "b"));

            assertThat(stream.limit(2).toList()).containsExactly("a", "b");
            assertThat(stream.limit(5).toList()).containsExactly("a", "b");
        }

        @Test
        @DisplayName("returns empty for limit(0)")
        void returnsEmptyForLimitZero() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a", "b", "c"));

            assertThat(stream.limit(0).toList()).isEmpty();
        }

        @Test
        @DisplayName("throws for negative limit count")
        void throwsForNegativeLimitCount() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a"));

            assertThatThrownBy(() -> stream.limit(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("limit count");
        }

        @Test
        @DisplayName("creates independent copy (GC-friendly)")
        void createsIndependentCopy() {
            List<String> original = new ArrayList<>(List.of("a", "b", "c", "d"));
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(original);

            // The limited stream should have an independent copy
            List<String> limitedResult = stream.limit(2).toList();
            assertThat(limitedResult).containsExactly("a", "b");
        }
    }

    @Nested
    @DisplayName("distinct()")
    class DistinctTests {

        @Test
        @DisplayName("removes duplicates")
        void removesDuplicates() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a", "b", "a", "c", "b"));

            List<String> result = stream.distinct().toList();

            assertThat(result).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("preserves order of first occurrences")
        void preservesOrderOfFirstOccurrences() {
            ImmutableResultStream<Integer> stream = new ImmutableResultStream<>(List.of(3, 1, 2, 1, 3, 2));

            List<Integer> result = stream.distinct().toList();

            assertThat(result).containsExactly(3, 1, 2);
        }

        @Test
        @DisplayName("handles list without duplicates")
        void handlesListWithoutDuplicates() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a", "b", "c"));

            assertThat(stream.distinct().toList()).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("handles empty list")
        void handlesEmptyList() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of());

            assertThat(stream.distinct().toList()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Terminal operations")
    class TerminalOperationTests {

        @Nested
        @DisplayName("count()")
        class CountTests {

            @Test
            @DisplayName("returns size of list")
            void returnsSizeOfList() {
                ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a", "b", "c"));

                assertThat(stream.count()).isEqualTo(3);
            }

            @Test
            @DisplayName("returns 0 for empty list")
            void returnsZeroForEmptyList() {
                ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of());

                assertThat(stream.count()).isZero();
            }
        }

        @Nested
        @DisplayName("toList()")
        class ToListTests {

            @Test
            @DisplayName("returns copy of results")
            void returnsCopyOfResults() {
                List<String> original = List.of("a", "b", "c");
                ImmutableResultStream<String> stream = new ImmutableResultStream<>(original);

                List<String> result = stream.toList();

                assertThat(result).containsExactly("a", "b", "c");
                assertThat(result).isNotSameAs(original);
            }

            @Test
            @DisplayName("returns mutable list")
            void returnsMutableList() {
                ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a", "b"));

                List<String> result = stream.toList();
                result.add("c");

                assertThat(result).containsExactly("a", "b", "c");
            }
        }

        @Nested
        @DisplayName("getSingleResult()")
        class GetSingleResultTests {

            @Test
            @DisplayName("returns single element")
            void returnsSingleElement() {
                ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("only"));

                assertThat(stream.getSingleResult()).isEqualTo("only");
            }

            @Test
            @DisplayName("throws NoResultException for empty list")
            void throwsNoResultExceptionForEmptyList() {
                ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of());

                assertThatThrownBy(stream::getSingleResult)
                        .isInstanceOf(NoResultException.class)
                        .hasMessageContaining("none");
            }

            @Test
            @DisplayName("throws NonUniqueResultException for multiple elements")
            void throwsNonUniqueResultExceptionForMultipleElements() {
                ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a", "b"));

                assertThatThrownBy(stream::getSingleResult)
                        .isInstanceOf(NonUniqueResultException.class)
                        .hasMessageContaining("2");
            }
        }

        @Nested
        @DisplayName("findFirst()")
        class FindFirstTests {

            @Test
            @DisplayName("returns first element wrapped in Optional")
            void returnsFirstElementWrappedInOptional() {
                ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("first", "second"));

                assertThat(stream.findFirst()).contains("first");
            }

            @Test
            @DisplayName("returns empty Optional for empty list")
            void returnsEmptyOptionalForEmptyList() {
                ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of());

                assertThat(stream.findFirst()).isEmpty();
            }
        }

        @Nested
        @DisplayName("exists()")
        class ExistsTests {

            @Test
            @DisplayName("returns true for non-empty list")
            void returnsTrueForNonEmptyList() {
                ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a"));

                assertThat(stream.exists()).isTrue();
            }

            @Test
            @DisplayName("returns false for empty list")
            void returnsFalseForEmptyList() {
                ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of());

                assertThat(stream.exists()).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("Unsupported operations")
    class UnsupportedOperationTests {

        private final ImmutableResultStream<String> stream = new ImmutableResultStream<>(List.of("a"), "test-context");

        @Test
        @DisplayName("where() throws UnsupportedOperationException")
        void whereThrowsUnsupportedOperationException() {
            assertThatThrownBy(() -> stream.where(s -> true))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("filter")
                    .hasMessageContaining("test-context");
        }

        @Test
        @DisplayName("select() throws UnsupportedOperationException")
        void selectThrowsUnsupportedOperationException() {
            assertThatThrownBy(() -> stream.select(s -> s))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("project")
                    .hasMessageContaining("test-context");
        }

        @Test
        @DisplayName("sortedBy() throws UnsupportedOperationException")
        void sortedByThrowsUnsupportedOperationException() {
            assertThatThrownBy(() -> stream.sortedBy(s -> s))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("sort")
                    .hasMessageContaining("test-context");
        }

        @Test
        @DisplayName("sortedDescendingBy() throws UnsupportedOperationException")
        void sortedDescendingByThrowsUnsupportedOperationException() {
            assertThatThrownBy(() -> stream.sortedDescendingBy(s -> s))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("sort")
                    .hasMessageContaining("test-context");
        }

        @Test
        @DisplayName("min() throws UnsupportedOperationException")
        void minThrowsUnsupportedOperationException() {
            assertThatThrownBy(() -> stream.min(s -> s))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("aggregate");
        }

        @Test
        @DisplayName("max() throws UnsupportedOperationException")
        void maxThrowsUnsupportedOperationException() {
            assertThatThrownBy(() -> stream.max(s -> s))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("aggregate");
        }

        @Test
        @DisplayName("sumInteger() throws UnsupportedOperationException")
        void sumIntegerThrowsUnsupportedOperationException() {
            ImmutableResultStream<Integer> intStream = new ImmutableResultStream<>(List.of(1));

            assertThatThrownBy(() -> intStream.sumInteger(i -> i))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("aggregate");
        }

        @Test
        @DisplayName("sumLong() throws UnsupportedOperationException")
        void sumLongThrowsUnsupportedOperationException() {
            ImmutableResultStream<Long> longStream = new ImmutableResultStream<>(List.of(1L));

            assertThatThrownBy(() -> longStream.sumLong(l -> l))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("aggregate");
        }

        @Test
        @DisplayName("sumDouble() throws UnsupportedOperationException")
        void sumDoubleThrowsUnsupportedOperationException() {
            ImmutableResultStream<Double> doubleStream = new ImmutableResultStream<>(List.of(1.0));

            assertThatThrownBy(() -> doubleStream.sumDouble(d -> d))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("aggregate");
        }

        @Test
        @DisplayName("avg() throws UnsupportedOperationException")
        void avgThrowsUnsupportedOperationException() {
            ImmutableResultStream<Integer> intStream = new ImmutableResultStream<>(List.of(1));

            assertThatThrownBy(() -> intStream.avg(i -> i))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("aggregate");
        }

        @Test
        @DisplayName("join() throws UnsupportedOperationException")
        void joinThrowsUnsupportedOperationException() {
            assertThatThrownBy(() -> stream.join(s -> List.of()))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("join");
        }

        @Test
        @DisplayName("leftJoin() throws UnsupportedOperationException")
        void leftJoinThrowsUnsupportedOperationException() {
            assertThatThrownBy(() -> stream.leftJoin(s -> List.of()))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("join");
        }

        @Test
        @DisplayName("groupBy() throws UnsupportedOperationException")
        void groupByThrowsUnsupportedOperationException() {
            assertThatThrownBy(() -> stream.groupBy(s -> s))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("group");
        }
    }

    @Nested
    @DisplayName("Chained operations")
    class ChainedOperationTests {

        @Test
        @DisplayName("skip and limit can be chained")
        void skipAndLimitCanBeChained() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(
                    List.of("a", "b", "c", "d", "e"));

            List<String> result = stream.skip(1).limit(3).toList();

            assertThat(result).containsExactly("b", "c", "d");
        }

        @Test
        @DisplayName("distinct and limit can be chained")
        void distinctAndLimitCanBeChained() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(
                    List.of("a", "b", "a", "c", "b", "d"));

            List<String> result = stream.distinct().limit(2).toList();

            assertThat(result).containsExactly("a", "b");
        }

        @Test
        @DisplayName("skip, distinct, and limit can be chained")
        void skipDistinctAndLimitCanBeChained() {
            ImmutableResultStream<String> stream = new ImmutableResultStream<>(
                    List.of("a", "b", "a", "c", "b", "d", "e"));

            List<String> result = stream.skip(1).distinct().limit(3).toList();

            // After skip(1): ["b", "a", "c", "b", "d", "e"]
            // After distinct: ["b", "a", "c", "d", "e"]
            // After limit(3): ["b", "a", "c"]
            assertThat(result).containsExactly("b", "a", "c");
        }
    }
}
