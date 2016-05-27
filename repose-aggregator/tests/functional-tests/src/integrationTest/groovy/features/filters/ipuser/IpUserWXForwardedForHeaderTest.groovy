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
package features.filters.ipuser

import framework.ReposeValveTest
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain

/**
 * Created by jennyvo on 4/26/16.
 *  REP-3838 fix using x-forward-for as x-pp-user
 */
class IpUserWXForwardedForHeaderTest extends ReposeValveTest {

    def setupSpec() {
        deproxy = new Deproxy()
        //Just set up a simple endpoint
        deproxy.addEndpoint(properties.targetPort, 'origin service')
        def params = properties.defaultTemplateParams
        repose.configurationProvider.applyConfigs("common", params)
        repose.configurationProvider.applyConfigs("features/filters/ipuser", params)
        repose.start()
    }

    def cleanupSpec() {
        if (deproxy) {
            deproxy.shutdown()
        }
        if (repose) {
            repose.stop()
        }
    }

    def "classifying a request by its IP"() {
        when: "Request is sent through repose"
        MessageChain mc = deproxy.makeRequest(url: reposeEndpoint, method: 'get')
        def sentRequest = mc.handlings[0]

        then: "Repose will send x-pp-group with the configured value"
        mc.handlings.size() == 1

        and: "Repose will send x-pp-user based on requestor ip"
        sentRequest.request.getHeaders().findAll("x-pp-user").size() == 1
        def user = sentRequest.request.headers.getFirstValue("x-pp-user")
        // The possible IPv4/6 addresses.
        user == "127.0.0.1;q=0.4" || user == "0:0:0:0:0:0:0:1;q=0.4" || user == "::1;q=0.4"

        and: "Repose will send x-pp-groups with the configured value"
        sentRequest.request.headers.getFirstValue("x-pp-groups").equalsIgnoreCase("local-group;q=0.4")
    }

    //REP-3838 fix using x-forward-for as x-pp-user
    def "When X-Forwarded-For header when it is included should use vaule for x-pp-user"() {
        given: "x-forwarded-for header is set"
        def headers = ['x-forwarded-for': '10.1.1.3']

        when: "Request is sent through repose"
        MessageChain mc = deproxy.makeRequest(url: reposeEndpoint, method: 'get', headers: headers)
        def sentRequest = mc.handlings[0]

        then: "Repose will send x-pp-group with the configured value"
        mc.handlings.size() == 1

        and: "Repose will x-pp-user base on x-forward-for header plus default quality"
        sentRequest.request.headers.findAll("x-pp-user").size() == 1
        sentRequest.request.headers.getFirstValue("x-pp-user") == '10.1.1.3;q=0.4'
    }

    def "When multi x-forwarded-for headers are included shoud take 1st valued for x-pp-user"() {
        given: "x-forwarded-for header is set"
        def headers = ['x-forwarded-for': '192.25.25.15',
                       'X-Forwarded-For': '10.1.1.3']
        when: "Request is sent through repose"
        MessageChain mc = deproxy.makeRequest(url: reposeEndpoint, method: 'get', headers: headers)
        def sentRequest = mc.handlings[0]

        then: "Repose will send x-pp-group with the configured value"
        mc.handlings.size() == 1

        and: "Repose will x-pp-user base on x-forward-for header plus default quality"
        sentRequest.request.headers.findAll("x-pp-user").size() == 1
        sentRequest.request.headers.getFirstValue("x-pp-user") == '192.25.25.15;q=0.4'
    }

    def "When multi x-forwarded-for splitable headers are included shoud take 1st valued for x-pp-user"() {
        given: "x-forwarded-for header is set"
        def headers = ['x-forwarded-for': '192.25.25.15, 10.1.1.3']

        when: "Request is sent through repose"
        MessageChain mc = deproxy.makeRequest(url: reposeEndpoint, method: 'get', headers: headers)
        def sentRequest = mc.handlings[0]

        then: "Repose will send x-pp-group with the configured value"
        mc.handlings.size() == 1

        and: "Repose will x-pp-user base on x-forward-for header plus default quality"
        sentRequest.request.headers.findAll("x-pp-user").size() == 1
        sentRequest.request.headers.getFirstValue("x-pp-user") == '192.25.25.15;q=0.4'
    }
}
