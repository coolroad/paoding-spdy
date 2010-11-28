package net.paoding.spdy.common.frame.frames;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Header编解码
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class HeaderUtil {

    private static Charset utf8 = Charset.forName("utf8");

    private final static Log logger = LogFactory.getLog(HeaderUtil.class);

    /**
     * 解码
     * 
     * @param buffer
     * @return
     */
    public static Map<String, String> decode(ChannelBuffer buffer) {
        int size = buffer.readUnsignedShort();
        boolean debugEnabled = logger.isDebugEnabled();
        if (debugEnabled) {
            logger.debug("decoding: headers.size=" + size);
        }
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
            if (debugEnabled) {
                logger.debug("decoding: [" + (i + 1) + "/" + size + "] " + name + "='" + value
                        + "'");
            }
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
        boolean debugEnabled = logger.isDebugEnabled();
        if (debugEnabled) {
            logger.debug("encoding: headers.size=" + headers.size());
        }
        int index = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String keyString = entry.getKey().trim();
            byte[] key = keyString.getBytes(utf8);
            byte[] value = entry.getValue().getBytes(utf8);
            buffer.writeShort(key.length);
            buffer.writeBytes(key);
            buffer.writeShort(value.length);
            buffer.writeBytes(value);
            if (debugEnabled) {
                index++;
                logger.debug("encoding: [" + index + "/" + headers.size() + "] " + keyString + "='"
                        + entry.getValue() + "'");
            }
        }
    }

    public static int estimatedLength(Map<String, String> headers) {
        if (headers == null || headers.size() == 0) {
            return 0;
        }
        return headers.size() << 5; //32 = (2shorts+key+value);
    }

}
