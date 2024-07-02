package net.winrob.proteus.api.request;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import net.winrob.proteus.api.Constants;
import net.winrob.proteus.api.h2.FrameType;
import net.winrob.proteus.api.h2.H2Setting;
import net.winrob.proteus.api.h2.Stream;
import net.winrob.proteus.api.h2.StreamImpl;
import net.winrob.proteus.routing.CompositeRouter;

public class ProteusH2ConnectionImpl {
	
	private final Socket client;
	private final CompositeRouter router;
	
	private final InputStream inputStream;
	private final OutputStream outputStream;
	
	private Map<Integer, StreamImpl> streams;
	private Map<H2Setting, Integer> settings;
	
	public ProteusH2ConnectionImpl(Socket client, CompositeRouter router) throws IOException {
		this.client = client;
		this.router = router;
		this.inputStream = client.getInputStream();
		this.outputStream = client.getOutputStream();
		streams = new HashMap<>();
	}
	
	public void start() throws IOException {	
		streams.put(0, new StreamImpl(this, 0));
		outputStream.write(Constants.CONNECTION_PREFACE.getBytes());
		
		Thread reply = new Thread(() -> {
			try {
				writeLoop();
				client.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		reply.start();
		readLoop();
	}
	
	public void readLoop() {
		boolean firstSettings = false;
		while (true) {
			try {
				int length = ((byte) inputStream.read() << 16) + ((byte) inputStream.read() << 8) + ((byte) inputStream.read());
				if (length > Constants.SETTINGS_MAX_FRAME_SIZE) {
					// TODO FRAME_SIZE_ERROR
				}
				
				FrameType type = FrameType.forValue((byte) inputStream.read());
				if (firstSettings == false && type != FrameType.SETTINGS) {
					// TODO PROTOCOL_ERROR
				}
				if (type == null) return;
				
				byte flags = (byte) inputStream.read();
				
				ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
				b.put(inputStream.readNBytes(4));
				b.rewind();
				int streamID = b.getInt();
				boolean reserved = ((streamID >> 63) & 1) == 1;
				streamID = streamID & 0x7FFFFFFF;
				
				if (streamID == 0 && !type.isCtrlFrame()) {
					// TODO PROTOCOL_ERROR
				}
				
				StreamImpl stream = streams.get(Integer.valueOf(streamID));
				if (stream == null) {
					if (type == FrameType.HEADERS) {
						stream = new StreamImpl(this, streamID);
						streams.put(streamID, stream);
					} else {
						// TODO PROTOCOL_ERROR
					}
				}
				if (!type.allows(stream.getState())) {
					// TODO STREAM_CLOSED
				}
				
				stream.readFrame(type, length, flags, inputStream);
			} catch (IOException e) {
				// TODO handle error
				e.printStackTrace();
			}
		}
	}
	
	public void writeLoop() {
		
	}
	
	public void settingUpdate(H2Setting setting, int value) {
		// TODO test okay
		settings.put(setting, value);
	}

}
