package plusplus.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;


//Class responsible for sending packet after delay expire
public class ScheduledHandler implements Runnable {
	
	private byte[] packet;
	private SocketChannel channel;
	
	public ScheduledHandler(byte[] packet, SocketChannel channel) {
		this.packet = packet;
		this.channel = channel;
	}

	public byte[] getPacket() {
		return packet;
	}

	@Override
	public void run() {
		ByteBuffer buffer = ByteBuffer.wrap(packet);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		
		try {
			this.channel.write(buffer);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
