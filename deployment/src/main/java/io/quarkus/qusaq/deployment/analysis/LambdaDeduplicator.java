package io.quarkus.qusaq.deployment.analysis;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.qusaq.deployment.LambdaExpression;
import io.quarkus.qusaq.deployment.QusaqProcessor;
import org.jboss.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deduplicates lambda expressions to reuse executors and reduce bytecode size.
 */
public class LambdaDeduplicator {

    private static final Logger log = Logger.getLogger(LambdaDeduplicator.class);

    private final Map<String, String> lambdaHashToExecutor = new HashMap<>();

    /**
     * Computes MD5 hash for lambda expression and query type.
     */
    public String computeLambdaHash(LambdaExpression expression, boolean isCountQuery, boolean isProjectionQuery) {
        String queryType = CallSiteProcessor.getQueryType(isCountQuery, !isProjectionQuery, isProjectionQuery);
        String astString = expression.toString() + "|queryType=" + queryType;

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(astString.getBytes(UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(astString.hashCode());
        }
    }

    /**
     * Computes MD5 hash for combined where + select query (Phase 2.2).
     */
    public String computeCombinedHash(LambdaExpression predicateExpression,
                                     LambdaExpression projectionExpression,
                                     boolean isCountQuery) {
        String queryType = CallSiteProcessor.getQueryType(isCountQuery, true, true);
        String astString = "WHERE=" + predicateExpression.toString() +
                          "|SELECT=" + projectionExpression.toString() +
                          "|queryType=" + queryType;

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(astString.getBytes(UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(astString.hashCode());
        }
    }

    /**
     * Returns true if lambda is duplicate and reuses existing executor.
     * Query type information is already encoded in the lambdaHash parameter.
     */
    public boolean handleDuplicateLambda(
            String callSiteId,
            String lambdaHash,
            boolean isCountQuery,
            int capturedVarCount,
            AtomicInteger deduplicatedCount,
            BuildProducer<QusaqProcessor.QueryTransformationBuildItem> queryTransformations) {

        if (lambdaHashToExecutor.containsKey(lambdaHash)) {
            String existingExecutor = lambdaHashToExecutor.get(lambdaHash);
            log.debugf("Deduplicated lambda at %s (reusing %s)", callSiteId, existingExecutor);
            deduplicatedCount.incrementAndGet();

            queryTransformations.produce(
                    new QusaqProcessor.QueryTransformationBuildItem(callSiteId, existingExecutor,
                            Object.class, isCountQuery, capturedVarCount));
            return true;
        }

        return false;
    }

    /**
     * Registers executor class for lambda hash.
     */
    public void registerExecutor(String lambdaHash, String executorClassName) {
        lambdaHashToExecutor.put(lambdaHash, executorClassName);
    }

    /**
     * Returns number of unique lambda expressions.
     */
    public int getUniqueCount() {
        return lambdaHashToExecutor.size();
    }
}
