package net.paoding.spdy.client;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public interface Bootstrap {

    /**
     * 
     * @param host
     * @param port
     * @return
     */
    public HttpFuture<Connector> connect(String host, int port);
}
