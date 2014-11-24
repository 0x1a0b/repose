package org.openrepose.filters.clientauth.common

import org.openrepose.common.auth.AuthGroup
import org.openrepose.common.auth.AuthGroups
import org.openrepose.common.auth.AuthToken
import org.openrepose.commons.utils.regex.ExtractorResult
import org.openrepose.commons.utils.servlet.http.ReadableHttpServletResponse
import org.openrepose.core.filter.logic.FilterDirector
import spock.lang.Specification

class AuthenticationHandlerTest extends Specification {

    Configurables configurables = Mock()
    EndpointsConfiguration endpointsConfiguration = Mock()
    AuthenticationHandler authenticationHandler

    def "when using the safeEndpointsTtl method, it should not allow for numbers larger than the integer limit"() {
        given:
        configurables.getEndpointsConfiguration() >> endpointsConfiguration
        endpointsConfiguration.getCacheTimeout() >> Long.valueOf(Integer.MAX_VALUE) + 1
        authenticationHandler = new TestableAuthenticationHandler(configurables)

        when:
        def i = authenticationHandler.safeEndpointsTtl()

        then:
        i == Integer.MAX_VALUE
    }

    def "when checking the cache for endpoints, should return null if not present"() {
        given:
        configurables.getEndpointsConfiguration() >> endpointsConfiguration
        endpointsConfiguration.getCacheTimeout() >> Long.valueOf(Integer.MAX_VALUE) + 1
        authenticationHandler = new TestableAuthenticationHandler(configurables)
        String token = null;

        when:
        def nil = authenticationHandler.checkEndpointsCache(token)

        then:
        nil == null;
    }

    class TestableAuthenticationHandler extends AuthenticationHandler {

        TestableAuthenticationHandler(Configurables configurables) {
            super(configurables, null, null, null, null, null)
        }

        Integer safeEndpointsTtl() {
            super.safeEndpointsTtl()
        }

        @Override
        protected AuthToken validateToken(ExtractorResult<String> account, String token) {
            return null
        }

        @Override
        protected AuthGroups getGroups(String group) {
            return null
        }

        @Override
        protected String getEndpointsBase64(String token, EndpointsConfiguration endpointsConfiguration) {
            return null
        }

        @Override
        protected FilterDirector processResponse(ReadableHttpServletResponse response) {
            return null
        }

        @Override
        protected void setFilterDirectorValues(String authToken, AuthToken cachableToken, Boolean delegatable,
                                               double delegableQuality, String delegationMessage,
                                               FilterDirector filterDirector, String extractedResult,
                                               List<AuthGroup> groups, String endpointsBase64, boolean tenanted,
                                               boolean sendAllTenantIds) {}

        protected String checkEndpointsCache(String token) {
            super.checkEndpointsCache()
        }

    }

}
