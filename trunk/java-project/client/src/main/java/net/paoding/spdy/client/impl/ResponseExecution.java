package net.paoding.spdy.client.impl;

import java.util.Map;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.StreamFrame;
import net.paoding.spdy.common.frame.frames.SynReply;
import net.paoding.spdy.common.frame.frames.SynStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

/**
 * 接受服务器下发的各种frame，解析为response对象，并提交给executor通知源操作的future告知成功
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
@Sharable
public class ResponseExecution implements ChannelUpstreamHandler {

    private static Log logger = LogFactory.getLog(ResponseExecution.class);

    private NettyConnector connector;

    public ResponseExecution(NettyConnector connector) {
        this.connector = connector;
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            ctx.sendUpstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;
        Object msg = e.getMessage();
        if (!(msg instanceof StreamFrame)) {
            ctx.sendUpstream(evt);
            return;
        }
        final StreamFrame frame = (StreamFrame) msg;
        final int streamId = frame.getStreamId();
        final HttpResponseFuture future;
        // (客户端)接收到单向的synStream代表有一个server push过来了！
        if (msg instanceof SynStream) {
            future = doWithSynFrame(streamId, (SynStream) msg);
            if (future != null) {
                // serverpush时要把future注册到requests中，使得后续的dataframe能够正确塞出来
                // XXX:放进来到futures的这个future会不会和客户端的future frameId相同，从而互相覆盖而郁闷？
                // 不会!因为服务器端来的streamId和客户端发送的frameId奇偶性质不一样，不会互相覆盖
                connector.requests.put(streamId, future);
            }
        } else {
            // 
            future = connector.requests.get(streamId);
            if (future == null) {
                logger.warn("not found future for " + msg);
            } else {
                if (msg instanceof SynReply) {
                    doWithSynReply(future, (SynReply) msg);
                } else if (msg instanceof DataFrame) {
                    doWithDataFrame(future, (DataFrame) msg);
                }
            }
        }
        if (future != null && frame.getFlags() == SpdyFrame.FLAG_FIN) {
            connector.requests.remove(streamId);
            future.setSuccess();
        }
    }

    private HttpResponseFuture doWithSynFrame(int streamId, SynStream syn) {
        if (syn.getFlags() == SpdyFrame.FLAG_UNIDIRECTIONAL) {
            SubscriptionImpl subscription = connector.subscriptions.get(syn.getAssociatedId());
            if (subscription != null) {
                final HttpResponseFuture f = new HttpResponseFuture(connector, subscription, false);
                f.addListener(subscription.listener);
                HttpResponseStatus status = new HttpResponseStatus(200, "OK");
                HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
                for (Map.Entry<String, String> header : syn.getHeaders().entrySet()) {
                    String name = header.getKey();
                    if (name.equals("version") || name.equals("status")) {
                        continue;
                    }
                    response.setHeader(name, header.getValue());
                }
                f.setTarget(response);
                return f;
            } else {
                logger.warn("not found subscription for SynFrame:" + syn + "  url="
                        + syn.getHeader("url"));
            }
        } else {
            logger.warn("wrong SynFrame from server: should be unidirectional " + syn + "  url="
                    + syn.getHeader("url"));
        }
        return null;
    }

    /**
     * 处理SynReply帧
     * 
     * @param f
     * @param reply
     */
    private void doWithSynReply(final HttpResponseFuture f, SynReply reply) {
        HttpVersion version = HttpVersion.valueOf(reply.getHeader("version"));
        String statusline = reply.getHeader("status");
        int statusCode;
        String reasonPhrase;
        if (statusline.length() > 4) {
            statusCode = Integer.parseInt(statusline.substring(0, 3));
            reasonPhrase = statusline.substring(4);
        } else {
            statusCode = Integer.parseInt(statusline.substring(0, 3));
            reasonPhrase = "";
        }
        HttpResponseStatus status = new HttpResponseStatus(statusCode, reasonPhrase);
        HttpResponse response = new DefaultHttpResponse(version, status);
        for (Map.Entry<String, String> header : reply.getHeaders().entrySet()) {
            String name = header.getKey();
            if (name.equals("version") || name.equals("status")) {
                continue;
            }
            response.setHeader(name, header.getValue());
        }
        // 将repsonse设置为future的target
        f.setTarget(response);
    }

    /**
     * 处理DataFrame
     * 
     * @param f
     * @param frame
     */
    private void doWithDataFrame(final HttpResponseFuture f, DataFrame frame) {
        HttpResponse response = f.getTarget();
        ChannelBuffer content = response.getContent();
        if (frame.getData().readable()) {
            if (content.array().length == 0) {
                response.setContent(frame.getData());
            } else {
                content = ChannelBuffers.wrappedBuffer(content, frame.getData());
                response.setContent(content);
            }
        }
    }

}
