package net.paoding.spdy.server.tomcat.impl.supports;

import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.jboss.netty.channel.Channel;

public class CoyoteAttributes {

    public static void setStreamId(Request request, int streamId) {
        request.setAttribute("net.paoding.spdy.streamId", streamId);
    }

    public static int getStreamId(Request request) {
        return (Integer) request.getAttribute("net.paoding.spdy.streamId");
    }

    public static int getStreamId(Response response) {
        return getStreamId(response.getRequest());
    }

    public static void setChannel(Request request, Channel channel) {
        request.setAttribute("net.paoding.spdy.channel", channel);
    }

    public static Channel getChannel(Request request) {
        return (Channel) request.getAttribute("net.paoding.spdy.channel");
    }

    public static Channel getChannel(Response response) {
        return getChannel(response.getRequest());
    }

    public static void setFinished(Response response) {
        response.getRequest().setAttribute("net.paoding.spdy.response.finished", Boolean.TRUE);
    }

    public static boolean isFinished(Response response) {
        return response.getRequest().getAttribute("net.paoding.spdy.response.finished") != null;
    }

}
