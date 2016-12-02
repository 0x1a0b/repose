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
package features.filters.cors

import framework.ReposeValveTest
import org.apache.http.HttpResponse
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import org.apache.http.util.EntityUtils
import org.openrepose.commons.utils.http.CommonHttpHeader
import org.openrepose.commons.utils.http.CorsHttpHeader
import org.rackspace.deproxy.ClientConnector
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.DeproxyHttpRequest
import org.rackspace.deproxy.Header
import org.rackspace.deproxy.MessageChain
import org.rackspace.deproxy.PortFinder
import org.rackspace.deproxy.Request
import org.rackspace.deproxy.RequestParams
import org.rackspace.deproxy.Response
import spock.lang.Shared
import spock.lang.Unroll

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN
import static javax.servlet.http.HttpServletResponse.SC_OK

class CorsSameOriginTest extends ReposeValveTest {

    @Shared
    int reposeSslPort

    @Shared
    String reposeSslEndpoint

    def setupSpec() {
        reposeLogSearch.cleanLog()

        reposeSslPort = PortFinder.Singleton.getNextOpenPort()
        reposeSslEndpoint = "https://$properties.targetHostname:$reposeSslPort"

        def params = properties.getDefaultTemplateParams() + [reposeSslPort: reposeSslPort]
        repose.configurationProvider.cleanConfigDirectory()
        repose.configurationProvider.applyConfigs("common", params)
        repose.configurationProvider.applyConfigs("features/filters/cors/sameorigin", params)

        // add self-signed SSL certificate support to Deproxy
        CloseableHttpClient client = HttpClients.custom()
                .setSSLSocketFactory(new SSLConnectionSocketFactory(
                        SSLContexts.custom().loadTrustMaterial(TrustSelfSignedStrategy.INSTANCE).build(),
                        NoopHostnameVerifier.INSTANCE))
                .build()
        ClientConnector sslConnector = { Request request, boolean https, host, port, RequestParams requestParams ->
            def scheme = https ? 'https' : 'http'
            def deproxyRequest = new DeproxyHttpRequest(request, scheme, host as String, port)

            HttpResponse response = client.execute(deproxyRequest)

            def body
            if (response.entity?.contentType?.value?.toLowerCase()?.startsWith("text/")) {
                body = EntityUtils.toString(response.entity)
            } else if (response.entity) {
                body = EntityUtils.toByteArray(response.entity)
            } else {
                body = null
            }

            new Response(response.statusLine.statusCode,
                    response.statusLine.reasonPhrase,
                    response.getAllHeaders().collect { new Header(it.getName(), it.getValue()) },
                    body)
        }

        deproxy = new Deproxy(null, sslConnector)
        deproxy.addEndpoint(properties.targetPort, 'origin service')

        repose.start()
        repose.waitForNon500FromUrl(reposeEndpoint)
    }

    @Unroll
    def "Request considered non-CORS due to no Origin header with URI scheme '#scheme', method '#method', Host '#host', X-Forwarded-Host '#forwardedHost'"() {
        given: "the correct Repose endpoint is used depending on which scheme (http or https) we want to use"
        def endpoint = (scheme == "https") ? reposeSslEndpoint : reposeEndpoint

        and: "the headers are set for Host and maybe X-Forwarded-Host but not Origin"
        def headers = [(CommonHttpHeader.HOST.toString()): host]
        if (forwardedHost) {
            headers += [(CommonHttpHeader.X_FORWARDED_HOST.toString()): forwardedHost]
        }

        when:
        MessageChain mc = deproxy.makeRequest(url: endpoint, method: method, headers: headers)

        then: "the response status is OK"
        mc.receivedResponse.code as Integer == SC_OK

        and: "the request makes it to the origin service"
        mc.getHandlings().size() == 1

        and: "none of the CORS headers are added to the response"
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_EXPOSE_HEADERS.toString()).isEmpty()

        and: "the Vary header is set"
        mc.receivedResponse.headers.contains("Vary")

        where:
        [scheme, method, host, forwardedHost] <<
                [["http", "https"],
                 ["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "TRACE", "OPTIONS"],
                 ["openrepose.com", "openrepose.com:8080", "test.repose.site", "dev.repose.site:8443", "10.8.8.8", "10.8.4.4:9090", "[2001:db8:cafe::34]", "[2001:db8:cafe::34]:4444"],
                 [null, "test.repose.site", "dev.repose.site:8080, test.repose.site", "10.8.8.8", "10.8.4.4:9090", "[2001:db8:cafe::34]", "[2001:db8:cafe::34]:4444"]]
                        .combinations()
    }

