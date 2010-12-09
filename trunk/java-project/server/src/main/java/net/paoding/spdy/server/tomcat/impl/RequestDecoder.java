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
package net.paoding.spdy.server.tomcat.impl;

import static org.jboss.netty.channel.Channels.fireMessageReceived;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.RstStream;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.StreamFrame;
import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.common.supports.ExpireWheel;
import net.paoding.spdy.server.subscription.Subscription;
import net.paoding.spdy.server.tomcat.impl.subscriptionimpl.SubscriptionFactoryImpl;
import net.paoding.spdy.server.tomcat.impl.supports.CoyoteAttributes;
import net.paoding.spdy.server.tomcat.impl.supports.SpdyInputBuffer;

import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class RequestDecoder extends SimpleChannelHandler {

    protected static Log logger = LogFactory.getLog(RequestDecoder.class);

    private ExpireWheel<Request> requests = new ExpireWheel<Request>(1 << 14, 2);

    private SubscriptionFactoryImpl subscriptionFactory;

    public RequestDecoder(SubscriptionFactoryImpl subscriptionFactory) {
        this.subscriptionFactory = subscriptionFactory;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (!(msg instanceof StreamFrame)) {
            ctx.sendUpstream(e);
            return;
        }
        if (msg instanceof SynStream) {
            SynStream syn = (SynStream) msg;
            if (syn.getAssociatedId() > 0) {
                Subscription subscription = subscriptionFactory.getSubscription(syn
                        .getAssociatedId());
                if (subscription != null && syn.getFlags() == SpdyFrame.FLAG_FIN) {
                    subscription.close();
                    if (logger.isInfoEnabled()) {
                        logger.info("Describ: " + subscription);
                    }
                } else {
                    logger.warn("wrong synStream for : " + subscription + "; syn=" + syn);
                }
                return;
            }
            Request request = decode(syn);
            if ("GET".equals(syn.getHeader("method")) && syn.getFlags() != SpdyFrame.FLAG_FIN) {
                // it's a subscription
                Subscription subscription = subscriptionFactory.createSubscription(syn);
                request.setAttribute(Subscription.REQUEST_ATTR, subscription);
                if (logger.isInfoEnabled()) {
                    logger.info("Subscrib: " + request);
                }
                fireMessageReceived(ctx, request, e.getRemoteAddress());
                return;
            } else {
                if (syn.getFlags() != SpdyFrame.FLAG_FIN) {
                    requests.put(syn.getStreamId(), request);
                } else {
                    fireRequest(ctx, e, request);
                }
                return;
            }
        } else if (msg instanceof DataFrame) {
            DataFrame frame = (DataFrame) msg;
            Request request = requests.get(frame.getStreamId());
            if (request == null) {
                // bad!
                logger.warn("not found request for dataFrame : " + frame);
            } else {
                SpdyInputBuffer ib = (SpdyInputBuffer) request.getInputBuffer();
                ib.addDataFrame(frame);
                if (frame.getFlags() == SpdyFrame.FLAG_FIN) {
                    requests.remove(frame.getStreamId());
                    fireRequest(ctx, e, request);
                }
            }
            return;
        } else if (msg instanceof RstStream) {
            RstStream frame = (RstStream) msg;
            Request request = requests.get(frame.getStreamId());
            if (request == null) {
                // bad!
                logger.warn("not found request for rstStream : " + frame);
            } else {
                SpdyInputBuffer ib = (SpdyInputBuffer) request.getInputBuffer();
                ib.reset();
                requests.remove(frame.getStreamId());
            }
            return;
        } else {
            // continue
            return;
        }
    }

    private void fireRequest(ChannelHandlerContext ctx, MessageEvent e, Request request) {
        if (logger.isInfoEnabled()) {
            logger.info("Request: " + request);
        }
        fireMessageReceived(ctx, request, e.getRemoteAddress());
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        subscriptionFactory.destory();
        ctx.sendUpstream(e);
    }

    private Request decode(SynStream synStream) throws URISyntaxException {
        Request request = new Request();
        request.setStartTime(synStream.getTimestamp());
        CoyoteAttributes.setSynStream(request, synStream);
        //
        String method = synStream.getHeader("method");
        String url = synStream.getHeader("url");
        String version = synStream.getHeader("version");
        if (method == null && url == null && version == null) {
            throw new IllegalArgumentException("method=" + method + "; url=" + url + "; version="
                    + version);
        }
        request.method().setString(method);
        URI uri = new URI(url);
        request.protocol().setString(uri.getScheme());
        request.requestURI().setString(uri.getPath());
        request.query().setString(uri.getQuery());
        request.serverName().setString(uri.getHost());
        int port = uri.getPort();
        request.setServerPort(port > 0 ? port : 80);
        // copy headers
        MimeHeaders coyoteHeaders = request.getMimeHeaders();
        Map<String, String> headers = synStream.getHeaders();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            MessageBytes val = coyoteHeaders.addValue(header.getKey());
            val.setString(header.getValue());
        }
        // body
        request.setInputBuffer(new SpdyInputBuffer(synStream));
        //        System.out.println("RequestDecoder.decode: returnrequest " + request.decodedURI());
        return request;
    }
}
