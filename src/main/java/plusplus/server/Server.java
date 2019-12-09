package plusplus.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Random;

public class Server implements Runnable {

	private Thread listener;
	private Random random;

	public Server() {
		this.random = new Random();
	}

	@Override
	public void run() {

		try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

			serverChannel.socket().bind(new InetSocketAddress(InetAddress.getLocalHost(), 8888));

			SocketChannel clientChannel = serverChannel.accept();

			this.listener = new Thread(() -> {
				ByteBuffer buffer = ByteBuffer.allocate(16);

				while (true) {

					try {
						clientChannel.read(buffer);
					} catch (IOException e) {
						e.printStackTrace();
						continue;
					}

					buffer.order(ByteOrder.LITTLE_ENDIAN);

					buffer.flip();
					
					if (buffer.limit() != 16) {
						break;
					}
					
					System.out.println("\t\t\t\tServer:\n\t\t\t\t" + buffer.getInt() + " | " + buffer.getInt() + " | "
							+ buffer.getInt() + " | " + buffer.getInt() + "\n\t\t\t\t------------------------");

					buffer.clear();
				}
			});
			this.listener.start();

			for (int i = 0; i < 10; i++) {
				if (i == 9) {
					ByteBuffer buffer = ByteBuffer.allocate(12);
					buffer.order(ByteOrder.LITTLE_ENDIAN);
					buffer.putInt(2);
					buffer.putInt(12);
					buffer.putInt(Integer.MAX_VALUE - random.nextInt(100));
					buffer.flip();
					clientChannel.write(buffer);
					continue;
				}

				ByteBuffer buffer = ByteBuffer.allocate(16);
				buffer.order(ByteOrder.LITTLE_ENDIAN);
				buffer.putInt(1);
				buffer.putInt(16);
				buffer.putInt(Integer.MAX_VALUE - random.nextInt(100));
				buffer.putInt(random.nextInt(10));
				buffer.flip();
				clientChannel.write(buffer);
				Thread.sleep(1000);

			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

}
