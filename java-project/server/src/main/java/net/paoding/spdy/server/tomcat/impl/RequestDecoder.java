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
import net.paoding.spdy.server.subscription.ServerSubscription;
import net.paoding.spdy.server.tomcat.impl.subscription.ServerSubscriptionImpl;
import net.paoding.spdy.server.tomcat.impl.subscription.SubscriptionFactoryImpl;
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
            Request request = decode(syn);
            if (request.method().equals("GET") && syn.getFlags() != SpdyFrame.FLAG_FIN) {
                // it's a subscription
                ServerSubscription subscription = subscriptionFactory.createSubscription(syn);
                request.setAttribute(ServerSubscription.REQUEST_ATTR, subscription);
                if (logger.isInfoEnabled()) {
                    logger.info("Subscrib: " + request);
                }
                fireMessageReceived(ctx, request, e.getRemoteAddress());
                return;
            } else if (syn.getAssociatedId() > 0) {
                ServerSubscriptionImpl subscription = subscriptionFactory.getSubscription(syn
                        .getAssociatedId());
                if (subscription != null && syn.getFlags() == SpdyFrame.FLAG_FIN) {
                    subscription.setClosed();
                    if (logger.isInfoEnabled()) {
                        logger.info("Describ: " + subscription);
                    }
                } else {
                    logger.warn("wrong synStream for : " + subscription + "; syn=" + syn);
                }
                return;
            } else {
                if (syn.getFlags() != SpdyFrame.FLAG_FIN) {
                    requests.put(syn.getStreamId(), request);
                }
                if (logger.isInfoEnabled()) {
                    logger.info("Request: " + request);
                }
                fireMessageReceived(ctx, request, e.getRemoteAddress());
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
                if (ib.isFlagFin()) {
                    requests.remove(frame.getStreamId());
                }
            }
            return;
        } else if (msg instanceof RstStream) {
            RstStream frame = (RstStream) msg;
            requests.remove(frame.getStreamId());
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

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        subscriptionFactory.close();
        super.channelClosed(ctx, e);
    }

    private Request decode(SynStream synStream) throws URISyntaxException {
        Request request = new Request();
        request.setStartTime(synStream.getTimestamp());
        CoyoteAttributes.setStreamId(request, synStream.getStreamId());
        CoyoteAttributes.setChannel(request, synStream.getChannel());
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
