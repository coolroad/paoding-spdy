package net.paoding.spdy.client.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.SynStream;

import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;

@Sharable
public class RequestEncoder extends SimpleChannelHandler {

    private NettyConnector connection;

    public RequestEncoder(NettyConnector spdyConnection) {
        this.connection = spdyConnection;
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof SpdyRequest) {
            encode(ctx, e, ((SpdyRequest) msg));
        } else {
            super.messageReceived(ctx, e);
        }
    }

    protected void encode(ChannelHandlerContext ctx, MessageEvent e, SpdyRequest spdyRequest)
            throws Exception {
        final HttpRequest request = spdyRequest.httpRequest;
        if (spdyRequest.type == 1 && spdyRequest.httpRequest.getMethod() != HttpMethod.GET) {
            throw new IllegalArgumentException(spdyRequest.httpRequest.getMethod()
                    + " is not allowed in request type " + spdyRequest.type);
        }
        final int streamId = spdyRequest.streamId;
        if (isTransferEncodingChunked(request)) {
            throw new UnsupportedOperationException("chunked is not supported in spdy");
        }
        SynStream synStream = new SynStream();
        synStream.setStreamId(streamId);
        synStream.setChannel(ctx.getChannel());
        Map<String, String> spdyHeaders = new HashMap<String, String>();
        spdyHeaders.put("method", request.getMethod().getName());
        spdyHeaders.put("url", getFullUrl(request));
        spdyHeaders.put("version", request.getProtocolVersion().getText());
        int length = request.getContent().readableBytes();
        HttpHeaders.setContentLength(request, length);
        if (length == 0 && spdyRequest.type == 0) {
            synStream.setFlags(SpdyFrame.FLAG_FIN);
        }
        for (Map.Entry<String, String> entry : request.getHeaders()) {
            if (spdyHeaders.containsKey(entry.getKey())) {
                throw new IllegalArgumentException("dulicated header " + entry.getKey());
            }
            spdyHeaders.put(entry.getKey(), entry.getValue());
        }
        synStream.setHeaders(spdyHeaders);
        Channels.write(ctx, e.getFuture(), synStream, e.getRemoteAddress());
        if (length > 0) {
            DataFrame dataFrame = new DataFrame();
            dataFrame.setStreamId(streamId);
            dataFrame.setChannel(ctx.getChannel());
            dataFrame.setFlags(SpdyFrame.FLAG_FIN);
            dataFrame.setData(request.getContent());
            Channels.write(ctx, e.getFuture(), dataFrame, e.getRemoteAddress());
        }
    }

    String getFullUrl(HttpRequest request) {
        String url = request.getUri();
        if (url.indexOf("://") < 0) {
            if (url.length() == 0) {
                url = "/";
            } else if (url.charAt(0) != '/') {
                url = "/" + url;
            }
            url = connection.getUrlPrefix() + url;
        }
        return url;
    }

    static boolean isTransferEncodingChunked(HttpMessage m) {
        List<String> chunked = m.getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
        if (chunked.isEmpty()) {
            return false;
        }

        for (String v : chunked) {
            if (v.equalsIgnoreCase(HttpHeaders.Values.CHUNKED)) {
                return true;
            }
        }
        return false;
    }
}
