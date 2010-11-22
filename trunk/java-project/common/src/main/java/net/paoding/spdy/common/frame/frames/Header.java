package net.paoding.spdy.common.frame.frames;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Header编解码
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class Header {

    private static Charset utf8 = Charset.forName("utf8");

    /**
     * 解码
     * 
     * @param buffer
     * @return
     */
    public static Map<String, String> decode(ChannelBuffer buffer) {
        int size = buffer.readUnsignedShort();
        //        System.out.println("Header.decode headers.size=" + size);
        Map<String, String> pairs = new HashMap<String, String>(size);
        for (int i = 0; i < size; i++) {
            int nameLength = buffer.readUnsignedShort();
            String name = new String(buffer.array(), buffer.readerIndex(), nameLength, utf8)
                    .toLowerCase();
            buffer.skipBytes(nameLength);
            int valueLength = buffer.readUnsignedShort();
            String value = new String(buffer.array(), buffer.readerIndex(), valueLength, utf8);
            buffer.skipBytes(valueLength);
            pairs.put(name, value);
            //            System.out.println("Header.decode put " + name + "(" + nameLength + ")=" + value + "("
            //                    + valueLength + ")");
        }
        return pairs;
    }

    /**
     * 编码
     * 
     * @param headers
     * @param buffer
     */
    public static void encode(Map<String, String> headers, ChannelBuffer buffer) {
        buffer.writeShort(headers.size());
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String keyString = entry.getKey().trim();
            byte[] key = keyString.getBytes(utf8);
            byte[] value = entry.getValue().getBytes(utf8);
            buffer.writeShort(key.length);
            buffer.writeBytes(key);
            buffer.writeShort(value.length);
            buffer.writeBytes(value);
            //            System.out.println("Header.encode " + keyString + "(" + key.length + ")="
            //                    + entry.getValue() + " (" + value.length + ")");
        }
    }

}
