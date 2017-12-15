/*
 * _=_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=
 * Repose
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Copyright (C) 2010 - 2015 Rackspace US, Inc.
 * _-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_-_=_
 */
package features.core.config

import org.openrepose.framework.test.ReposeValveTest
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain
import org.rackspace.deproxy.Response
import spock.lang.Shared

import static javax.servlet.http.HttpServletResponse.SC_OK

class EnvironmentVariableElseTest extends ReposeValveTest {

    @Shared
    def ENV_KEY = "Howdy"
    @Shared
    def ENV_VAL = "BAD"

    def setupSpec() {
        deproxy = new Deproxy()
        deproxy.addEndpoint(port: properties.targetPort, name: "Origin Service")

        def params = properties.getDefaultTemplateParams()
        repose.configurationProvider.applyConfigs("common", params)
        repose.configurationProvider.applyConfigs("features/core/config/env-vars", params)
        repose.addToEnvironment("ENV_KEY", ENV_KEY)
        repose.addToEnvironment("ENV_VAL", ENV_VAL)
        repose.start()
        repose.waitForNon500FromUrl(reposeEndpoint)
    }

    def "Repose should add the Response header based on the environment variables"() {
        when: "send request"
        MessageChain mc = deproxy.makeRequest(
            [
                method        : 'GET',
                url           : reposeEndpoint,
                defaultHandler: { new Response(SC_OK) }
            ])

        then: "repose response"
        mc.receivedResponse.code as Integer == SC_OK
        mc.handlings[0].request.headers.contains("language")
        mc.handlings[0].request.headers.getFirstValue("language") == "groovy"
        mc.receivedResponse.headers.contains(ENV_KEY)
        mc.receivedResponse.headers.getFirstValue(ENV_KEY) == "???"
    }
}
