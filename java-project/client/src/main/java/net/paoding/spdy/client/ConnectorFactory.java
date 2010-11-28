package net.paoding.spdy.client;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public interface ConnectorFactory {

    /**
     * 
     * @param host
     * @param port
     * @return
     */
    public Connector get(String host, int port);
}
