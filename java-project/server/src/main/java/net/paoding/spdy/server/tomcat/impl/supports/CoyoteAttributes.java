package net.paoding.spdy.server.tomcat.impl.supports;

import net.paoding.spdy.common.frame.frames.SynStream;

import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.jboss.netty.channel.Channel;

public class CoyoteAttributes {

    public static void setSynStream(Request request, SynStream SynStream) {
        request.setAttribute("net.paoding.spdy.synStream", SynStream);
    }

    public static SynStream getSynStream(Request request) {
        return (SynStream) request.getAttribute("net.paoding.spdy.synStream");
    }

    public static int getStreamId(Request request) {
        return getSynStream(request).getStreamId();
    }

    public static int getStreamId(Response response) {
        return getStreamId(response.getRequest());
    }

    public static Channel getChannel(Request request) {
        return (Channel) getSynStream(request).getChannel();
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
