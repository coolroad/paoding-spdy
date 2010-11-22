package net.paoding.spdy.client;

public interface HttpFutureListener<T> {

    void operationComplete(HttpFuture<T> httpFuture) throws Exception;
}
