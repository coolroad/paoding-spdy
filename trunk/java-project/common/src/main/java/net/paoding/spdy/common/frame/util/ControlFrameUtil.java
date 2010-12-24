package net.paoding.spdy.common.frame.util;

/**
 * {@link ControlFrame}相关的工具类
 * 
 * @author weibo.leo@gmail.com
 */
public class ControlFrameUtil {

	/**
	 * 用来提取Control Frame中的version的位运算数字,
	 * 因为Control Frame中前16 bytes的格式如下：
	 * |C| Version(15bits) |
	 * 所以通过0x00007fff可以将Version(15bits)提取出来
	 * 
	 * 注：小于32bit的数据类型在做位运算的时候，会先转换为int型
	 * 再操作。
	 * 
	 */
	private static final int VERSION_EXTRACTOR = 0x00007fff;
	
	/**
	 * 用来设置Control Frame的control bit的位运算数字
	 */
	private static final int CONTROL_BIT_SETTER = 0xffff8000;
	
	/**
	 * 从给定的16 bits数据中去掉C位，提取version
	 * 
	 * @param first16bits
	 * @return
	 */
	public static int extractVersion(short first16bits) {
		return first16bits & VERSION_EXTRACTOR;
	}
	
	/**
	 * 给version设置上C位，并以short(16 bits)返回
	 * 
	 * @param version
	 * @return
	 */
	public static short encodeVersion(int version) {
		return (short) (version | CONTROL_BIT_SETTER);
	}
	
}
