package net.paoding.spdy.common.frame;

import net.paoding.spdy.common.frame.frames.Ping;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

/**
 * ping处理器
 * <p>
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
@Sharable
public class PingExecution extends SimpleChannelHandler {

    private static Log logger = LogFactory.getLog(PingExecution.class);

    private PingListener pingListener;

    public PingExecution() {
    }

    public PingExecution(PingListener pingListener) {
        setPingListener(pingListener);
    }

    public void setPingListener(PingListener pingListener) {
        this.pingListener = pingListener;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof Ping) {
            final Ping ping = (Ping) msg;
            if (ping.getId() % 2 == 1) {
                // Receivers of a PING frame should send an identical frame 
                // to the sender as soon as possible echo
                Channels.write(e.getChannel(), ping);
                if (logger.isInfoEnabled()) {
                    logger.info("ping echo to '" + e.getChannel().getRemoteAddress() + "' " + ping);
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("ping responsed from '" + e.getChannel().getRemoteAddress() + "' "
                            + ping);
                }
            }
            if (pingListener != null) {
                pingListener.pingArrived(ping);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("there's not pingListener for ping arriving:" + ping);
                }
            }
        } else {
            super.messageReceived(ctx, e);
        }
    }
}
