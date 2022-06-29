package net.aionstudios.proteus.api.websocket;

import java.nio.ByteBuffer;

import net.aionstudios.proteus.api.request.ProteusWebSocketConnection;
import net.aionstudios.proteus.api.websocket.ServerFrameImpl.Callback;
import net.aionstudios.proteus.util.StreamUtils;

/**
 * Used to construct server frames from data.
 * 
 * @author Winter Roberts
 *
 */
public class ServerFrameBuilder {
	
	private ByteBuffer byteBuffer;
	private int bufferSize;
	
	private OpCode opCode;
	
	private Callback callback = null;

	private ServerFrameBuilder(OpCode opCode) {
		byteBuffer = ByteBuffer.allocate(0);
		if (opCode == null || opCode.getValue() < 0x3) {
			throw new NullPointerException("OpCode invalid (" + (opCode == null ? "NULL" : opCode.getValue()) + ")");
		}
		this.opCode = opCode;
	}
	
	/**
	 * Creates a new frame builder.
	 * @param opCode The {@link OpCode} of the first frame, which may change if this server frame builder uses continuation frames.
	 * @return A new server frame builder.
	 */
	public static ServerFrameBuilder newFrameBuilder(OpCode opCode) {
		return new ServerFrameBuilder(opCode);
	}
	
	/**
	 * Sets the callback to be used by the last frame built from this server frame builder.
	 * @param callback The {@link Callback} which may be null.
	 */
	public void setCallback(Callback callback) {
		this.callback = callback;
	}
	
	/**
	 * Adds the following bytes to the server frame if possible.
	 * 
	 * @param bytes The bytes to be added.
	 * @return True if the bytes did not exceed the length of the buffer, false otherwise.
	 */
	public boolean addBytes(byte[] bytes) {
		if (bytes.length + bufferSize <= ProteusWebSocketConnection.MAX_SIZE) {
			byteBuffer = StreamUtils.joinByteArrayToBuffer(byteBuffer.array(), bytes);
			bufferSize += bytes.length;
			return true;
		}
		return false;
	}
	
	/**
	 * Builds the current state of the server frame builder into one or more server frames.
	 * @return The {@link ServerFrame}s encoding the data in this builder.
	 */
	public ServerFrame[] build() {
		int maxSize = ProteusWebSocketConnection.MAX_SIZE;
		int maxFrame = ProteusWebSocketConnection.FRAME_MAX;
		boolean divisible = maxSize % maxFrame == 0;
		ServerFrame[] frames = new ServerFrameImpl[(maxSize / maxFrame) + (!divisible ? 1 : 0)];
		for (int i = 0; i < frames.length; i++) {
			int readLength = (i + 1) == frames.length ? bufferSize % maxFrame : maxFrame;
			byte[] frameBytes = new byte[readLength];
			byteBuffer.get(frameBytes, i * maxFrame, readLength);
			frames[i] = new ServerFrameImpl(opCode, frameBytes, (i + 1) == frames.length, callback);
		}
		return frames;
	}
	
}
