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

package org.openrepose.core.services.serviceclient.akka.impl

import org.junit.runner.RunWith
import org.mockito.AdditionalMatchers._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.openrepose.core.services.httpclient.HttpClientService
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, FunSpec}
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AkkaServiceClientFactoryImplTest extends FunSpec with Matchers with MockitoSugar {

  val httpClientService = mock[HttpClientService]

  describe("the factory will return an instance when") {
    List(null, "", "test_conn_pool").foreach { connectionPoolId =>
      it(s"the connection pool id is $connectionPoolId") {
        when(httpClientService.getMaxConnections(or(anyString(), isNull.asInstanceOf[String]))).thenReturn(20)
        val akkaServiceClientFactoryImpl = new AkkaServiceClientFactoryImpl(httpClientService)

        val akkaServiceClient = akkaServiceClientFactoryImpl.newAkkaServiceClient(connectionPoolId)

        akkaServiceClient should not be null
      }
    }
  }
}