    @Unroll
    def "Request considered CORS actual request due to Origin '#origin' not matching X-Forwarded-Host 'will.not.match:3030' despite Host '#host' matching with URI scheme '#scheme' and method '#method'"() {
        given: "the correct Repose endpoint is used depending on which scheme (http or https) we want to use"
        def endpoint = (scheme == "https") ? reposeSslEndpoint : reposeEndpoint

        and: "the headers are set for Host and Origin to match explicitly but X-Forwarded-Host to some other value"
        def headers = [
                (CommonHttpHeader.X_FORWARDED_HOST.toString()): "will.not.match:3030",
                (CommonHttpHeader.HOST.toString())            : host,
                (CorsHttpHeader.ORIGIN.toString())            : origin]

        when:
        MessageChain mc = deproxy.makeRequest(url: endpoint, method: method, headers: headers)

        then: "the response status is OK"
        mc.receivedResponse.code as Integer == SC_OK

        and: "the request makes it to the origin service"
        mc.getHandlings().size() == 1

        and: "the CORS headers for an actual request are added"
        mc.receivedResponse.headers.getFirstValue(CorsHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString()) == origin
        mc.receivedResponse.headers.getFirstValue(CorsHttpHeader.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString()) == 'true'

        and: "the CORS headers for a preflight request are not added"
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString()).isEmpty()

        and: "the Vary header is set"
        mc.receivedResponse.headers.contains("Vary")

