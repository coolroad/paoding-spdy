package net.paoding.spdy.common.frame.frames;

/**
 * 
 * 表示那些可组成为stream的frame
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public interface StreamFrame extends SpdyFrame {

    /**
     * 返回该frame的所属的streamId
     * 
     * @return
     */
    public int getStreamId();

    /**
     * 设置该frame的所属的streamId
     * 
     * @param streamId
     */
    public void setStreamId(int streamId);

}
