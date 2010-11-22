package net.paoding.spdy.common.frame;

import java.util.concurrent.Executor;

import net.paoding.spdy.common.frame.frames.Ping;

import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

/**
 * ping处理器
 * <p>
 * @author qieqie.wang@gmail.com
 * 
 */
@Sharable
public class PingExecution extends SimpleChannelHandler {

    private PingListener pingListener;

    private Executor executor;

    public PingExecution() {
    }

    public PingExecution(Executor executor, PingListener pingListener) {
        setExecutor(executor);
        setPingListener(pingListener);
    }

    public void setPingListener(PingListener pingListener) {
        this.pingListener = pingListener;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof Ping) {
            final Ping ping = (Ping) msg;
            //System.out.println("ping received: " + e.getChannel().getRemoteAddress() + " " + ping.getId());
            if (ping.getId() % 2 == 1) {
                // Receivers of a PING frame should send an identical frame 
                // to the sender as soon as possible echo
                Channels.write(e.getChannel(), ping);
            }
            if (pingListener != null) {
                if (executor == null) {
                    pingListener.pingArrived(ping);
                } else {
                    executor.execute(new Runnable() {

                        @Override
                        public void run() {
                            pingListener.pingArrived(ping);
                        }
                    });
                }
            }
        } else {
            super.messageReceived(ctx, e);
        }
    }
}
