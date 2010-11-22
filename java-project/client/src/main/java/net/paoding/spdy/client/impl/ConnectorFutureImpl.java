package net.paoding.spdy.client.impl;

import org.jboss.netty.channel.ChannelFuture;

import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.ConnectorFuture;

public class ConnectorFutureImpl extends HttpFutureImpl<Connector> implements ConnectorFuture {

    ConnectorFutureImpl(Connector conn, ChannelFuture channelFuture) {
        super(conn, channelFuture);
    }

}
