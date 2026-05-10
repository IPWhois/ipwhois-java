package io.ipwhois;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of a {@link IPWhois#bulkLookup(List)} call.
 * <p>
 * The bulk endpoint has two outcomes that need to be distinguishable: either
 * the whole call succeeded and you get a list of per-IP maps, or the whole
 * batch failed (network down, bad API key, rate limit, invalid argument, …)
 * and you get a single error map. This wrapper makes both reachable in a
 * type-safe way:
 *
 * <ul>
 *   <li>{@link #isSuccess()} — whether the bulk call as a whole succeeded.
 *       Note that individual per-IP entries may still have
 *       {@code "success": false} — check each row.</li>
 *   <li>{@link #getResults()} — the list of per-IP result maps when the bulk
 *       call succeeded. Returns an empty list when the whole call failed.</li>
 *   <li>{@link #getError()} — the whole-batch error map when the call failed.
 *       Returns {@code null} when the call succeeded.</li>
 * </ul>
 *
 * Just like the rest of the library, this class never throws — every failure
 * is reachable through {@link #getError()}.
 */
public final class BulkResult {

    private final boolean success;
    private final List<Map<String, Object>> results;
    private final Map<String, Object> error;

    private BulkResult(boolean success,
                       List<Map<String, Object>> results,
                       Map<String, Object> error) {
        this.success = success;
        this.results = results;
        this.error = error;
    }

    static BulkResult success(List<Map<String, Object>> results) {
        return new BulkResult(true, Collections.unmodifiableList(results), null);
    }

    static BulkResult error(Map<String, Object> error) {
        return new BulkResult(false, null, error);
    }

    /**
     * Whether the bulk call as a whole succeeded. Note that individual
     * per-IP entries may still have {@code "success": false} — check each row.
     *
     * @return {@code true} if the call succeeded as a whole, {@code false} if it failed
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * The list of per-IP result maps when the bulk call succeeded.
     * Returns an empty list when the whole call failed (in which case
     * {@link #getError()} is non-null).
     *
     * @return per-IP result maps, in the same order as the input list;
     *         never {@code null}, but empty when the call failed as a whole
     */
    public List<Map<String, Object>> getResults() {
        return results != null ? results : Collections.emptyList();
    }

    /**
     * The whole-batch error map when the call failed (network down, bad API
     * key, rate limit, invalid argument, …). Contains {@code "success": false},
     * a human-readable {@code "message"}, and an {@code "error_type"} of
     * {@code "api"}, {@code "network"}, {@code "environment"}, or
     * {@code "invalid_argument"}.
     * <p>
     * Returns {@code null} when the call succeeded.
     *
     * @return the whole-batch error map, or {@code null} if the call succeeded
     */
    public Map<String, Object> getError() {
        return error;
    }
}
