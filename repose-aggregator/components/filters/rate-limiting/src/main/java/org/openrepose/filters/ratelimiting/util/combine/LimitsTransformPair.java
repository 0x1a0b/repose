package org.openrepose.filters.ratelimiting.util.combine;

import org.openrepose.core.services.ratelimit.config.RateLimitList;

import java.io.InputStream;

/**
 *
 * @author zinic
 */
public class LimitsTransformPair {

    private final InputStream is;
    private final RateLimitList rll;

    public LimitsTransformPair(InputStream is, RateLimitList rll) {
        this.is = is;
        this.rll = rll;
    }

    public InputStream getInputStream() {
        return is;
    }

    public RateLimitList getRateLimitList() {
        return rll;
    }
}
