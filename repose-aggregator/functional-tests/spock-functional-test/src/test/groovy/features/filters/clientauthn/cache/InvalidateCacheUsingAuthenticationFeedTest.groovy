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
package features.filters.clientauthn.cache

import framework.ReposeValveTest
import framework.mocks.MockIdentityV2Service
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain
import org.rackspace.deproxy.Response

/**
 * Created by jennyvo on 1/22/16.
 *  identity using authenticated feed
 */
class InvalidateCacheUsingAuthenticationFeedTest extends ReposeValveTest {

    def originEndpoint
    def identityEndpoint
    def atomEndpoint

    MockIdentityV2Service fakeIdentityV2Service
    features.filters.keystonev2.AtomFeedResponseSimulator fakeAtomFeed

    def setup() {
        deproxy = new Deproxy()

        int atomPort = properties.atomPort2
        fakeAtomFeed = new features.filters.keystonev2.AtomFeedResponseSimulator(atomPort)
        atomEndpoint = deproxy.addEndpoint(atomPort, 'atom service', null, fakeAtomFeed.handler)

        def params = properties.defaultTemplateParams
        repose.configurationProvider.applyConfigs("common", params)
        repose.configurationProvider.applyConfigs("features/filters/keystonev2/common", params)
        repose.configurationProvider.applyConfigs("features/filters/keystonev2/atom", params)
        repose.start()

        originEndpoint = deproxy.addEndpoint(properties.targetPort, 'origin service')

        fakeIdentityV2Service = new MockIdentityV2Service(properties.identityPort, properties.targetPort)
        identityEndpoint = deproxy.addEndpoint(properties.identityPort,
                'identity service', null, fakeIdentityV2Service.handler)

    }

    def cleanup() {
        if (deproxy) {
            deproxy.shutdown()
        }
        repose.stop()
    }

    def "when token is cached then invalidated by atom feed, should attempt to revalidate token with identity endpoint"() {

        when: "I send a GET request to REPOSE with an X-Auth-Token header"
        fakeIdentityV2Service.resetCounts()
        MessageChain mc = deproxy.makeRequest(url: reposeEndpoint, method: 'GET', headers: ['X-Auth-Token': fakeIdentityV2Service.client_token])

        then: "REPOSE should validate the token and then pass the request to the origin service"
        mc.receivedResponse.code == '200'
        mc.handlings.size() == 1

        //Repose is getting an admin token and groups, so the number of
        //orphaned handlings doesn't necessarily equal the number of times a
        //token gets validated
        fakeIdentityV2Service.validateTokenCount == 1
        mc.handlings[0].endpoint == originEndpoint


        when: "I send a GET request to REPOSE with the same X-Auth-Token header"
        fakeIdentityV2Service.resetCounts()
        mc = deproxy.makeRequest(url: reposeEndpoint, method: 'GET', headers: ['X-Auth-Token': fakeIdentityV2Service.client_token])

        then: "Repose should use the cache, not call out to the fake identity service, and pass the request to origin service"
        mc.receivedResponse.code == '200'
        mc.handlings.size() == 1
        fakeIdentityV2Service.validateTokenCount == 0
        mc.handlings[0].endpoint == originEndpoint


        when: "identity atom feed has an entry that should invalidate the tenant associated with this X-Auth-Token"
        // change identity atom feed

        fakeIdentityV2Service.with {
            fakeIdentityV2Service.validateTokenHandler = {
                tokenId, tenantId, request, xml ->
                    new Response(404)
            }
        }

        fakeIdentityV2Service.resetCounts()
        fakeAtomFeed.hasEntry = true
        atomEndpoint.defaultHandler = fakeAtomFeed.handler



        and: "we sleep for 11 seconds so that repose can check the atom feed"
        sleep(15000)

        and: "I send a GET request to REPOSE with the same X-Auth-Token header"
        mc = deproxy.makeRequest(
                [
                        url           : reposeEndpoint,
                        method        : 'GET',
                        headers       : ['X-Auth-Token': fakeIdentityV2Service.client_token],
                        defaultHandler: fakeIdentityV2Service.handler
                ])

        then: "Repose should not have the token in the cache any more, so it try to validate it, which will fail and result in a 401"
        mc.receivedResponse.code == '401'
        mc.handlings.size() == 0
        fakeIdentityV2Service.validateTokenCount == 1
    }

