package net.paoding.spdy.common.frame;

import net.paoding.spdy.common.frame.frames.Ping;

public interface PingListener {

    public void pingArrived(Ping ping);

}
