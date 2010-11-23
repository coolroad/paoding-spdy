package net.paoding.spdy.common.frame;

import net.paoding.spdy.common.frame.frames.Ping;

/**
 * Ping侦听器，当有 ping 到达的时候，此侦听器将能够收到该ping信息
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public interface PingListener {

    /**
     * 当有ping到达或写回的时候，此方法将被调用；
     * <p>
     * 如果该ping需要被echo，这个动作已经由框架完成了，此方法不用重复执行这个工作。
     * 
     * @param ping
     */
    public void pingArrived(Ping ping);

}
