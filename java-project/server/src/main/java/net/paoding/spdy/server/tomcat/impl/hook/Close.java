package net.paoding.spdy.server.tomcat.impl.hook;

import net.paoding.spdy.common.frame.frames.DataFrame;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.server.tomcat.impl.supports.CoyoteAttributes;

import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.jboss.netty.channel.Channels;

public class Close implements Action {

    @Override
    public void action(Request request, Response response, Object param) {
        if (!CoyoteAttributes.isFinished(response)) {
            CoyoteAttributes.setFinished(response);
            DataFrame frame = new DataFrame();
            frame.setChannel(CoyoteAttributes.getChannel(request));
            frame.setStreamId(CoyoteAttributes.getStreamId(request));
            frame.setFlags(SpdyFrame.FLAG_FIN);
            Channels.write(frame.getChannel(), frame);
        }
    }

}
