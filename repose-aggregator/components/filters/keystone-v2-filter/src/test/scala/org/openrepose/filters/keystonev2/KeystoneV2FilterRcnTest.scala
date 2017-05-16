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
package org.openrepose.filters.keystonev2

import java.io.{ByteArrayInputStream, InputStream}
import javax.servlet.FilterConfig
import javax.servlet.http.HttpServletResponse._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import com.rackspace.httpdelegation.HttpDelegationManager
import org.hamcrest.Matchers.hasEntry
import org.junit.runner.RunWith
import org.mockito.AdditionalMatchers._
import org.mockito.Matchers.{eq => mockitoEq, _}
import org.mockito.Mockito._
import org.openrepose.commons.utils.http._
import org.openrepose.core.services.config.ConfigurationService
import org.openrepose.core.services.datastore.{Datastore, DatastoreService}
import org.openrepose.core.services.serviceclient.akka.{AkkaServiceClient, AkkaServiceClientFactory}
import org.openrepose.core.systemmodel.{SystemModel, TracingHeaderConfig}
import org.openrepose.filters.keystonev2.KeystoneRequestHandler._
import org.openrepose.nodeservice.atomfeed.AtomFeedService
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, FunSpec}
import org.springframework.mock.web.{MockFilterChain, MockHttpServletRequest, MockHttpServletResponse}

import scala.collection.JavaConverters._
import scala.language.implicitConversions

@RunWith(classOf[JUnitRunner])
class KeystoneV2FilterRcnTest extends FunSpec
  with org.scalatest.Matchers
  with BeforeAndAfterEach
  with MockitoSugar
  with IdentityResponses
  with HttpDelegationManager {

  private var mockAkkaServiceClient: AkkaServiceClient = _
  private val mockAkkaServiceClientFactory = mock[AkkaServiceClientFactory]
  private val mockDatastore = mock[Datastore]
  private val mockDatastoreService = mock[DatastoreService]
  private var mockConfigurationService: ConfigurationService = _
  when(mockDatastoreService.getDefaultDatastore).thenReturn(mockDatastore)
  private val mockSystemModel = mock[SystemModel]
  private val mockTracingHeader = mock[TracingHeaderConfig]
  when(mockSystemModel.getTracingHeader).thenReturn(mockTracingHeader)
  when(mockTracingHeader.isEnabled).thenReturn(true, Nil: _*)
  private val mockFilterConfig = mock[FilterConfig]
  private var filter: KeystoneV2Filter = _

  override def beforeEach(): Unit = {
    mockConfigurationService = mock[ConfigurationService]
    mockAkkaServiceClient = mock[AkkaServiceClient]
    when(mockAkkaServiceClientFactory.newAkkaServiceClient(or(anyString(), isNull.asInstanceOf[String]))).thenReturn(mockAkkaServiceClient)
  }

  Seq(true, false) foreach { applyRcnRoles =>
    val shouldOrNot: Boolean => String = { boolean => if (boolean) "should" else "should NOT" }
    val appendRcnParameter: Boolean => String = { boolean => if (boolean) "?apply_rcn_roles=true" else "" }
    describe(s"With apply-rcn-roles $applyRcnRoles") {
      val identityServiceUri = "https://some.identity.com"

      def configuration = Marshaller.keystoneV2ConfigFromString(
        s"""<?xml version="1.0" encoding="UTF-8"?>
           |<keystone-v2 xmlns="http://docs.openrepose.org/repose/keystone-v2/v1.0">
           |    <identity-service uri="$identityServiceUri"
           |                      set-catalog-in-header="true"
           |                      apply-rcn-roles="$applyRcnRoles"
           |    />
           |</keystone-v2>
           |""".stripMargin
      )

      it(s"${shouldOrNot(applyRcnRoles)} append the apply_rcn_roles query parameter to the Identity interactions") {
        filter = new KeystoneV2Filter(mockConfigurationService, mockAkkaServiceClientFactory, mock[AtomFeedService], mockDatastoreService)
        filter.init(mockFilterConfig)
        filter.KeystoneV2ConfigListener.configurationUpdated(configuration)
        filter.SystemModelConfigListener.configurationUpdated(mockSystemModel)
        val request = new MockHttpServletRequest()
        request.addHeader(CommonHttpHeader.AUTH_TOKEN, VALID_TOKEN)
        val response = new MockHttpServletResponse
        // Bogus status to make sure it is unchanged.
        val responseStatus = 7
        response.setStatus(responseStatus)
        val filterChain = new MockFilterChain()

        when(mockAkkaServiceClient.get(
          mockitoEq(s"$TOKEN_KEY_PREFIX$VALID_TOKEN"),
          mockitoEq(s"$identityServiceUri$TOKEN_ENDPOINT/$VALID_TOKEN${appendRcnParameter(applyRcnRoles)}"),
          argThat(hasEntry(CommonHttpHeader.AUTH_TOKEN, VALID_TOKEN)),
          anyBoolean()
        )).thenReturn(new ServiceClientResponse(SC_OK, validateTokenResponse(userId = VALID_USER_ID)))
        when(mockAkkaServiceClient.get(
          mockitoEq(s"$ENDPOINTS_KEY_PREFIX$VALID_TOKEN"),
          mockitoEq(s"$identityServiceUri${ENDPOINTS_ENDPOINT(VALID_TOKEN)}${appendRcnParameter(applyRcnRoles)}"),
          argThat(hasEntry(CommonHttpHeader.AUTH_TOKEN, VALID_TOKEN)),
          anyBoolean()
        )).thenReturn(new ServiceClientResponse(SC_OK, endpointsResponse()))
        when(mockAkkaServiceClient.get(
          mockitoEq(s"$GROUPS_KEY_PREFIX$VALID_USER_ID"),
          mockitoEq(s"$identityServiceUri${GROUPS_ENDPOINT(VALID_USER_ID)}"),
          argThat(hasEntry(CommonHttpHeader.AUTH_TOKEN, VALID_TOKEN)),
          anyBoolean()
        )).thenReturn(new ServiceClientResponse(SC_OK, groupsResponse()))

        filter.doFilter(request, response, filterChain)

        val filterChainRequest = filterChain.getRequest.asInstanceOf[HttpServletRequest]
        filterChainRequest shouldNot be(null)
        filterChainRequest.getHeaders(PowerApiHeader.X_CATALOG).asScala.isEmpty shouldBe false
        val filterChainResponse = filterChain.getResponse.asInstanceOf[HttpServletResponse]
        filterChainResponse shouldNot be(null)
        filterChainResponse.getStatus shouldBe responseStatus
      }
    }
  }

  implicit def stringToInputStream(s: String): InputStream = new ByteArrayInputStream(s.getBytes)

  implicit def looseToStrictStringMap(sm: java.util.Map[_ <: String, _ <: String]): java.util.Map[String, String] =
    sm.asInstanceOf[java.util.Map[String, String]]
}
