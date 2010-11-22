package net.paoding.spdy.client.impl;

import java.util.concurrent.TimeUnit;

import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.HttpFuture;
import net.paoding.spdy.client.HttpFutureListener;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

class HttpFutureImpl<T> implements HttpFuture<T> {

    private Connector connector;

    private ChannelFuture channelFuture;

    private T target;

    HttpFutureImpl(Connector connector, ChannelFuture channelFuture) {
        this.connector = connector;
        this.channelFuture = channelFuture;
    }

    @Override
    public Connector getConnector() {
        return connector;
    }

    @Override
    public T getTarget() {
        return target;
    }

    public void setTarget(T target) {
        this.target = target;
    }

    @Override
    public boolean isDone() {
        return channelFuture.isDone();
    }

    @Override
    public boolean isSuccess() {
        return channelFuture.isSuccess();
    }

    boolean setSuccess() {
        return channelFuture.setSuccess();
    }

    boolean setFailure(Throwable cause) {
        return channelFuture.setFailure(cause);
    }

    boolean cancel() {
        return channelFuture.cancel();
    }

    @Override
    public Throwable getCause() {
        return channelFuture.getCause();
    }

    @Override
    public void addListener(final HttpFutureListener<T> listener) {
        channelFuture.addListener(new ChannelFutureListenerX(listener));
    }

    @Override
    public void removeListener(HttpFutureListener<T> listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpFuture<T> await() throws InterruptedException {
        channelFuture.await();
        return this;
    }

    @Override
    public HttpFuture<T> awaitUninterruptibly() {
        channelFuture.awaitUninterruptibly();
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return channelFuture.await(timeout, unit);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return channelFuture.await(timeoutMillis);
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return channelFuture.awaitUninterruptibly(timeout, unit);
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return channelFuture.awaitUninterruptibly(timeoutMillis);
    }

    class ChannelFutureListenerX implements ChannelFutureListener {

        HttpFutureListener<T> listener;

        public ChannelFutureListenerX(HttpFutureListener<T> listener) {
            this.listener = listener;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            listener.operationComplete(HttpFutureImpl.this);
        }
    }

}
