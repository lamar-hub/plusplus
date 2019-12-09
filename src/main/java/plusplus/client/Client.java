package plusplus.client;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import plusplus.util.ScheduledHandler;

//Client test application
public class Client implements Runnable {

	// Thread pool
	private ScheduledExecutorService scheduledExecutorService;

	// Hash table that monitor all input packets and its progress
	private HashMap<ScheduledHandler, ScheduledFuture<?>> map;

	// Server hostname
	private String hostname;

	// Server listening port
	private int port;

	// Client side socket channel
	private SocketChannel socketChannel;

	public Client(String hostname, int port, int threadPoolSize) {
		this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
		this.map = new HashMap<ScheduledHandler, ScheduledFuture<?>>();
		this.hostname = hostname;
		this.port = port;
	}

	@Override
	public void run() {

		try (SocketChannel socketChannel = SocketChannel.open()) {

			this.socketChannel = socketChannel;

			// Connecting channel to certain hostname and port
			try {
				InetSocketAddress ia = new InetSocketAddress(this.hostname, this.port);
				this.socketChannel.connect(ia);
//				this.socketChannel.connect(new InetSocketAddress(InetAddress.getLocalHost(), this.port));
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
			


			// Reading file
			this.readFile();

			// Reading packets until cancel packet occurs
			boolean read = true;
			while (read) {

				// Read packet header
				ByteBuffer headerBuffer = this.getByteBuffer(4);
				this.socketChannel.read(headerBuffer);
				headerBuffer.flip();

				if (headerBuffer.getInt(0) == 1) {
					this.proccessDummyPacket();
				}

				if (headerBuffer.getInt(0) == 2) {
					// Stop reading from channel
					read = false;

					this.proccessCancelPacket();

					// Abort all task which are currently in delay time
					this.scheduledExecutorService.shutdownNow();

					this.writeFile();
				}

				headerBuffer.clear();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Read rest 12 bytes of the packet and schedule its for execution
	private void proccessDummyPacket() throws IOException {
		ByteBuffer dummyBuffer = this.getByteBuffer(16);

		dummyBuffer.putInt(1);

		this.socketChannel.read(dummyBuffer);

		dummyBuffer.flip();

		System.out.println("Client:\n" + dummyBuffer.getInt() + " | " + dummyBuffer.getInt() + " | "
				+ dummyBuffer.getInt() + " | " + dummyBuffer.getInt() + "\n------------------------");

		this.schedule(dummyBuffer, -1);
	}

	// Handle task and add it to the scheduler, map all to hashtable
	private void schedule(ByteBuffer dummyBuffer, long delay) {
		ScheduledHandler scheduledHandler = new ScheduledHandler(dummyBuffer.array(), this.socketChannel);

		ScheduledFuture<?> scheduledFuture = this.scheduledExecutorService.schedule(scheduledHandler,
				delay != -1 ? delay : dummyBuffer.getInt(12), delay != -1 ? TimeUnit.MILLISECONDS : TimeUnit.SECONDS);

		this.addToTable(scheduledHandler, scheduledFuture);
	}

	// Optimizing table and insert new task
	private void addToTable(ScheduledHandler scheduledHandler, ScheduledFuture<?> scheduledFuture) {

		// Reduce table size by removing done tasks when table size grow over 15
		// key-value pars
		try {
			if (map.size() > 15)
				for (Iterator<Entry<ScheduledHandler, ScheduledFuture<?>>> iterator = map.entrySet().iterator(); map
						.entrySet().iterator().hasNext();) {
					Entry<ScheduledHandler, ScheduledFuture<?>> entry = iterator.next();
					if (entry.getValue().isDone()) {
						iterator.remove();
					}
				}
		} catch (Exception e) {
		}

		// Insert new task in table
		this.map.put(scheduledHandler, scheduledFuture);
	}

	// Read rest 8 bytes of the packet
	private void proccessCancelPacket() throws IOException {
		ByteBuffer cancelBuffer = this.getByteBuffer(12);

		cancelBuffer.putInt(2);

		this.socketChannel.read(cancelBuffer);

		cancelBuffer.flip();

		System.out.println("Client:\n" + cancelBuffer.getInt() + " | " + cancelBuffer.getInt() + " | "
				+ cancelBuffer.getInt() + "\n------------------------");
	}

	// Allocate new buffer and set byte order to little_endian
	private ByteBuffer getByteBuffer(int capacity) {
		ByteBuffer buffer = ByteBuffer.allocate(capacity);
		buffer.order(ByteOrder.LITTLE_ENDIAN);

		return buffer;
	}

	// Reading packets from binary file and schedule them into executor
	private void readFile() {
		this.scheduledExecutorService.submit(() -> {

			try (RandomAccessFile file = new RandomAccessFile("./src/main/resources/storage.bin", "rw");
					FileChannel fileChannel = file.getChannel()) {

				ByteBuffer buffer = this.getByteBuffer(24);

				int readCount;
				while ((readCount = fileChannel.read(buffer)) != -1) {
					if (readCount == 24) {
						buffer.flip();
						long endDelayTimestamp = buffer.getLong(16);
						long currentTimestamp = System.currentTimeMillis();

						ByteBuffer dummyBuffer = this.getByteBuffer(16);
						dummyBuffer.putInt(0, buffer.getInt());
						dummyBuffer.putInt(4, buffer.getInt());
						dummyBuffer.putInt(8, buffer.getInt());
						dummyBuffer.putInt(12, buffer.getInt());
						dummyBuffer.flip();

						// Schedule packet with new computed delay
						this.schedule(dummyBuffer,
								endDelayTimestamp >= currentTimestamp ? (endDelayTimestamp - currentTimestamp) : 0);
					}
					buffer.clear();
				}

				// Clear file content after reading
				file.setLength(0);

			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			}

		});
	}

	// After cancel packet income save state of packets which delay time did not
	// elapse
	// 24 bytes for every packet |type = 4bytes|len = 4bytes|id = 4bytes|delay =
	// 4bytes|elapseTimestamp = 8bytes|
	private void writeFile() throws IOException {
		try (RandomAccessFile file = new RandomAccessFile("./src/main/resources/storage.bin", "rw");
				FileChannel fileChannel = file.getChannel()) {

			ByteBuffer buffer = this.getByteBuffer(24);
			long currentTimestamp = System.currentTimeMillis();

			this.map.forEach((scheduledHandler, scheduledFuture) -> {

				// Save only ones which is not done
				if (!scheduledFuture.isDone()) {
					buffer.put(scheduledHandler.getPacket());

					// Compute timestamp when delay will eplapse
					buffer.putLong(currentTimestamp + scheduledFuture.getDelay(TimeUnit.MILLISECONDS));

					buffer.flip();

					try {
						fileChannel.write(buffer);
					} catch (IOException e) {
						buffer.clear();
						e.printStackTrace();
					}
					buffer.clear();
				}
			});

			// Clear all table
			this.map.clear();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

}