    def "When a user is cached by repose, and a user Update event, invalidate the cache for that user"() {
        when: "I send a GET request to REPOSE with an X-Auth-Token header for a specific user"
        fakeIdentityV2Service.resetCounts()
        MessageChain mc = deproxy.makeRequest(url: reposeEndpoint, method: 'GET', headers: ['X-Auth-Token': fakeIdentityV2Service.client_token])

        then: "REPOSE should validate the token and then pass the request to the origin service"
        mc.receivedResponse.code == '200'
        mc.handlings.size() == 1

        //Repose is getting an admin token and groups, so the number of
        //orphaned handlings doesn't necessarily equal the number of times a
        //token gets validated
        fakeIdentityV2Service.validateTokenCount == 1
        fakeIdentityV2Service.getGroupsCount == 1
        mc.handlings[0].endpoint == originEndpoint

        when: "I send a GET request to REPOSE with the same X-Auth-Token header"
        fakeIdentityV2Service.resetCounts()
        mc = deproxy.makeRequest(url: reposeEndpoint, method: 'GET', headers: ['X-Auth-Token': fakeIdentityV2Service.client_token])

        then: "Repose should use the cache, not call out to the fake identity service, and pass the request to origin service"
        mc.receivedResponse.code == '200'
        mc.handlings.size() == 1
        fakeIdentityV2Service.validateTokenCount == 0
        fakeIdentityV2Service.getGroupsCount == 0
        mc.handlings[0].endpoint == originEndpoint

        when: "Identity atom feed has a Update User Event"
        //Identity needs to respond normally, so that we can get "new" user info
        fakeIdentityV2Service.resetCounts()
        fakeAtomFeed.hasEntry = true
        atomEndpoint.defaultHandler = fakeAtomFeed.userUpdateHandler(fakeIdentityV2Service.client_userid.toString())

        and: "we sleep for 15 seconds so that repose can check the atom feed"
        sleep(15000)

        and: "I send a GET request to REPOSE with the same X-Auth-Token header"
        mc = deproxy.makeRequest(
                [
                        url    : reposeEndpoint,
                        method : 'GET',
                        headers: ['X-Auth-Token': fakeIdentityV2Service.client_token]
                ])

        then: "Repose should not have the token in the cache any more, so it try to re-validate it"
        mc.receivedResponse.code == '200'
        mc.handlings.size() == 1
        fakeIdentityV2Service.validateTokenCount == 1
        fakeIdentityV2Service.getGroupsCount == 1

    }

    def "When a user is cached by repose, and a TRR event comes in, invalidate all of the cache for that user"() {

        when: "I send a GET request to REPOSE with an X-Auth-Token header for a specific user"
        fakeIdentityV2Service.resetCounts()
        MessageChain mc = deproxy.makeRequest(url: reposeEndpoint, method: 'GET', headers: ['X-Auth-Token': fakeIdentityV2Service.client_token])

        then: "REPOSE should validate the token and then pass the request to the origin service"
        mc.receivedResponse.code == '200'
        mc.handlings.size() == 1

        //Repose is getting an admin token and groups, so the number of
        //orphaned handlings doesn't necessarily equal the number of times a
        //token gets validated
        fakeIdentityV2Service.validateTokenCount == 1
        mc.handlings[0].endpoint == originEndpoint


        when: "I send a GET request to REPOSE with the same X-Auth-Token header"
        fakeIdentityV2Service.resetCounts()
        mc = deproxy.makeRequest(url: reposeEndpoint, method: 'GET', headers: ['X-Auth-Token': fakeIdentityV2Service.client_token])

        then: "Repose should use the cache, not call out to the fake identity service, and pass the request to origin service"
        mc.receivedResponse.code == '200'
        mc.handlings.size() == 1
        fakeIdentityV2Service.validateTokenCount == 0
        mc.handlings[0].endpoint == originEndpoint


        when: "Identity atom feed has a TRR event to invalidate our user"

        //Make identity respond with a 404 every time now.
        fakeIdentityV2Service.with {
            fakeIdentityV2Service.validateTokenHandler = {
                tokenId, tenantId, request, xml ->
                    new Response(404)
            }
        }

        fakeIdentityV2Service.resetCounts()
        //Configure the fake Atom Feed to hork back a TRR token
        fakeAtomFeed.hasEntry = true
        atomEndpoint.defaultHandler = fakeAtomFeed.trrEventHandler(fakeIdentityV2Service.client_userid.toString())

        and: "we sleep for 15 seconds so that repose can check the atom feed"
        sleep(15000)

        and: "I send a GET request to REPOSE with the same X-Auth-Token header"
        mc = deproxy.makeRequest(
                [
                        url           : reposeEndpoint,
                        method        : 'GET',
                        headers       : ['X-Auth-Token': fakeIdentityV2Service.client_token],
                        defaultHandler: fakeIdentityV2Service.handler
                ])

        then: "Repose should not have the token in the cache any more, so it try to validate it, which will fail and result in a 401"
        mc.receivedResponse.code == '401'
        mc.handlings.size() == 0
        fakeIdentityV2Service.validateTokenCount == 1
    }
}

