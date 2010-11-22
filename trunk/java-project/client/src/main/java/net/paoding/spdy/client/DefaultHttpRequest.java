package net.paoding.spdy.client;

import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpVersion;

public class DefaultHttpRequest extends org.jboss.netty.handler.codec.http.DefaultHttpRequest {

    public DefaultHttpRequest(String method, String uri) {
        super(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri);
    }

    public DefaultHttpRequest(HttpMethod method, String uri) {
        super(HttpVersion.HTTP_1_1, method, uri);
    }

    public DefaultHttpRequest(HttpVersion version, HttpMethod method, String uri) {
        super(version, method, uri);
    }

}
