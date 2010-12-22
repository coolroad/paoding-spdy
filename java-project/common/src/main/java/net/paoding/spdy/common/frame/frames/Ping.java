package net.paoding.spdy.common.frame.frames;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * PING控制帧
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class Ping extends ControlFrame {

    public static final int TYPE = 6;

    /**
     * 
     */
    private static int nextId = 0;

    /**
     * 创建一个给服务器的Ping
     * 
     * @return
     */
    public synchronized static Ping getPingToServer() {
        nextId += 2;
        if (nextId < 0) {
            nextId = 0;
        }
        return new Ping(nextId + 1);
    }

    /**
     * 创建一个给客户端的Ping
     * 
     * @return
     */
    public synchronized static Ping getPingToClient() {
        nextId += 2;
        if (nextId < 0) {
            nextId = 0;
        }
        return new Ping(nextId);
    }

    //-----------------------------------------------------------

    private int id;

    /**
     * 创建一个id尚未设置的ping帧
     */
    public Ping() {
        super(TYPE);
    }

    /**
     * 
     * @param id
     */
    public Ping(int id) {
        this();
        this.id = id;
    }

    /**
     * 返回ping的id
     * 
     * @return
     */
    public int getId() {
        return id;
    }

    /**
     * 设置ping的id
     * 
     * @param pingId
     */
    public void setId(int pingId) {
        this.id = pingId;
    }

    @Override
    public void decodeData(ChannelBuffer buffer, int length) {
        this.id = buffer.readInt();
    }

    @Override
    public void encodeData(ChannelBuffer buffer) {
        buffer.writeInt(id);
    }

    @Override
    public String toString() {
        return String.format("Ping[id={0}, timestamp={1}]", id, getTimestamp());
    }
}
