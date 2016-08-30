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
package features.services.datastore

import framework.ReposeValveTest
import org.apache.http.HttpResponse
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import org.apache.http.util.EntityUtils
import org.openrepose.commons.utils.io.ObjectSerializer
import org.openrepose.core.services.datastore.types.StringValue
import org.rackspace.deproxy.*
import spock.lang.Shared

import javax.net.ssl.SSLContext
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class DistDatastoreClientAuthMultiTest extends ReposeValveTest {
    //Since we're serializing objects here for the dist datastore, we must have the dist datastore objects in our classpath
    final ObjectSerializer objectSerializer = new ObjectSerializer(this.getClass().getClassLoader())

    @Shared
    def params
    @Shared
    def String distDatastoreEndpoint
    @Shared
    def File serverFile
    @Shared
    def File clientFile

    def setupSpec() {
        int dataStorePort = PortFinder.Singleton.getNextOpenPort()

        distDatastoreEndpoint = "https://localhost:${dataStorePort}"

        params = properties.getDefaultTemplateParams()
        params += [
                'datastorePort': dataStorePort
        ]

        reposeLogSearch.cleanLog()
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
        repose.configurationProvider.applyConfigs("features/services/datastore/clientauth", params)
        repose.configurationProvider.applyConfigs("features/services/datastore/clientauth/truststore", params)

        SSLContext sslContext = SSLContexts.custom()
                .loadKeyMaterial(serverFile, "password".toCharArray(), "password".toCharArray())
                .loadTrustMaterial(clientFile, "password".toCharArray())
                .build()
        CloseableHttpClient client = HttpClients.custom()
                .setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE))
                .build()
        // A hacky ClientConnector for Deproxy since one has not been build that allows for passing or configuring
        // an Apache HttpClient
        ClientConnector sslConnector = new ClientConnector() {
            @Override
            Response sendRequest(Request request, boolean https, Object host, Object port, RequestParams params) {
                def scheme = (https ? 'https' : 'http')
                def request2 = new DeproxyHttpRequest(request, scheme, host as String, port)

                HttpResponse response2 = client.execute(request2)

                def body
                if (response2.entity.contentType != null &&
                        response2.entity.contentType.value.toLowerCase().startsWith("text/")) {
                    body = EntityUtils.toString(response2.getEntity())
                } else {
                    body = EntityUtils.toByteArray(response2.getEntity())
                }

                Response response = new Response(response2.statusLine.statusCode,
                        response2.statusLine.reasonPhrase,
                        response2.getAllHeaders().collect { new Header(it.getName(), it.getValue()) },
                        body)
                return response
            }
        }

        deproxy = new Deproxy(null, sslConnector)
        deproxy.addEndpoint(properties.targetPort)

        repose.start()
        reposeLogSearch.awaitByString("Repose ready", 1, 60, TimeUnit.SECONDS)
    }

//    def "should not be able to put an object in the datastore as an anonymous client"() {
//        // TODO: Move to another test
//    }

    def "should be able to put an object in the datastore as an authenticated client"() {
        given:
        def headers = ['X-PP-Host-Key': 'temp', 'X-TTL': '5']
        def objectkey = UUID.randomUUID().toString()
        def body = objectSerializer.writeObject(new StringValue("test data"))

        when:
        MessageChain mc =
                deproxy.makeRequest(
                        [
                                method     : 'PUT',
                                url        : distDatastoreEndpoint + "/powerapi/dist-datastore/objects/" + objectkey,
                                headers    : headers,
                                requestBody: body
                        ])

        then:
        mc.receivedResponse.code == '202'
    }
}
