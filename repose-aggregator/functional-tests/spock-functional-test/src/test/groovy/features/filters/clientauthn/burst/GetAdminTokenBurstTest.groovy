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
package features.filters.clientauthn.burst

import framework.ReposeValveTest
import framework.mocks.MockIdentityService
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain
import org.rackspace.deproxy.Request
import org.rackspace.deproxy.Response

class GetAdminTokenBurstTest extends ReposeValveTest {

    def static originEndpoint
    def static identityEndpoint
    static MockIdentityService fakeIdentityService

    def setupSpec() {
        deproxy = new Deproxy()


        repose.configurationProvider.applyConfigs("common", properties.defaultTemplateParams)
        repose.configurationProvider.applyConfigs("features/filters/clientauthn/common", properties.defaultTemplateParams)
        repose.start()

        originEndpoint = deproxy.addEndpoint(properties.targetPort, 'origin service')
        fakeIdentityService = new MockIdentityService(properties.identityPort, properties.targetPort)
        identityEndpoint = deproxy.addEndpoint(properties.identityPort,
                'identity service', null, fakeIdentityService.handler)

        Map header1 = ['X-Auth-Token': fakeIdentityService.client_token]
        Map acceptXML = ["accept": "application/xml"]

        def missingResponseErrorHandler = { Request request ->
            def headers = request.getHeaders()

            if (!headers.contains("X-Auth-Token")) {
                return new Response(500, "INTERNAL SERVER ERROR", null, "MISSING AUTH TOKEN")
            }


            return new Response(200, "OK", header1 + acceptXML)

        }

        deproxy.defaultHandler = missingResponseErrorHandler
    }


    def cleanupSpec() {
        if (deproxy) {
            deproxy.shutdown()
        }
        repose.stop()
    }

    /**
     * This test occasionally fails because threading problems
     * https://repose.atlassian.net/browse/REP-558
     * @return
     */
    def "under heavy load should only retrieve admin token once"() {

        given:
        fakeIdentityService.resetCounts()
        List<Thread> clientThreads = new ArrayList<Thread>()

        DateTimeFormatter fmt = DateTimeFormat
                .forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
                .withLocale(Locale.US)
                .withZone(DateTimeZone.UTC);

        def missingAuthResponse = false
        def missingAuthHeader = false

        (1..numClients).each {
            threadNum ->
                Map header1 = ['X-Auth-Token': UUID.randomUUID().leastSignificantBits.toString()]
                def thread = Thread.start {
                    (1..callsPerClient).each {
                        def messageChain = deproxy.makeRequest(url: reposeEndpoint, method: 'GET', headers: header1)

                        if (messageChain.receivedResponse.code.equalsIgnoreCase("500")) {
                            println messageChain.receivedResponse.body
                            if (messageChain.orphanedHandlings.size() > 0) {
                                println messageChain.orphanedHandlings[0].request.body
                                println messageChain.orphanedHandlings[0].response.body
                            }
                            missingAuthResponse = true
                        } else {
                            def sentToOrigin = ((MessageChain) messageChain).getHandlings()[0]
                            if (sentToOrigin.request.headers.findAll("x-roles").empty) {
                                println sentToOrigin.request.headers
                                missingAuthHeader = true
                            }
                        }
                    }
                }
                clientThreads.add(thread)
        }

        when:
        clientThreads*.join()

        then:
        fakeIdentityService.generateTokenCount == 1

        and:
        missingAuthHeader == false

        and:
        missingAuthResponse == false

        where:
        numClients | callsPerClient
        50         | 10


    }

}
