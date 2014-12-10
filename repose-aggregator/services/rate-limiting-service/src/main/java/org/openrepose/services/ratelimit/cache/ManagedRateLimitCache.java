package org.openrepose.services.ratelimit.cache;

import org.apache.commons.lang3.tuple.Pair;
import org.openrepose.core.services.datastore.Datastore;
import org.openrepose.services.ratelimit.cache.util.TimeUnitConverter;
import org.openrepose.services.ratelimit.config.ConfiguredRatelimit;
import org.openrepose.services.ratelimit.config.TimeUnit;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* Responsible for updating and querying ratelimits in cache */
public class ManagedRateLimitCache implements RateLimitCache {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ManagedRateLimitCache.class);

    private final Datastore datastore;

    public ManagedRateLimitCache(Datastore datastore) {
        this.datastore = datastore;
    }

    @Override
    public Map<String, CachedRateLimit> getUserRateLimits(String user) {
        final Map<String, CachedRateLimit> accountRateLimitMap = getUserRateLimitMap(user);

        return Collections.unmodifiableMap(accountRateLimitMap);
    }

    private Map<String, CachedRateLimit> getUserRateLimitMap(String user) {
        final Serializable element = datastore.get(user);

        return (element == null) ? new HashMap<String, CachedRateLimit>() : ((UserRateLimit) element).getLimitMap();
    }

    @Override
    public NextAvailableResponse updateLimit(String user, List<Pair<String, ConfiguredRatelimit>> matchingLimits, TimeUnit largestUnit, int datastoreWarnLimit) throws IOException {
        UserRateLimit patchResult = (UserRateLimit) datastore.patch(user, new UserRateLimit.Patch(matchingLimits), 1, TimeUnitConverter.fromSchemaTypeToConcurrent(largestUnit));

        if (patchResult.getLimitMap().keySet().size() >= datastoreWarnLimit) {
            LOG.warn("Large amount of limits recorded.  Repose Rate Limited may be misconfigured, keeping track of rate limits for user: " + user + ". Please review capture groups in your rate limit configuration.  If using clustered datastore, you may experience network latency.");
        }

        return new NextAvailableResponse(patchResult.getLowestLimit());
    }
}
