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
package org.openrepose.core.services.rms;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.openrepose.commons.utils.InputStreamUtilities;
import org.openrepose.commons.utils.StringUtilities;
import org.openrepose.commons.utils.io.ByteBufferInputStream;
import org.openrepose.commons.utils.io.ByteBufferServletOutputStream;
import org.openrepose.commons.utils.io.buffer.ByteBuffer;
import org.openrepose.commons.utils.io.buffer.CyclicByteBuffer;
import org.openrepose.commons.utils.servlet.http.MutableHttpServletResponse;
import org.openrepose.core.services.config.ConfigurationService;
import org.openrepose.core.services.rms.config.Message;
import org.openrepose.core.services.rms.config.OverwriteType;
import org.openrepose.core.services.rms.config.ResponseMessagingConfiguration;
import org.openrepose.core.services.rms.config.StatusCodeMatcher;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.I_AM_A_TEAPOT;

/**
 * @author fran
 */
@RunWith(Enclosed.class)
public class ResponseMessageServiceImplTest {

    public static class WhenHandlingResponse {
        private static final String MESSAGE = "This is the replaced message";
        private final ResponseMessageServiceImpl rmsImpl = new ResponseMessageServiceImpl(mock(ConfigurationService.class));
        private final ResponseMessagingConfiguration configurationObject = new ResponseMessagingConfiguration();
        private final Vector<String> acceptValues = new Vector<>(1);
        private Enumeration<String> headerValueEnumeration = null;
        private HttpServletRequest mockedRequest = mock(HttpServletRequest.class);
        private MutableHttpServletResponse mockedResponse = mock(MutableHttpServletResponse.class);

        @Before
        public void setup() {
            acceptValues.addAll(Collections.singletonList("application/json"));
            headerValueEnumeration = acceptValues.elements();
            List<String> headerNames = new ArrayList<>();
            headerNames.add("Accept");
            when(mockedRequest.getHeaderNames()).thenReturn(Collections.enumeration(headerNames));
            when(mockedRequest.getHeaders("Accept")).thenReturn(headerValueEnumeration);
            when(mockedResponse.getStatus()).thenReturn(413);

            configurationObject.getStatusCode().clear();
            configurationObject.getStatusCode().add(createMatcher(OverwriteType.IF_EMPTY));
            rmsImpl.setInitialized();
            rmsImpl.updateConfiguration(configurationObject.getStatusCode());

        }

        private StatusCodeMatcher createMatcher(OverwriteType overwriteType) {
            StatusCodeMatcher matcher = new StatusCodeMatcher();
            matcher.setId("413");
            matcher.setCodeRegex("413");
            matcher.setOverwrite(overwriteType);

            Message message = new Message();
            message.setMediaType("*/*");
            message.setValue(MESSAGE);

            matcher.getMessage().add(message);

            return matcher;
        }

        @Test
        public void shouldWriteIfEmptyAndNoBody() throws IOException {
            when(mockedResponse.hasBody()).thenReturn(false);

            // Hook up response body stream to mocked response
            final ByteBuffer internalBuffer = new CyclicByteBuffer();
            final ServletOutputStream outputStream = new ByteBufferServletOutputStream(internalBuffer);
            final ByteBufferInputStream inputStream = new ByteBufferInputStream(internalBuffer);
            when(mockedResponse.getOutputStream()).thenReturn(outputStream);
            when(mockedResponse.getBufferedOutputAsInputStream()).thenReturn(inputStream);


            rmsImpl.handle(mockedRequest, mockedResponse);

            String result = InputStreamUtilities.streamToString(new ByteBufferInputStream(internalBuffer));
            assertTrue(StringUtilities.nullSafeEquals(MESSAGE, result));
        }

        @Test
        public void shouldPreserveIfEmptyAndBody() throws IOException {
            when(mockedResponse.hasBody()).thenReturn(true);

            // Hook up response body stream to mocked response
            final ByteBuffer internalBuffer = new CyclicByteBuffer();
            internalBuffer.put("hello there".getBytes());
            final ServletOutputStream outputStream = new ByteBufferServletOutputStream(internalBuffer);
            final ByteBufferInputStream inputStream = new ByteBufferInputStream(internalBuffer);
            when(mockedResponse.getBufferedOutputAsInputStream()).thenReturn(inputStream);
            when(mockedResponse.getOutputStream()).thenReturn(outputStream);

            rmsImpl.handle(mockedRequest, mockedResponse);

            String result = InputStreamUtilities.streamToString(new ByteBufferInputStream(internalBuffer));
            assertTrue(StringUtilities.nullSafeEquals("hello there", result));
        }
    }

