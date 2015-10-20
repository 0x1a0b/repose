package org.openrepose.core.services.ratelimit

import org.openrepose.commons.config.resource.impl.BufferedURLConfigurationResource
import org.openrepose.core.services.ratelimit.config.RateLimitingConfiguration
import org.openrepose.commons.config.parser.jaxb.JaxbConfigurationParser
import spock.lang.Specification

class RateLimitConfigValidationTest extends Specification {

    def rateLimitingXSD = getClass().getResource("/META-INF/schema/config/rate-limiting-configuration.xsd")

    private RateLimitingConfiguration configResource(String resource)  {
        def parser = JaxbConfigurationParser.getXmlConfigurationParser(
                RateLimitingConfiguration.class,
                rateLimitingXSD,
                this.getClass().getClassLoader())

        def configResource = new BufferedURLConfigurationResource(this.getClass().getResource(resource))

        parser.read(configResource)
    }

    def "duplicate limit IDs across limit groups should NOT validate" () {
        given: "a rate limiting configuration with duplicate limit IDs in different groups"
        def invalidRateLimit = "/invalid-rate-limiting-config.xml"

        when: "validation is attempted"
        def loadedConfig = configResource(invalidRateLimit)

        then: "that validation will fail"
        //TODO: an exception
        loadedConfig == null
    }
}
