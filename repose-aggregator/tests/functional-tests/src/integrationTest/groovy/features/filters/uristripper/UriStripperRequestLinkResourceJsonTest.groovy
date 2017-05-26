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
package features.filters.uristripper

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.openrepose.commons.utils.http.media.MimeType
import org.openrepose.framework.test.ReposeValveTest
import org.rackspace.deproxy.Deproxy
import spock.lang.Unroll

class UriStripperRequestLinkResourceJsonTest extends ReposeValveTest {

    def static String tenantId = "94828347"
    def static jsonSlurper = new JsonSlurper()
    def static requestHeaders = ["Content-Type": MimeType.APPLICATION_JSON.toString()]
    def jsonBuilder

    def setupSpec() {
        deproxy = new Deproxy()
        deproxy.addEndpoint(properties.targetPort)

        def params = properties.getDefaultTemplateParams()
        repose.configurationProvider.applyConfigs("common", params)
        repose.configurationProvider.applyConfigs("features/filters/uristripper/common", params)
        repose.configurationProvider.applyConfigs("features/filters/uristripper/linkresource/request/json", params)
        repose.enableDebug()
        repose.start()
        waitUntilReadyToServiceRequests()
    }

    def setup() {
        jsonBuilder = new JsonBuilder()
    }

    def "when the uri does not match the configured uri-path-regex, the request body should not be modified"() {
        given: "url does not match configured uri-path-regex"
        def requestUrl = "/bar/$tenantId/path/to/resource"

        and: "a JSON request body contains a link with the tenantId"
        jsonBuilder {
            link requestUrl
        }

        when: "a request is made"
        def mc = deproxy.makeRequest(url: reposeEndpoint + requestUrl,
                method: "POST",
                headers: requestHeaders,
                requestBody: jsonBuilder.toString())

        and: "the received request JSON is parsed"
        def receivedRequestJson = jsonSlurper.parseText(mc.handlings[0].request.body as String)

        then: "the link in the received request body should remain unmodified"
        receivedRequestJson.link == requestUrl
    }

    @Unroll
    def "when the HTTP method #method is used, the request body link should be #expectedRequestBodyLink"() {
        given: "a JSON request body contains a link with a tenantId"
        def requestUrl = "/foo/$tenantId/bar"

        and: "a JSON request body contains a link with the tenantId"
        jsonBuilder {
            link requestUrl
        }

        when: "a request is made"
        def mc = deproxy.makeRequest(url: reposeEndpoint + requestUrl,
                method: method,
                headers: requestHeaders,
                requestBody: jsonBuilder.toString())

        and: "the received request JSON is parsed"
        def receivedRequestJson = jsonSlurper.parseText(mc.handlings[0].request.body as String)

        then: "the request body link is the expected value for the given method"
        receivedRequestJson.link == expectedRequestBodyLink

        where:
        method   | expectedRequestBodyLink
        "DELETE" | "/foo/$tenantId/bar"
        "POST"   | "/foo/bar"
        "PUT"    | "/foo/$tenantId/bar"
        "PATCH"  | "/foo/bar"
    }

    def "when the request is not JSON, the request body is not modified"() {
        given: "a non-JSON request body"
        def requestUrl = "/foo/$tenantId/bar"
        def body = "There's a million things I haven't done, just you wait"

        when: "a request is made"
        def mc = deproxy.makeRequest(url: reposeEndpoint + requestUrl,
                method: "POST",
                headers: ["Content-Type": MimeType.TEXT_PLAIN.toString()],
                requestBody: body)

        then: "the request body is not modified"
        mc.handlings[0].request.body as String == body
    }

    def "when configured to continue on mismatch, the request body link is not modified if it can't be updated due to the index being out of bounds"() {
        given: "the link in the JSON request doesn't contain the previous nor following token"
        def requestUrl = "/foo/$tenantId/bar"
        def requestBodyLink = "/foo"
        jsonBuilder {
            link requestBodyLink
        }

        when: "a request is made"
        def mc = deproxy.makeRequest(url: reposeEndpoint + requestUrl,
                method: "POST",
                headers: requestHeaders,
                requestBody: jsonBuilder.toString())

        and: "the received request JSON is parsed"
        def receivedRequestJson = jsonSlurper.parseText(mc.handlings[0].request.body as String)

        then: "the request body link is not modified"
        receivedRequestJson.link == requestBodyLink
    }

    def "when configured to continue on mismatch, the request body is not modified if the JSON path to the link does not resolve"() {
        given: "the JSON request doesn't contain the link field at all"
        def requestUrl = "/foo/$tenantId/bar"
        jsonBuilder {
            "not-the-link" "/foo/bar"
        }

        when: "a request is made"
        def mc = deproxy.makeRequest(url: reposeEndpoint + requestUrl,
                method: "POST",
                headers: requestHeaders,
                requestBody: jsonBuilder.toString())

        then: "the request body is not modified"
        mc.handlings[0].request.body as String == jsonBuilder.toString()
    }
}
