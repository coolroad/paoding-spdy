package net.paoding.spdy.server.tomcat.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.RstStream;
import net.paoding.spdy.common.frame.frames.StreamFrame;
import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.common.supports.ExpireWheel;
import net.paoding.spdy.server.subscription.ServerSubscription;
import net.paoding.spdy.server.tomcat.impl.supports.CoyoteAttributes;
import net.paoding.spdy.server.tomcat.impl.supports.SpdyInputBuffer;
import net.paoding.spdy.server.tomcat.impl.trap.SubscriptionFactoryImpl;
import net.paoding.spdy.server.tomcat.impl.trap.ServerSubscriptionImpl;

import org.apache.coyote.Request;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

@Sharable
public class RequestDecoder extends OneToOneDecoder {

    private ExpireWheel<Request> requests = new ExpireWheel<Request>(1 << 14, 2);

    private SubscriptionFactoryImpl subscriptionFactory;

    public RequestDecoder(SubscriptionFactoryImpl subscriptionFactory) {
        this.subscriptionFactory = subscriptionFactory;
    }
    // TODO: client关闭后subscription要自动关闭

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {
        if (msg instanceof StreamFrame) {
//            System.out.println("RequestDecoder.decode: " + channel.getRemoteAddress()
//                    + " streamId=" + ((StreamFrame) msg).getStreamId());
            if (msg instanceof SynStream) {
                SynStream syn = (SynStream) msg;
                Request request = decode(syn);
                if (request.method().equals("GET") && syn.getFlags() != SpdyFrame.FLAG_FIN) {
                    // it's a subscription
                    ServerSubscription trapper = subscriptionFactory.createSubscription(syn);
                    request.setAttribute(ServerSubscription.REQUEST_ATTR, trapper);
                    return request;
                } else if (syn.getAssociatedId() > 0) {
                    ServerSubscriptionImpl trapper = subscriptionFactory.getSubscription(syn.getAssociatedId());
                    if (trapper != null && syn.getFlags() == SpdyFrame.FLAG_FIN) {
                        trapper.setClosed();
                    }
                    return null;
                } else {
                    requests.put(syn.getStreamId(), request);
                    return request;
                }
            } else if (msg instanceof DataFrame) {
                DataFrame frame = (DataFrame) msg;
                Request request = requests.get(frame.getStreamId());
                if (request == null) {
                    // bad!
                } else {
                    SpdyInputBuffer ib = (SpdyInputBuffer) request.getInputBuffer();
                    ib.addDataFrame(frame);
                    if (ib.isFlagFin()) {
                        requests.remove(frame.getStreamId());
                    }
                }
                return null;
            } else if (msg instanceof RstStream) {
                RstStream frame = (RstStream) msg;
                requests.remove(frame.getStreamId());
                //
                Request request = requests.get(frame.getStreamId());
                if (request == null) {
                    // bad!
                } else {
                    SpdyInputBuffer ib = (SpdyInputBuffer) request.getInputBuffer();
                    ib.reset();
                    requests.remove(frame.getStreamId());
                }
                return null;
            } else {
                // continue
                return null;
            }
        } else {
            return msg;
        }
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
