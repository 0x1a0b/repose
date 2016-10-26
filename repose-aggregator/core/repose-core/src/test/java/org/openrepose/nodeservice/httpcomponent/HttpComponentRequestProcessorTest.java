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
package org.openrepose.nodeservice.httpcomponent;

import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.params.HttpParams;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.openrepose.commons.utils.io.stream.ServletInputStreamWrapper;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(Enclosed.class)
public class HttpComponentRequestProcessorTest {

    public static class WhenProcessingRequests {
        private URI uri;
        private HttpServletRequest request;
        private HttpComponentRequestProcessor processor;
        private String queryString = "param1%5B%5D=value1&param2=value21&param2=value22";
        private String[] headers = {"header1", "header2"};
        private String[] values1 = {"value1"};
        private String[] values2 = {"value21", "value22"};
        private HttpHost host;
        private HttpEntityEnclosingRequestBase method;
        private HttpParams methodParams;

        @Before
        public void setUp() throws URISyntaxException, IOException {
            request = mock(HttpServletRequest.class);
            uri = new URI("http://www.openrepose.org"); // mock(URI.class);
            host = new HttpHost("somename");
            method = mock(HttpEntityEnclosingRequestBase.class);
            methodParams = mock(HttpParams.class);

            when(request.getHeaderNames()).thenReturn(Collections.enumeration(Arrays.asList(headers)));
            when(request.getHeaders(eq("header1"))).thenReturn(Collections.enumeration(Arrays.asList(values1)));
            when(request.getHeaders(eq("header2"))).thenReturn(Collections.enumeration(Arrays.asList(values2)));
            when(request.getQueryString()).thenReturn(queryString);
            when(method.getParams()).thenReturn(methodParams);
            processor = new HttpComponentRequestProcessor(request, new URI("www.openrepose.org"), true, "true");
        }

        @Test
        public void shouldSetHeaders() throws IOException {
            when(request.getInputStream())
                    .thenReturn(new ServletInputStreamWrapper(new ByteArrayInputStream(new byte[]{})));

            processor.process(method);

            verify(request).getHeaderNames();
            for (String header : headers) {
                verify(request).getHeaders(eq(header));
            }

            for (String value : values1) {
                verify(method).addHeader(eq("header1"), eq(value));
            }

            for (String value : values2) {
                verify(method).addHeader(eq("header2"), eq(value));
            }
        }

        @Test
        public void shouldSetParams() throws Exception {
            when(request.getInputStream())
                    .thenReturn(new ServletInputStreamWrapper(new ByteArrayInputStream(new byte[]{})));

            URI uri = processor.getUri("http://foo.com");

            assertThat(uri.getRawQuery(), allOf(containsString("param1%5B%5D=value1"), containsString("param2=value21"), containsString("param2=value22")));
        }

        @Test
        public void shouldSetInputStream() throws IOException {
            when(request.getInputStream())
                    .thenReturn(new ServletInputStreamWrapper(new ByteArrayInputStream(new byte[]{})));

            processor.process(method);

            verify(method).setEntity(any(InputStreamEntity.class));
        }

        @Test
        public void shouldSetUnknownContentLengthIfChunkedIsInvalid() throws Exception {
            when(request.getInputStream())
                    .thenReturn(new ServletInputStreamWrapper(new ByteArrayInputStream(new byte[]{})));

            ArgumentCaptor<InputStreamEntity> requestEntityCaptor = ArgumentCaptor.forClass(InputStreamEntity.class);
            processor = new HttpComponentRequestProcessor(request, new URI("www.openrepose.org"), true, "invalid");
            processor.process(method);

            verify(method).setEntity(requestEntityCaptor.capture());
            assertEquals(-1, requestEntityCaptor.getValue().getContentLength());
        }

