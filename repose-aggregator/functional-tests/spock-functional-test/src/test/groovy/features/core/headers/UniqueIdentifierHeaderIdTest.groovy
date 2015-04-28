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
package features.core.headers

import framework.ReposeConfigurationProvider
import framework.ReposeValveLauncher
import framework.ReposeValveTest
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain
import org.rackspace.deproxy.Response
import spock.lang.Unroll

/**
 * Created by jennyvo on 4/17/15.
 *  Repose to create a unique identifier for each request that comes in and
 *  a) add that ID to the http headers that are passed to the origin service
 *      (where it can also be included in each log entry on the origin service related to that request),
 *  b) include that ID in the user access event for that request, and
 *  c) pass the ID to the logging filter
 */
class UniqueIdentifierHeaderIdTest extends ReposeValveTest {
    String charset = (('A'..'Z') + ('0'..'9')).join()
    static int originServicePort
    static int reposePort
    static String url
    static ReposeConfigurationProvider reposeConfigProvider

    def setupSpec() {
        deproxy = new Deproxy()
        originServicePort = properties.targetPort
        deproxy.addEndpoint(originServicePort)

        reposePort = properties.reposePort
        url = "http://localhost:${reposePort}"

        reposeConfigProvider = new ReposeConfigurationProvider(configDirectory, configTemplates)
        repose = new ReposeValveLauncher(
                reposeConfigProvider,
                properties.getReposeJar(),
                url,
                properties.getConfigDirectory(),
                reposePort
        )
        repose.enableDebug()

        def params = properties.getDefaultTemplateParams()

        reposeConfigProvider.cleanConfigDirectory()
        reposeConfigProvider.applyConfigs("common", params)
        reposeConfigProvider.applyConfigs("features/core/headers", params)

        repose.start(killOthersBeforeStarting: false,
                waitOnJmxAfterStarting: false)

        repose.waitForNon500FromUrl(url)
    }

    @Unroll("Request: #method")
    def "Repose will add the tracing header if not present"() {
        setup:
        def headers = [
                'Content-Length': '0',
                'Content-type'  : "application/xml",
                'Accept'        : "application/xml"
        ]
        when: "When make request along with other headers"

        MessageChain mc = deproxy.makeRequest(url: url, headers: headers)

        then:
        mc.handlings.size() == 1
        mc.handlings[0].request.headers.contains("Accept")
        mc.handlings[0].request.headers.contains("x-trans-id")
        //def uniqueid = mc.handlings[0].request.headers.getFirstValue("x-unique-id")

        where:
        method << ["GET", "POST"]
    }

    @Unroll("Request: #method already have unique id")
    def "Don't need to add the tracing header if comming request already have one"() {
        setup:
        def randomid = UUID.randomUUID().toString()
        def headers = [
                'Content-Length': '0',
                'Content-type'  : "application/xml",
                'Accept'        : "application/xml",
                'x-trans-id': randomid
        ]
        when: "When make request along with other headers"

        MessageChain mc = deproxy.makeRequest(url: url, headers: headers)

        then:
        mc.handlings.size() == 1
        mc.handlings[0].request.headers.contains("Accept")
        mc.handlings[0].request.headers.contains("x-trans-id")
        mc.handlings[0].request.headers.getFirstValue("x-trans-id") == randomid

        where:
        method << ["GET", "POST"]
    }

    def "Origin service http should include trace header id"() {
        setup:
        def headers = [
                'Content-Length': '0',
                'Content-type'  : "application/xml",
        ]

        when: "Set default handler from origin"
        MessageChain mc = deproxy.makeRequest(url: url, defaultHandler: { new Response(200, null, headers) })

        then:
        mc.handlings.size() == 1
        mc.receivedResponse.headers.contains("Content-Length")
        mc.receivedResponse.headers.contains("Content-type")
        mc.receivedResponse.headers.contains("x-trans-id")
    }
}
