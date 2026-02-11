package io.quarkiverse.qubit.deployment.analysis;

import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_TYPE_COUNT;
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.QUERY_TYPE_LIST;

import java.util.List;
import java.util.stream.Collectors;

import io.quarkiverse.qubit.deployment.analysis.LambdaAnalysisResult.SortExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

/** Fluent builder for constructing query hash strings. */
public final class HashBuilder {

    private static final String SEPARATOR = "|";

    private final StringBuilder builder = new StringBuilder();

    private HashBuilder() {
        // Use factory method
    }

    /** Creates a new HashBuilder. */
    public static HashBuilder create() {
        return new HashBuilder();
    }

    // ========== Expression Components ==========

    public HashBuilder expression(LambdaExpression expr) {
        if (expr != null) {
            appendSeparatorIfNeeded();
            builder.append(expr);
        }
        return this;
    }

    public HashBuilder where(LambdaExpression expr) {
        if (expr != null) {
            appendSeparatorIfNeeded();
            builder.append("WHERE=").append(expr);
        }
        return this;
    }

    public HashBuilder select(LambdaExpression expr) {
        if (expr != null) {
            appendSeparatorIfNeeded();
            builder.append("SELECT=").append(expr);
        }
        return this;
    }

    public HashBuilder sort(List<SortExpression> sortExpressions) {
        if (sortExpressions != null && !sortExpressions.isEmpty()) {
            appendSeparatorIfNeeded();
            builder.append("SORT=").append(buildSortString(sortExpressions));
        }
        return this;
    }

    public HashBuilder aggregation(LambdaExpression expr) {
        if (expr != null) {
            appendSeparatorIfNeeded();
            builder.append("AGG=").append(expr);
        }
        return this;
    }

    public HashBuilder aggregationType(String type) {
        if (type != null) {
            appendSeparatorIfNeeded();
            builder.append("TYPE=").append(type);
        }
        return this;
    }

    // ========== Join Components ==========

    public HashBuilder join(LambdaExpression expr) {
        appendSeparatorIfNeeded();
        builder.append("JOIN=");
        if (expr != null) {
            builder.append(expr);
        }
        return this;
    }

    public HashBuilder biWhere(LambdaExpression expr) {
        if (expr != null) {
            appendSeparatorIfNeeded();
            builder.append("BI_WHERE=").append(expr);
        }
        return this;
    }

    public HashBuilder biSelect(LambdaExpression expr) {
        if (expr != null) {
            appendSeparatorIfNeeded();
            builder.append("BI_SELECT=").append(expr);
        }
        return this;
    }

    public HashBuilder joinType(String type) {
        if (type != null) {
            appendSeparatorIfNeeded();
            builder.append("JOIN_TYPE=").append(type);
        }
        return this;
    }

    public HashBuilder selectJoined(boolean isSelectJoined) {
        if (isSelectJoined) {
            appendSeparatorIfNeeded();
            builder.append("SELECT_JOINED=true");
        }
        return this;
    }

    public HashBuilder joinProjection(boolean isJoinProjection) {
        if (isJoinProjection) {
            appendSeparatorIfNeeded();
            builder.append("JOIN_PROJECTION=true");
        }
        return this;
    }

    // ========== Group Components ==========

    public HashBuilder groupBy(LambdaExpression expr) {
        appendSeparatorIfNeeded();
        builder.append("GROUP_BY=");
        if (expr != null) {
            builder.append(expr);
        }
        return this;
    }

    public HashBuilder having(LambdaExpression expr) {
        if (expr != null) {
            appendSeparatorIfNeeded();
            builder.append("HAVING=").append(expr);
        }
        return this;
    }

    public HashBuilder groupSelect(LambdaExpression expr) {
        if (expr != null) {
            appendSeparatorIfNeeded();
            builder.append("GROUP_SELECT=").append(expr);
        }
        return this;
    }

    // ========== Query Type ==========

    public HashBuilder queryType(String type) {
        if (type != null) {
            appendSeparatorIfNeeded();
            builder.append("queryType=").append(type);
        }
        return this;
    }

    public HashBuilder queryType(boolean isCountQuery) {
        return queryType(isCountQuery ? QUERY_TYPE_COUNT : QUERY_TYPE_LIST);
    }

    // ========== Build Methods ==========

    /** Returns raw hash string (before MD5). */
    public String buildString() {
        return builder.toString();
    }

    /** Computes FNV-1a hash of built string. */
    public String buildHash() {
        return computeFnv1aHash(builder.toString());
    }

    // ========== Private Helpers ==========

    private void appendSeparatorIfNeeded() {
        if (!builder.isEmpty()) {
            builder.append(SEPARATOR);
        }
    }

    private static String buildSortString(List<SortExpression> sortExpressions) {
        return sortExpressions.stream()
                .map(s -> s.keyExtractor().toString() + s.direction().getSuffix())
                .collect(Collectors.joining(","));
    }

    // FNV-1a 64-bit constants
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Fast FNV-1a 64-bit hash - no MessageDigest overhead. Padded to 16 chars. */
    private static String computeFnv1aHash(String input) {
        long hash = FNV_OFFSET_BASIS;
        int length = input.length();
        for (int i = 0; i < length; i++) {
            hash ^= input.charAt(i);
            hash *= FNV_PRIME;
        }
        // Pad to 16 hex chars (use Long.toHexString then left-pad with zeros)
        String hex = Long.toHexString(hash);
        return "0".repeat(16 - hex.length()) + hex;
    }
}