        where:
        scheme  | method    | host                       | origin
        "http"  | "GET"     | "[2001:db8:cafe::34]:4444" | "http://[2001:db8:cafe::34]:4444"
        "https" | "HEAD"    | "[2001:db8:cafe::34]"      | "https://[2001:db8:cafe::34]"
        "http"  | "POST"    | "10.8.8.8:9090"            | "http://10.8.8.8:9090"
        "https" | "PUT"     | "10.8.4.4"                 | "https://10.8.4.4"
        "http"  | "PATCH"   | "test.repose.site"         | "http://test.repose.site"
        "https" | "DELETE"  | "dev.repose.site:8443"     | "https://dev.repose.site:8443"
        "http"  | "TRACE"   | "openrepose.com"           | "http://test.repose.site"
        "https" | "OPTIONS" | "openrepose.com:9443"      | "https://openrepose.com:9443"
    }

    @Unroll
    def "Request considered CORS preflight request due to Origin '#origin' not matching X-Forwarded-Host 'will.not.match:3030' despite Host '#host' matching with URI scheme '#scheme', Access-Control-Request-Method '#requestedMethod'"() {
        given: "the correct Repose endpoint is used depending on which scheme (http or https) we want to use"
        def endpoint = (scheme == "https") ? reposeSslEndpoint : reposeEndpoint

        and: "the headers are set for Host and Origin to match explicitly but X-Forwarded-Host to some other value"
        def headers = [
                (CommonHttpHeader.X_FORWARDED_HOST.toString())           : "will.not.match:3030",
                (CommonHttpHeader.HOST.toString())                       : host,
                (CorsHttpHeader.ORIGIN.toString())                       : origin,
                (CorsHttpHeader.ACCESS_CONTROL_REQUEST_METHOD.toString()): requestedMethod]

        when:
        MessageChain mc = deproxy.makeRequest(url: endpoint, method: "OPTIONS", headers: headers)

        then: "the response status is OK"
        mc.receivedResponse.code as Integer == SC_OK

        and: "the request does not make it to the origin service"
        mc.getHandlings().isEmpty()

        and: "the CORS headers for a preflight request are added"
        mc.receivedResponse.headers.getFirstValue(CorsHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString()) == origin
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString()).size() == 1
        mc.receivedResponse.headers.getFirstValue(CorsHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString()).tokenize(',').contains(requestedMethod)
        mc.receivedResponse.headers.getFirstValue(CorsHttpHeader.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString()) == 'true'

        and: "the 'Access-Control-Allow-Headers' header is not set since none were requested"
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString()).isEmpty()

        and: "the CORS headers for an actual request are not added"
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_EXPOSE_HEADERS.toString()).isEmpty()

        and: "the 'Vary' header is set with the correct values for an OPTIONS request"
        mc.receivedResponse.headers.contains("Vary")
        mc.receivedResponse.headers.findAll("Vary") == ['origin', 'access-control-request-headers', 'access-control-request-method']

        where:
        scheme  | requestedMethod | host                       | origin
        "http"  | "GET"           | "[2001:db8:cafe::34]:4444" | "http://[2001:db8:cafe::34]:4444"
        "https" | "HEAD"          | "[2001:db8:cafe::34]"      | "https://[2001:db8:cafe::34]"
        "http"  | "POST"          | "10.8.8.8:9090"            | "http://10.8.8.8:9090"
        "https" | "PUT"           | "10.8.4.4"                 | "https://10.8.4.4"
        "http"  | "PATCH"         | "test.repose.site"         | "http://test.repose.site"
        "https" | "DELETE"        | "dev.repose.site:8443"     | "https://dev.repose.site:8443"
        "http"  | "TRACE"         | "openrepose.com"           | "http://test.repose.site"
        "https" | "OPTIONS"       | "openrepose.com:9443"      | "https://openrepose.com:9443"
    }

    @Unroll
    def "Only the first X-Forwarded-Host '#forwardedHost' will be used when determining if a request is a same-origin request"() {
        given: "the headers are set for the Host never to match the Origin and for the X-Forwarded-Host to vary depending on the test"
        def headers = [
                new Header(CommonHttpHeader.HOST.toString(), "will.never.match:9999"),
                new Header(CorsHttpHeader.ORIGIN.toString(), "http://not.cors.allowed:7777")
        ] + forwardedHost.collect { new Header(CommonHttpHeader.X_FORWARDED_HOST.toString(), it) }

        when:
        MessageChain mc = deproxy.makeRequest(url: reposeEndpoint, method: "GET", headers: headers)

        then: "response code will be OK if same-origin or Forbidden if considered CORS request since origin wasn't allowed"
        mc.receivedResponse.code as Integer == responseCode

        and: "the request makes it to the origin service if it was supposed to have a good status, otherwise it should not"
        mc.getHandlings().size() == ((responseCode == SC_OK) ? 1 : 0)

        and: "none of the CORS headers are added to the response"
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_EXPOSE_HEADERS.toString()).isEmpty()

        and: "the Vary header is set"
        mc.receivedResponse.headers.contains("Vary")

        where:
        responseCode | forwardedHost
        SC_OK        | ["not.cors.allowed:7777, some.other.host:4567"]
        SC_OK        | ["not.cors.allowed:7777", "some.other.host:4567"]
        SC_OK        | ["not.cors.allowed:7777, some.other.host:4567", "even.another.host"]
        SC_OK        | ["not.cors.allowed:7777, some.other.host:4567", "even.another.host, yet.another.host:555"]
        SC_FORBIDDEN | ["some.other.host:4567, not.cors.allowed:7777"]
        SC_FORBIDDEN | ["some.other.host:4567", "not.cors.allowed:7777"]
        SC_FORBIDDEN | ["some.other.host:4567, even.another.host", "not.cors.allowed:7777"]
        SC_FORBIDDEN | ["some.other.host:4567, even.another.host", "not.cors.allowed:7777, yet.another.host:555"]
        SC_FORBIDDEN | ["some.other.host:4567, even.another.host", "yet.another.host:555, not.cors.allowed:7777"]
    }

    // https://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.2.3 says this is valid syntax for a URI, so we
    // let's support it for this header.
    @Unroll
    def "X-Forwarded-Host '#forwardedHost' can be parsed when it ends with a colon for URI Scheme '#scheme', method '#method', and Origin '#origin'"() {
        given: "the correct Repose endpoint is used depending on which scheme (http or https) we want to use"
        def endpoint = (scheme == "https") ? reposeSslEndpoint : reposeEndpoint

        and: "the headers are set for X-Forwarded-Host and Origin to match explicitly and Host to some other value"
        def headers = [
                (CommonHttpHeader.X_FORWARDED_HOST.toString()): forwardedHost,
                (CorsHttpHeader.ORIGIN.toString())            : origin,
                (CommonHttpHeader.HOST.toString())            : "origin.service:9090"]

        when:
        MessageChain mc = deproxy.makeRequest(url: endpoint, method: method, headers: headers)

        then: "the response status is OK"
        mc.receivedResponse.code as Integer == SC_OK

        and: "the request makes it to the origin service"
        mc.getHandlings().size() == 1

        and: "none of the CORS headers are added to the response"
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_EXPOSE_HEADERS.toString()).isEmpty()

        and: "the Vary header is set"
        mc.receivedResponse.headers.contains("Vary")

        where:
        scheme  | method    | forwardedHost          | origin
        "http"  | "GET"     | "[2001:db8:cafe::34]:" | "http://[2001:db8:cafe::34]:80"
        "https" | "HEAD"    | "[2001:db8:cafe::34]:" | "https://[2001:db8:cafe::34]:443"
        "http"  | "POST"    | "10.8.8.8:"            | "http://10.8.8.8:80"
        "https" | "PUT"     | "10.8.4.4:"            | "https://10.8.4.4:443"
        "http"  | "TRACE"   | "cors.not.allowed:"    | "http://cors.not.allowed:80"
        "https" | "OPTIONS" | "cors.not.allowed:"    | "https://cors.not.allowed:443"
    }

    @Unroll
    def "URI Scheme '#scheme' and X-Forwarded-Host/Host '#host' will match Origin '#origin' and be considered a same-origin request"() {
        given: "the correct Repose endpoint is used depending on which scheme (http or https) we want to use"
        def endpoint = (scheme == "https") ? reposeSslEndpoint : reposeEndpoint

        and: "the Origin header is set to one that is not allowed to make a CORS request"
        def headers = [(CorsHttpHeader.ORIGIN.toString()): origin]

        when: "we make a request with the Host header set to the same value as the Origin header"
        MessageChain mc = deproxy.makeRequest(url: endpoint, method: "GET", headers: headers + [(CommonHttpHeader.HOST.toString()): host])

        then: "the response status is OK"
        mc.receivedResponse.code as Integer == SC_OK

        and: "the request makes it to the origin service"
        mc.getHandlings().size() == 1

        and: "none of the CORS headers are added to the response, proving the Host and Origin matched"
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_EXPOSE_HEADERS.toString()).isEmpty()

        and: "the Vary header is set"
        mc.receivedResponse.headers.contains("Vary")

        when: "we make a request with the X-Forwarded-Host header set to the same value as the Origin header"
        mc = deproxy.makeRequest(url: endpoint, method: "GET", headers: headers + [(CommonHttpHeader.X_FORWARDED_HOST.toString()): host])

        then: "the response status is OK"
        mc.receivedResponse.code as Integer == SC_OK

        and: "the request makes it to the origin service"
        mc.getHandlings().size() == 1

        and: "none of the CORS headers are added to the response, proving the X-Forwarded-Host and Origin matched"
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_EXPOSE_HEADERS.toString()).isEmpty()

        and: "the Vary header is set"
        mc.receivedResponse.headers.contains("Vary")

        where:
        scheme  | host                                             | origin
        // host/origin comparison should be case insensitive
        "http"  | "www.rackspace.com:7070"                         | "http://www.rackspace.com:7070"
        "http"  | "www.rackspace.com:7070"                         | "http://WWW.rackspace.com:7070"
        "http"  | "WWW.rackspace.com:7070"                         | "http://www.rackspace.com:7070"
        "http"  | "www.rackspace.com:7070"                         | "http://www.RACKspace.com:7070"
        "http"  | "www.rackSPACE.com:7070"                         | "http://www.rackspace.com:7070"
        "https" | "www.rackspace.com:7070"                         | "https://www.rackspace.com:7070"
        "https" | "www.rackspace.com:7070"                         | "https://WWW.rackspace.com:7070"
        "https" | "WWW.rackspace.com:7070"                         | "https://www.rackspace.com:7070"
        "https" | "www.rackspace.com:7070"                         | "https://www.RACKspace.com:7070"
        "https" | "www.rackSPACE.com:7070"                         | "https://www.rackspace.com:7070"
        // scheme/origin comparison should be case insensitive
        "http"  | "www.rackspace.com:7070"                         | "HTTP://www.rackspace.com:7070"
        "http"  | "www.rackspace.com:7070"                         | "Http://www.rackspace.com:7070"
        "https" | "www.rackspace.com:7070"                         | "HTTPS://www.rackspace.com:7070"
        "https" | "www.rackspace.com:7070"                         | "Https://www.rackspace.com:7070"
        // default ports should be supported
        "http"  | "www.rackspace.com"                              | "http://www.rackspace.com"
        "http"  | "www.rackspace.com"                              | "http://www.rackspace.com:"
        "http"  | "www.rackspace.com"                              | "http://www.rackspace.com:80"
        "http"  | "www.rackspace.com:80"                           | "http://www.rackspace.com"
        "http"  | "www.rackspace.com:80"                           | "http://www.rackspace.com:80"
        "https" | "www.rackspace.com"                              | "https://www.rackspace.com"
        "https" | "www.rackspace.com"                              | "https://www.rackspace.com:"
        "https" | "www.rackspace.com"                              | "https://www.rackspace.com:443"
        "https" | "www.rackspace.com:443"                          | "https://www.rackspace.com"
        "https" | "www.rackspace.com:443"                          | "https://www.rackspace.com:443"
        // IPv4, default ports should be supported
        "http"  | "173.203.44.122"                                 | "http://173.203.44.122"
        "http"  | "173.203.44.122"                                 | "http://173.203.44.122:"
        "http"  | "173.203.44.122"                                 | "http://173.203.44.122:80"
        "http"  | "173.203.44.122:80"                              | "http://173.203.44.122"
        "http"  | "173.203.44.122:80"                              | "http://173.203.44.122:80"
        "https" | "173.203.44.122"                                 | "https://173.203.44.122"
        "https" | "173.203.44.122"                                 | "https://173.203.44.122:"
        "https" | "173.203.44.122"                                 | "https://173.203.44.122:443"
        "https" | "173.203.44.122:443"                             | "https://173.203.44.122"
        "https" | "173.203.44.122:443"                             | "https://173.203.44.122:443"
        // IPv6, host/origin comparison should support canonical and verbose formatting
        "http"  | "[2001:db8:cafe::17]:5555"                       | "http://[2001:db8:cafe::17]:5555"
        "http"  | "[2001:db8:cafe::17]:5555"                       | "http://[2001:db8:cafe:0:0:0:0:17]:5555"
        "http"  | "[2001:db8:cafe::17]:5555"                       | "http://[2001:0db8:cafe:0000:0000:0000:0000:0017]:5555"
        "http"  | "[2001:db8:cafe::17]:5555"                       | "http://[2001:0db8:CAFE:0000:0000:0000:0000:0017]:5555"
        "http"  | "[::2001:db8:cafe:17]:5555"                      | "http://[::2001:db8:cafe:17]:5555"
        "http"  | "[::2001:db8:cafe:17]:5555"                      | "http://[0:0:0:0:2001:db8:cafe:17]:5555"
        "http"  | "[::2001:db8:cafe:17]:5555"                      | "http://[0000:0:0000:0:2001:0db8:cafe:017]:5555"
        "http"  | "[::2001:db8:cafe:17]:5555"                      | "http://[0000:0000:0000:0000:2001:0db8:cafe:0017]:5555"
        "http"  | "[::2001:db8:cafe:17]:5555"                      | "http://[0000:0000:0000:0000:2001:0db8:CAFE:0017]:5555"
        "http"  | "[2001:db8:cafe:17::]:5555"                      | "http://[2001:db8:cafe:17::]:5555"
        "http"  | "[2001:db8:cafe:17::]:5555"                      | "http://[2001:db8:cafe:17:0:0:0:0]:5555"
        "http"  | "[2001:db8:cafe:17::]:5555"                      | "http://[2001:0db8:cafe:0017:0000:0000:0000:0000]:5555"
        "http"  | "[2001:db8:cafe:17:0:0:0:0]:5555"                | "http://[2001:db8:cafe:17::]:5555"
        "http"  | "[2001:0db8:cafe:0017:0000:0000:0000:0000]:5555" | "http://[2001:db8:cafe:17::]:5555"
        "http"  | "[2001:0db8:cafe:0017:0000:0000:0000:0000]:5555" | "http://[2001:db8:cafe:17:0:0:0:0]:5555"
        "http"  | "[2001:0db8:cafe:0017:0000:0000:0000:0000]:5555" | "http://[2001:0db8:cafe:0017:0000:0000:0000:0000]:5555"
        "http"  | "[2001:0db8:CAFE:0017:0000:0000:0000:0000]:5555" | "http://[2001:0db8:cafe:0017:0000:0000:0000:0000]:5555"
        "https" | "[2001:db8:cafe::17]:5555"                       | "https://[2001:db8:cafe::17]:5555"
        "https" | "[2001:db8:cafe::17]:5555"                       | "https://[2001:db8:cafe:0:0:0:0:17]:5555"
        "https" | "[2001:db8:cafe::17]:5555"                       | "https://[2001:0db8:cafe:0000:0000:0000:0000:0017]:5555"
        "https" | "[2001:db8:cafe::17]:5555"                       | "https://[2001:0db8:CAFE:0000:0000:0000:0000:0017]:5555"
        "https" | "[::2001:db8:cafe:17]:5555"                      | "https://[::2001:db8:cafe:17]:5555"
        "https" | "[::2001:db8:cafe:17]:5555"                      | "https://[0:0:0:0:2001:db8:cafe:17]:5555"
        "https" | "[::2001:db8:cafe:17]:5555"                      | "https://[0000:0:0000:0:2001:0db8:cafe:017]:5555"
        "https" | "[::2001:db8:cafe:17]:5555"                      | "https://[0000:0000:0000:0000:2001:0db8:cafe:0017]:5555"
        "https" | "[::2001:db8:cafe:17]:5555"                      | "https://[0000:0000:0000:0000:2001:0db8:CAFE:0017]:5555"
        "https" | "[2001:db8:cafe:17::]:5555"                      | "https://[2001:db8:cafe:17::]:5555"
        "https" | "[2001:db8:cafe:17::]:5555"                      | "https://[2001:db8:cafe:17:0:0:0:0]:5555"
        "https" | "[2001:db8:cafe:17::]:5555"                      | "https://[2001:0db8:cafe:0017:0000:0000:0000:0000]:5555"
        "https" | "[2001:db8:cafe:17:0:0:0:0]:5555"                | "https://[2001:db8:cafe:17::]:5555"
        "https" | "[2001:0db8:cafe:0017:0000:0000:0000:0000]:5555" | "https://[2001:db8:cafe:17::]:5555"
        "https" | "[2001:0db8:cafe:0017:0000:0000:0000:0000]:5555" | "https://[2001:db8:cafe:17:0:0:0:0]:5555"
        "https" | "[2001:0db8:cafe:0017:0000:0000:0000:0000]:5555" | "https://[2001:0db8:cafe:0017:0000:0000:0000:0000]:5555"
        "https" | "[2001:0db8:CAFE:0017:0000:0000:0000:0000]:5555" | "https://[2001:0db8:cafe:0017:0000:0000:0000:0000]:5555"
        // IPv6, default port should be supported (none specified)
        "http"  | "[2001:db8:cafe::17]"                            | "http://[2001:db8:cafe::17]"
        "http"  | "[2001:db8:cafe::17]"                            | "http://[2001:db8:cafe:0:0:0:0:17]"
        "http"  | "[2001:db8:cafe::17]"                            | "http://[2001:0db8:cafe:0000:0000:0000:0000:0017]"
        "http"  | "[2001:db8:cafe::17]"                            | "http://[2001:0db8:CAFE:0000:0000:0000:0000:0017]"
        "http"  | "[::2001:db8:cafe:17]"                           | "http://[::2001:db8:cafe:17]"
        "http"  | "[::2001:db8:cafe:17]"                           | "http://[0:0:0:0:2001:db8:cafe:17]"
        "http"  | "[::2001:db8:cafe:17]"                           | "http://[0000:0:0000:0:2001:0db8:cafe:017]"
        "http"  | "[::2001:db8:cafe:17]"                           | "http://[0000:0000:0000:0000:2001:0db8:cafe:0017]"
        "http"  | "[::2001:db8:cafe:17]"                           | "http://[0000:0000:0000:0000:2001:0db8:CAFE:0017]"
        "http"  | "[2001:db8:cafe:17::]"                           | "http://[2001:db8:cafe:17::]"
        "http"  | "[2001:db8:cafe:17::]"                           | "http://[2001:db8:cafe:17:0:0:0:0]"
        "http"  | "[2001:db8:cafe:17::]"                           | "http://[2001:0db8:cafe:0017:0000:0000:0000:0000]"
        "http"  | "[2001:db8:cafe:17:0:0:0:0]"                     | "http://[2001:db8:cafe:17::]"
        "http"  | "[2001:0db8:cafe:0017:0000:0000:0000:0000]"      | "http://[2001:db8:cafe:17::]"
        "http"  | "[2001:0db8:cafe:0017:0000:0000:0000:0000]"      | "http://[2001:db8:cafe:17:0:0:0:0]"
        "http"  | "[2001:0db8:cafe:0017:0000:0000:0000:0000]"      | "http://[2001:0db8:cafe:0017:0000:0000:0000:0000]"
        "http"  | "[2001:0db8:CAFE:0017:0000:0000:0000:0000]"      | "http://[2001:0db8:cafe:0017:0000:0000:0000:0000]"
        "https" | "[2001:db8:cafe::17]"                            | "https://[2001:db8:cafe::17]"
        "https" | "[2001:db8:cafe::17]"                            | "https://[2001:db8:cafe:0:0:0:0:17]"
        "https" | "[2001:db8:cafe::17]"                            | "https://[2001:0db8:cafe:0000:0000:0000:0000:0017]"
        "https" | "[2001:db8:cafe::17]"                            | "https://[2001:0db8:CAFE:0000:0000:0000:0000:0017]"
        "https" | "[::2001:db8:cafe:17]"                           | "https://[::2001:db8:cafe:17]"
        "https" | "[::2001:db8:cafe:17]"                           | "https://[0:0:0:0:2001:db8:cafe:17]"
        "https" | "[::2001:db8:cafe:17]"                           | "https://[0000:0:0000:0:2001:0db8:cafe:017]"
        "https" | "[::2001:db8:cafe:17]"                           | "https://[0000:0000:0000:0000:2001:0db8:cafe:0017]"
        "https" | "[::2001:db8:cafe:17]"                           | "https://[0000:0000:0000:0000:2001:0db8:CAFE:0017]"
        "https" | "[2001:db8:cafe:17::]"                           | "https://[2001:db8:cafe:17::]"
        "https" | "[2001:db8:cafe:17::]"                           | "https://[2001:db8:cafe:17:0:0:0:0]"
        "https" | "[2001:db8:cafe:17::]"                           | "https://[2001:0db8:cafe:0017:0000:0000:0000:0000]"
        "https" | "[2001:db8:cafe:17:0:0:0:0]"                     | "https://[2001:db8:cafe:17::]"
        "https" | "[2001:0db8:cafe:0017:0000:0000:0000:0000]"      | "https://[2001:db8:cafe:17::]"
        "https" | "[2001:0db8:cafe:0017:0000:0000:0000:0000]"      | "https://[2001:db8:cafe:17:0:0:0:0]"
        "https" | "[2001:0db8:cafe:0017:0000:0000:0000:0000]"      | "https://[2001:0db8:cafe:0017:0000:0000:0000:0000]"
        "https" | "[2001:0db8:CAFE:0017:0000:0000:0000:0000]"      | "https://[2001:0db8:cafe:0017:0000:0000:0000:0000]"
        // IPv6, default port should be supported (none specified, colon in the origin)
        "http"  | "[2001:db8:cafe::17]"                            | "http://[2001:db8:cafe::17]:"
        "http"  | "[2001:db8:cafe::17]"                            | "http://[2001:db8:cafe:0:0:0:0:17]:"
        "http"  | "[2001:db8:cafe::17]"                            | "http://[2001:0db8:cafe:0000:0000:0000:0000:0017]:"
        "http"  | "[2001:db8:cafe::17]"                            | "http://[2001:0db8:CAFE:0000:0000:0000:0000:0017]:"
        "http"  | "[::2001:db8:cafe:17]"                           | "http://[::2001:db8:cafe:17]:"
        "http"  | "[::2001:db8:cafe:17]"                           | "http://[0:0:0:0:2001:db8:cafe:17]:"
        "http"  | "[::2001:db8:cafe:17]"                           | "http://[0000:0:0000:0:2001:0db8:cafe:017]:"
        "http"  | "[::2001:db8:cafe:17]"                           | "http://[0000:0000:0000:0000:2001:0db8:cafe:0017]:"
        "http"  | "[::2001:db8:cafe:17]"                           | "http://[0000:0000:0000:0000:2001:0db8:CAFE:0017]:"
        "http"  | "[2001:db8:cafe:17::]"                           | "http://[2001:db8:cafe:17::]:"
        "http"  | "[2001:db8:cafe:17::]"                           | "http://[2001:db8:cafe:17:0:0:0:0]:"
        "http"  | "[2001:db8:cafe:17::]"                           | "http://[2001:0db8:cafe:0017:0000:0000:0000:0000]:"
        "http"  | "[2001:db8:cafe:17:0:0:0:0]"                     | "http://[2001:db8:cafe:17::]:"
        "http"  | "[2001:0db8:cafe:0017:0000:0000:0000:0000]"      | "http://[2001:db8:cafe:17::]:"
        "http"  | "[2001:0db8:cafe:0017:0000:0000:0000:0000]"      | "http://[2001:db8:cafe:17:0:0:0:0]:"
        "http"  | "[2001:0db8:cafe:0017:0000:0000:0000:0000]"      | "http://[2001:0db8:cafe:0017:0000:0000:0000:0000]:"
        "http"  | "[2001:0db8:CAFE:0017:0000:0000:0000:0000]"      | "http://[2001:0db8:cafe:0017:0000:0000:0000:0000]:"
        "https" | "[2001:db8:cafe::17]"                            | "https://[2001:db8:cafe::17]:"
        "https" | "[2001:db8:cafe::17]"                            | "https://[2001:db8:cafe:0:0:0:0:17]:"
        "https" | "[2001:db8:cafe::17]"                            | "https://[2001:0db8:cafe:0000:0000:0000:0000:0017]:"
        "https" | "[2001:db8:cafe::17]"                            | "https://[2001:0db8:CAFE:0000:0000:0000:0000:0017]:"
        "https" | "[::2001:db8:cafe:17]"                           | "https://[::2001:db8:cafe:17]:"
        "https" | "[::2001:db8:cafe:17]"                           | "https://[0:0:0:0:2001:db8:cafe:17]:"
        "https" | "[::2001:db8:cafe:17]"                           | "https://[0000:0:0000:0:2001:0db8:cafe:017]:"
        "https" | "[::2001:db8:cafe:17]"                           | "https://[0000:0000:0000:0000:2001:0db8:cafe:0017]:"
        "https" | "[::2001:db8:cafe:17]"                           | "https://[0000:0000:0000:0000:2001:0db8:CAFE:0017]:"
        "https" | "[2001:db8:cafe:17::]"                           | "https://[2001:db8:cafe:17::]:"
        "https" | "[2001:db8:cafe:17::]"                           | "https://[2001:db8:cafe:17:0:0:0:0]:"
        "https" | "[2001:db8:cafe:17::]"                           | "https://[2001:0db8:cafe:0017:0000:0000:0000:0000]:"
        "https" | "[2001:db8:cafe:17:0:0:0:0]"                     | "https://[2001:db8:cafe:17::]:"
        "https" | "[2001:0db8:cafe:0017:0000:0000:0000:0000]"      | "https://[2001:db8:cafe:17::]:"
        "https" | "[2001:0db8:cafe:0017:0000:0000:0000:0000]"      | "https://[2001:db8:cafe:17:0:0:0:0]:"
        "https" | "[2001:0db8:cafe:0017:0000:0000:0000:0000]"      | "https://[2001:0db8:cafe:0017:0000:0000:0000:0000]:"
        "https" | "[2001:0db8:CAFE:0017:0000:0000:0000:0000]"      | "https://[2001:0db8:cafe:0017:0000:0000:0000:0000]:"
        // IPv6, default port should be supported (specified in the host)
        "http"  | "[2001:db8:cafe::17]:80"                         | "http://[2001:db8:cafe::17]"
        "http"  | "[2001:db8:cafe::17]:80"                         | "http://[2001:db8:cafe:0:0:0:0:17]"
        "http"  | "[2001:db8:cafe::17]:80"                         | "http://[2001:0db8:cafe:0000:0000:0000:0000:0017]"
        "https" | "[2001:db8:cafe::17]:443"                        | "https://[2001:db8:cafe::17]"
        "https" | "[2001:db8:cafe::17]:443"                        | "https://[2001:db8:cafe:0:0:0:0:17]"
        "https" | "[2001:db8:cafe::17]:443"                        | "https://[2001:0db8:cafe:0000:0000:0000:0000:0017]"
        // IPv6, default port should be supported (specified in the origin)
        "http"  | "[2001:db8:cafe::17]"                            | "http://[2001:db8:cafe::17]:80"
        "http"  | "[2001:db8:cafe::17]"                            | "http://[2001:db8:cafe:0:0:0:0:17]:80"
        "http"  | "[2001:db8:cafe::17]"                            | "http://[2001:0db8:cafe:0000:0000:0000:0000:0017]:80"
        "https" | "[2001:db8:cafe::17]"                            | "https://[2001:db8:cafe::17]:443"
        "https" | "[2001:db8:cafe::17]"                            | "https://[2001:db8:cafe:0:0:0:0:17]:443"
        "https" | "[2001:db8:cafe::17]"                            | "https://[2001:0db8:cafe:0000:0000:0000:0000:0017]:443"
        // IPv6, default port should be supported (specified in the host and origin)
        "http"  | "[2001:db8:cafe::17]:80"                         | "http://[2001:db8:cafe::17]:80"
        "http"  | "[2001:db8:cafe::17]:80"                         | "http://[2001:db8:cafe:0:0:0:0:17]:80"
        "http"  | "[2001:db8:cafe::17]:80"                         | "http://[2001:0db8:cafe:0000:0000:0000:0000:0017]:80"
        "https" | "[2001:db8:cafe::17]:443"                        | "https://[2001:db8:cafe::17]:443"
        "https" | "[2001:db8:cafe::17]:443"                        | "https://[2001:db8:cafe:0:0:0:0:17]:443"
        "https" | "[2001:db8:cafe::17]:443"                        | "https://[2001:0db8:cafe:0000:0000:0000:0000:0017]:443"
    }

    @Unroll
    def "URI Scheme '#scheme' and X-Forwarded-Host/Host '#host' will NOT match Origin '#origin' and NOT be considered a same-origin request"() {
        given: "the correct Repose endpoint is used depending on which scheme (http or https) we want to use"
        def endpoint = (scheme == "https") ? reposeSslEndpoint : reposeEndpoint

        and: "the Origin header is set to one that is allowed to make a CORS request"
        def headers = [(CorsHttpHeader.ORIGIN.toString()): origin]

        when: "we make a request with the Host header set to a different value than the Origin header"
        MessageChain mc = deproxy.makeRequest(url: endpoint, method: "GET", headers: headers + [(CommonHttpHeader.HOST.toString()): host])

        then: "the response status is OK"
        mc.receivedResponse.code as Integer == SC_OK

        and: "the request makes it to the origin service"
        mc.getHandlings().size() == 1

        and: "the CORS headers for an actual request are added, proving the Host and Origin did not match"
        mc.receivedResponse.headers.getFirstValue(CorsHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString()) == origin
        mc.receivedResponse.headers.getFirstValue(CorsHttpHeader.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString()) == 'true'

        and: "the CORS headers for a preflight request are not added"
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString()).isEmpty()

        and: "the Vary header is set"
        mc.receivedResponse.headers.contains("Vary")

        when: "we make a request with the X-Forwarded-Host header set to a different value than the Origin header"
        mc = deproxy.makeRequest(url: endpoint, method: "GET", headers: headers + [(CommonHttpHeader.X_FORWARDED_HOST.toString()): host])

        then: "the response status is OK"
        mc.receivedResponse.code as Integer == SC_OK

        and: "the request makes it to the origin service"
        mc.getHandlings().size() == 1

        and: "the CORS headers for an actual request are added, proving the X-Forwarded-Host and Origin did not match"
        mc.receivedResponse.headers.getFirstValue(CorsHttpHeader.ACCESS_CONTROL_ALLOW_ORIGIN.toString()) == origin
        mc.receivedResponse.headers.getFirstValue(CorsHttpHeader.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString()) == 'true'

        and: "the CORS headers for a preflight request are not added"
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_METHODS.toString()).isEmpty()
        mc.receivedResponse.headers.findAll(CorsHttpHeader.ACCESS_CONTROL_ALLOW_HEADERS.toString()).isEmpty()

        and: "the Vary header is set"
        mc.receivedResponse.headers.contains("Vary")

        where:
        scheme  | host                                             | origin
        // different subdomains are considered different origins
        "http"  | "www.openrepose.com:8080"                        | "http://openrepose.com:8080"
        "http"  | "subdomain.openrepose.com:8080"                  | "http://openrepose.com:8080"
        "http"  | "test.repose.site:8080"                          | "http://dev.repose.site:8080"
        "https" | "www.openrepose.com:8080"                        | "https://openrepose.com:8080"
        "https" | "subdomain.openrepose.com:8080"                  | "https://openrepose.com:8080"
        "https" | "test.repose.site:8443"                          | "https://dev.repose.site:8443"
        // different ports are considered different origins
        "http"  | "openrepose.com:1111"                            | "http://openrepose.com:2222"
        "http"  | "openrepose.com"                                 | "http://openrepose.com:3333"
        "http"  | "openrepose.com"                                 | "http://openrepose.com:443"
        "https" | "openrepose.com:1111"                            | "https://openrepose.com:2222"
        "https" | "openrepose.com"                                 | "https://openrepose.com:3333"
        "https" | "openrepose.com"                                 | "https://openrepose.com:80"
        // different host names and TLDs are considered different origins
        "http"  | "rackspace.com:8080"                             | "http://openrepose.com:8080"
        "http"  | "openrepose.org:8080"                            | "http://openrepose.com:8080"
        "https" | "rackspace.com:8080"                             | "https://openrepose.com:8080"
        "https" | "openrepose.org:8080"                            | "https://openrepose.com:8080"
        // different schemes are considered different origins
        "http"  | "openrepose.com:8080"                            | "https://openrepose.com:8080"
        "https" | "openrepose.com:8080"                            | "http://openrepose.com:8080"
    }
}