    public static class WhenEscapingTheMessage {
        private static final String ESCAPE_THIS = "\b\n\t\f\r\\\"'/&<>";
        private static final String I_AM_A_TEAPOT_VALUE_STRING = Integer.toString(I_AM_A_TEAPOT.value());
        private List<Message> messages;
        private ResponseMessagingConfiguration responseMessagingConfiguration;
        private ResponseMessageServiceImpl responseMessageServiceImpl;
        private HttpServletRequest mockedRequest;
        private MutableHttpServletResponse response;

        @Before
        public void setup() {
            StatusCodeMatcher matcher = new StatusCodeMatcher();
            matcher.setId(I_AM_A_TEAPOT_VALUE_STRING);
            matcher.setCodeRegex(I_AM_A_TEAPOT_VALUE_STRING);
            matcher.setOverwrite(OverwriteType.IF_EMPTY);
            messages = matcher.getMessage();
            messages.add(createMessage("", ""));
            messages.add(createMessage("", MediaType.WILDCARD));
            messages.add(createMessage("", MediaType.TEXT_PLAIN));
            messages.add(createMessage("", MediaType.APPLICATION_JSON));
            messages.add(createMessage("", MediaType.APPLICATION_XML));
            messages.add(createMessage(MediaType.WILDCARD, ""));
            messages.add(createMessage(MediaType.WILDCARD, MediaType.WILDCARD));
            messages.add(createMessage(MediaType.WILDCARD, MediaType.TEXT_PLAIN));
            messages.add(createMessage(MediaType.WILDCARD, MediaType.APPLICATION_JSON));
            messages.add(createMessage(MediaType.WILDCARD, MediaType.APPLICATION_XML));
            messages.add(createMessage(MediaType.TEXT_PLAIN, ""));
            messages.add(createMessage(MediaType.TEXT_PLAIN, MediaType.WILDCARD));
            messages.add(createMessage(MediaType.TEXT_PLAIN, MediaType.TEXT_PLAIN));
            messages.add(createMessage(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));
            messages.add(createMessage(MediaType.TEXT_PLAIN, MediaType.APPLICATION_XML));
            responseMessagingConfiguration = new ResponseMessagingConfiguration();
            responseMessagingConfiguration.getStatusCode().add(matcher);
            responseMessageServiceImpl = new ResponseMessageServiceImpl(mock(ConfigurationService.class));
            responseMessageServiceImpl.setInitialized();
            responseMessageServiceImpl.updateConfiguration(responseMessagingConfiguration.getStatusCode());
            mockedRequest = mock(HttpServletRequest.class);
            when(mockedRequest.getHeaderNames()).thenReturn(Collections.enumeration(Collections.singletonList("Accept")));
            response = MutableHttpServletResponse.wrap(
                    mockedRequest,
                    new MockHttpServletResponse()
            );
            response.sendError(I_AM_A_TEAPOT.value(), ESCAPE_THIS);
            //response.setContentType(MediaType.TEXT_PLAIN);
        }

        @Test
        public void EscapeTheMessageForPlain() throws Exception {
            when(mockedRequest.getHeaders("Accept")).thenReturn(Collections.enumeration(Collections.singletonList(MediaType.TEXT_PLAIN)));

            responseMessageServiceImpl.handle(mockedRequest, response);

            assertEquals(
                    ESCAPE_THIS.trim(),
                    streamToString(response.getBufferedOutputAsInputStream())
            );
        }

        @Test
        public void EscapeTheMessageForJson() throws Exception {
            when(mockedRequest.getHeaders("Accept")).thenReturn(Collections.enumeration(Collections.singletonList(MediaType.APPLICATION_JSON)));

            responseMessageServiceImpl.handle(mockedRequest, response);

            assertEquals(
                    "\\b\\n\\t\\f\\r\\\\\\\"'\\/&<>".trim(),
                    streamToString(response.getBufferedOutputAsInputStream())
            );
        }

        @Test
        public void EscapeTheMessageForXml() throws Exception {
            when(mockedRequest.getHeaders("Accept")).thenReturn(Collections.enumeration(Collections.singletonList(MediaType.APPLICATION_XML)));

            responseMessageServiceImpl.handle(mockedRequest, response);

            assertEquals(
                    "\n\t\r\\&quot;&apos;/&amp;&lt;&gt;".trim(),
                    streamToString(response.getBufferedOutputAsInputStream())
            );
        }

        private Message createMessage(String mediaType, String contentType) {
            Message message = new Message();
            message.setMediaType(mediaType);
            message.setContentType(contentType);
            message.setValue("%M");
            return message;
        }

        private String streamToString(InputStream is) throws Exception {
            final StringBuilder stringBuilder = new StringBuilder();
            for (int i = is.read(); i != -1; i = is.read()) {
                stringBuilder.append((char) i);
            }
            return stringBuilder.toString();
        }
    }
}