        @Test
        public void shouldSetUnknownContentLengthIfChunkedIsTrue() throws Exception {
            when(request.getInputStream())
                    .thenReturn(new ServletInputStreamWrapper(new ByteArrayInputStream(new byte[]{})));

            ArgumentCaptor<InputStreamEntity> requestEntityCaptor = ArgumentCaptor.forClass(InputStreamEntity.class);
            processor = new HttpComponentRequestProcessor(request, new URI("www.openrepose.org"), true, "true");
            processor.process(method);

            verify(method).setEntity(requestEntityCaptor.capture());
            assertEquals(-1, requestEntityCaptor.getValue().getContentLength());
        }

        @Test
        public void shouldSetUnknownContentLengthIfChunkedIs1() throws Exception {
            when(request.getInputStream())
                    .thenReturn(new ServletInputStreamWrapper(new ByteArrayInputStream(new byte[]{})));

            ArgumentCaptor<InputStreamEntity> requestEntityCaptor = ArgumentCaptor.forClass(InputStreamEntity.class);
            processor = new HttpComponentRequestProcessor(request, new URI("www.openrepose.org"), true, "1");
            processor.process(method);

            verify(method).setEntity(requestEntityCaptor.capture());
            assertEquals(-1, requestEntityCaptor.getValue().getContentLength());
        }

        @Test
        public void shouldSetActualContentLengthIfChunkedIsFalse() throws Exception {
            String body = "test";
            when(request.getInputStream())
                    .thenReturn(new ServletInputStreamWrapper(new ByteArrayInputStream(body.getBytes())));

            ArgumentCaptor<InputStreamEntity> requestEntityCaptor = ArgumentCaptor.forClass(InputStreamEntity.class);
            processor = new HttpComponentRequestProcessor(request, new URI("www.openrepose.org"), true, "false");
            processor.process(method);

            verify(method).setEntity(requestEntityCaptor.capture());
            assertEquals(body.length(), requestEntityCaptor.getValue().getContentLength());
        }

        @Test
        public void shouldSetActualContentLengthIfChunkedIs0() throws Exception {
            String body = "test";
            when(request.getInputStream())
                    .thenReturn(new ServletInputStreamWrapper(new ByteArrayInputStream(body.getBytes())));

            ArgumentCaptor<InputStreamEntity> requestEntityCaptor = ArgumentCaptor.forClass(InputStreamEntity.class);
            processor = new HttpComponentRequestProcessor(request, new URI("www.openrepose.org"), true, "0");
            processor.process(method);

            verify(method).setEntity(requestEntityCaptor.capture());
            assertEquals(body.length(), requestEntityCaptor.getValue().getContentLength());
        }

        @Test
        public void shouldSetUnknownContentLengthIfChunkedIsAutoAndOriginalRequestWasChunked() throws Exception {
            when(request.getInputStream())
                    .thenReturn(new ServletInputStreamWrapper(new ByteArrayInputStream(new byte[]{})));
            when(request.getHeader(eq("transfer-encoding")))
                    .thenReturn("chunked");

            ArgumentCaptor<InputStreamEntity> requestEntityCaptor = ArgumentCaptor.forClass(InputStreamEntity.class);
            processor = new HttpComponentRequestProcessor(request, new URI("www.openrepose.org"), true, "auto");
            processor.process(method);

            verify(method).setEntity(requestEntityCaptor.capture());
            assertEquals(-1, requestEntityCaptor.getValue().getContentLength());
        }

        @Test
        public void shouldSetActualContentLengthIfChunkedIsAutoAndOriginalRequestWasNotChunked() throws Exception {
            String body = "test";
            when(request.getInputStream())
                    .thenReturn(new ServletInputStreamWrapper(new ByteArrayInputStream(body.getBytes())));

            ArgumentCaptor<InputStreamEntity> requestEntityCaptor = ArgumentCaptor.forClass(InputStreamEntity.class);
            processor = new HttpComponentRequestProcessor(request, new URI("www.openrepose.org"), true, "auto");
            processor.process(method);

            verify(method).setEntity(requestEntityCaptor.capture());
            assertEquals(body.length(), requestEntityCaptor.getValue().getContentLength());
        }
    }
}
