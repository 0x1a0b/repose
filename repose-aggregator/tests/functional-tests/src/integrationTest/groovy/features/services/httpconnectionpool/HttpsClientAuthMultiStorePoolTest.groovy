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
package features.services.httpconnectionpool

import framework.ReposeValveTest
import framework.mocks.MockIdentityV2Service
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.eclipse.jetty.http.HttpVersion
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.rackspace.deproxy.Deproxy
import spock.lang.Shared

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class HttpsClientAuthMultiStorePoolTest extends ReposeValveTest {

    @Shared
    def Server server
    @Shared
    def File serverFile
    @Shared
    def File clientFile
    @Shared
    def statusCode = HttpServletResponse.SC_OK
    @Shared
    def responseContent = "The is the plain text test body data.\n".bytes
    @Shared
    def contentType = "text/plain;charset=utf-8"
    @Shared
    def identityEndpoint
    @Shared
    def MockIdentityV2Service fakeIdentityV2Service

    def setupSpec() {
        deproxy = new Deproxy()

        reposeLogSearch.cleanLog()

        def params = properties.getDefaultTemplateParams()
        repose.configurationProvider.cleanConfigDirectory()
        repose.configurationProvider.applyConfigs("common", params)
        // Have to manually copy binary files, because the applyConfigs() attempts to substitute template parameters
        // when they are found and it breaks everything. :(
        def serverFileOrig = new File(repose.configurationProvider.configTemplatesDir, "common/server.jks")
        serverFile = new File(repose.configDir, "server.jks")
        def serverFileDest = new FileOutputStream(serverFile)
        Files.copy(serverFileOrig.toPath(), serverFileDest)
        def clientFileOrig = new File(repose.configurationProvider.configTemplatesDir, "common/client.jks")
        clientFile = new File(repose.configDir, "client.jks")
        def clientFileDest = new FileOutputStream(clientFile)
        Files.copy(clientFileOrig.toPath(), clientFileDest)
        params.targetPort = startJettyServer()
        repose.configurationProvider.applyConfigs("features/services/httpconnectionpool/clientauth/common", params)
        repose.configurationProvider.applyConfigs("features/services/httpconnectionpool/clientauth/multistore", params)

        fakeIdentityV2Service = new MockIdentityV2Service(params.identityPort, params.targetPort)
        identityEndpoint = deproxy.addEndpoint(params.identityPort, 'identity service', null, fakeIdentityV2Service.handler)

        repose.start()
        reposeLogSearch.awaitByString("Repose ready", 1, 60, TimeUnit.SECONDS)
    }

    def startJettyServer() {
        server = new Server()

        // SSL Context Factory
        def sslContextFactory = new SslContextFactory()
        sslContextFactory.keyStorePath = serverFile.absolutePath
        sslContextFactory.keyStorePassword = "password"
        sslContextFactory.keyManagerPassword = "password"
        sslContextFactory.needClientAuth = true
        sslContextFactory.trustStorePath = clientFile.absolutePath
        sslContextFactory.trustStorePassword = "password"

        // SSL HTTP Configuration
        def https_config = new HttpConfiguration()
        https_config.addCustomizer new SecureRequestCustomizer()

        // SSL Connector
        def sslConnector = new ServerConnector(
                server,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(https_config)
        )
        sslConnector.setPort(0)

        // Start the server with only the one endpoint for the server
        server.connectors = [sslConnector] as Connector[]
        server.handler = new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if (request.getHeader("x-tenant-id").equals(fakeIdentityV2Service.client_tenantid)
                        && request.getHeader("x-tenant-name").equals(fakeIdentityV2Service.client_tenantname)
                ) {
                    response.status = statusCode
                    response.contentType = contentType
                    baseRequest.handled = true
                    response.outputStream.write responseContent
                } else {
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                }
            }
        }
        server.start()
        return ((ServerConnector) server.connectors[0]).getLocalPort()
    }

    def cleanupSpec() {
        server?.stop()
    }

    def "Execute a non-SSL request to Repose which will use one pool to perform Auth-N and use the default pool w/ Client Auth to communicate with the origin service"() {
        //A simple request should go through
        given:
        fakeIdentityV2Service.with {
            client_token = UUID.randomUUID().toString()
            client_tenantid = "mytenant"
            client_tenantname = "mytenantname"
        }
        def request = new HttpGet("http://localhost:$properties.reposePort")
        request.addHeader('X-Auth-Token', fakeIdentityV2Service.client_token)
        def client = new DefaultHttpClient()

        when:
        def response = client.execute(request)

        then:
        assert response.statusLine.statusCode == statusCode
        assert response.entity.contentType.value == contentType
        assert Arrays.equals(response.entity.content.bytes, responseContent)
    }
}
