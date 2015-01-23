package org.openrepose.commons.utils.proxy;

import org.openrepose.commons.utils.http.ServiceClientResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

public interface RequestProxyService {

    //TODO: this is the most terrible return value ever
    int proxyRequest(String targetHost, HttpServletRequest request, HttpServletResponse response) throws IOException;
    void setRewriteHostHeader(boolean value);
    ServiceClientResponse get(String uri, Map<String, String> headers);
    ServiceClientResponse get(String baseUri, String extraUri, Map<String, String> headers);
    ServiceClientResponse delete(String baseUri, String extraUri, Map<String, String> headers);
    ServiceClientResponse put(String uri, Map<String, String> headers, byte[] body);
    ServiceClientResponse put(String baseUri, String path, Map<String, String> headers, byte[] body);
    ServiceClientResponse patch(String baseUri, String path, Map<String, String> headers, byte[] body);
}
