/*
 * Copyright 2010-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License i distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.paoding.spdy.client.netty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.SynStream;

import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * 请求对象编码器，将请求对象编码转化spdy帧
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
@Sharable
public class RequestEncoder implements ChannelDownstreamHandler {

    /** 所属的连接对象 */
    private final NettyConnector connector;

    /** 是否要对请求对象做transferEcodingChunked检查 */
    private final boolean transferEncodingChunkedChecking;

    /**
     * 构建一个指定connector所属的请求对象编码器
     * 
     * @param connector 所属的连接对象
     * @param transferEncodingChunkedChecking
     *        是否要对请求对象做transferEcodingChunked检查
     */
    public RequestEncoder(NettyConnector connector, boolean transferEncodingChunkedChecking) {
        this.connector = connector;
        this.transferEncodingChunkedChecking = transferEncodingChunkedChecking;
    }

    /**
     * 只当下行消息是 {@link SpdyRequest}时才编码
     */
    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        Object msg;
        if (e instanceof MessageEvent) {
            msg = ((MessageEvent) e).getMessage();
            if (msg instanceof SpdyRequest) {
                encode(ctx, (MessageEvent) e, (SpdyRequest) msg);
                return;
            }

        }
        ctx.sendDownstream(e);
    }

    protected void encode(ChannelHandlerContext ctx, MessageEvent e, SpdyRequest spdyRequest)
            throws Exception {
        final HttpRequest request = spdyRequest.httpRequest;
        if (spdyRequest.type == 1 && spdyRequest.httpRequest.getMethod() != HttpMethod.GET) {
            throw new IllegalArgumentException(spdyRequest.httpRequest.getMethod()
                    + " is not allowed in request type " + spdyRequest.type);
        }
        final int streamId = spdyRequest.streamId;
        if (transferEncodingChunkedChecking && isTransferEncodingChunked(request)) {
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
            url = connector.getUrlPrefix() + url;
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
