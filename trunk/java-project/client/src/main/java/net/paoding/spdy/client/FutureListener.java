package net.paoding.spdy.client;

public interface FutureListener<T> {

    void operationComplete(Future<T> future) throws Exception;
}
