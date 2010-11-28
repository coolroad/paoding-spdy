package net.paoding.spdy.common.frame.frames;

import java.util.Map;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public interface HeaderStreamFrame extends StreamFrame {

    /**
     * 返回当前所有headers
     * 
     * @return 有可能返回的是一个不可修改的Map
     */
    public Map<String, String> getHeaders();

    /**
     * 设置该stream的headers
     * 
     * @param headers
     */
    public void setHeaders(Map<String, String> headers);

    /**
     * 返回某个头的值，如果没有则返回null
     * 
     * @param name
     * @return
     */
    public String getHeader(String name);
}
