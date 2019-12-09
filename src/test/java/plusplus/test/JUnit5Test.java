package plusplus.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import plusplus.util.ScheduledHandler;

public class JUnit5Test {

	@Test
	void simpleTest() {
		ByteBuffer buffer = ByteBuffer.allocate(16);
		buffer.putInt(3);
		buffer.putInt(1);
		buffer.putInt(4);
		buffer.putInt(6);
		
		ScheduledHandler handler = new ScheduledHandler(buffer.array(), null);
		
		assertEquals(buffer.array(), handler.getPacket());
	}
	
}
