package net.paoding.spdy.client.impl;

import org.jboss.netty.handler.codec.http.HttpRequest;

class SpdyRequest {

    public int streamId;

    public HttpRequest httpRequest;
    
    public int type;

    public SpdyRequest(int streamId, HttpRequest request, int type) {
        this.httpRequest = request;
        this.streamId = streamId;
        this.type = type;
    }

}
