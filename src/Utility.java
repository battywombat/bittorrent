import java.nio.ByteBuffer;

public class Utility {

	static String escapeString(ByteBuffer stringBuffer) {
		StringBuffer sb = new StringBuffer();
		String hex;
		for(byte b : stringBuffer.array()) {
			hex = Integer.toHexString((char)(b & 0xFF));
			if (hex.length() < 2) {
				hex = "0"+hex;
			}
			sb.append("%"+hex);
		}
		return sb.toString();
	}
	
	static String escapeString(String s) {
		return escapeString(ByteBuffer.wrap(s.getBytes()));
	}
}
