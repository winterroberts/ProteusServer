package net.aionstudios.proteus.api.websocket;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import net.aionstudios.proteus.api.request.ProteusWebSocketConnection;

/**
 * An individual frame for transmission to the client.
 * 
 * @author Winter Roberts
 *
 */
public class ServerFrameImpl implements ServerFrame {
	
	private OpCode opCode;
	private byte[] payload;
	private boolean finished;
	
	private Callback callback;

	protected ServerFrameImpl(OpCode opCode, byte[] payload, boolean finished) {
		this.opCode = opCode;
		this.payload = payload;
		this.finished = finished;
	}
	
	protected ServerFrameImpl(OpCode opCode, byte[] payload, boolean finished, Callback callback) {
		this.opCode = opCode;
		this.payload = payload;
		this.finished = finished;
		this.callback = callback;
	}
	
	/**
	 * @return The byte indicating the op code to be transmitted.
	 */
	public byte getOpByte() {
		return (byte) ((byte) opCode.getValue() | (finished ? 0x80 : 0x00));
	}
	
	/**
	 * Calls the callback function (after this frame is sent).
	 */
	public void callback() {
		callback.call();
	}
	
	/**
	 * @return True if this is a single frame or the last frame in a continuation, false otherwise.
	 */
	public boolean isEnd() {
		return finished;
	}
	
	/**
	 * @return The encoded length of the message.
	 */
	public byte[] getLengthBytes() {
		ByteBuffer bytes;
		if (payload.length > 125) {
			if (payload.length > 65535) {
				bytes = ByteBuffer.allocate(5);
				bytes.put((byte) 127);
				bytes.putLong(payload.length & 0x7FFFFFFFFFFFFFFL);
			} else {
				bytes = ByteBuffer.allocate(3);
				bytes.put((byte) 126);
				bytes.putChar((char) (payload.length & 0xFFFF)); // Char is unsigned 16 (unsigned short).
			}
		} else {
			bytes = ByteBuffer.allocate(1);
			bytes.put((byte) payload.length);
		}
		return bytes.array();
	}
	
	/**
	 * @return The payload of this frame.
	 */
	public byte[] getPayload() {
		return payload;
	}
	
	/**
	 * Creates a new closing server frame.
	 * 
	 * @param code The {@link ClosingCode} to be used.
	 * @param message The closing reason message.
	 * @param callback The callback (or null) to be called after this frame is sent.
	 * @return A closing server frame.
	 */
	public static ServerFrame closingFrame(ClosingCode code, String message, Callback callback) {
		message = message == null ? "" : message;
		callback = callback == null ? Callback.NULL : callback;
		byte[] pMessage = message.getBytes(StandardCharsets.UTF_8);
		if (pMessage.length > ProteusWebSocketConnection.FRAME_MAX - 2) {
			throw new IllegalArgumentException("Closing message (size " + (pMessage.length + 2) + ") exceeds maximum frame size!");
		}
		ByteBuffer payload = ByteBuffer.allocate(2 + pMessage.length);
		payload.putShort(code.getCode());
		payload.put(pMessage);
		return new ServerFrameImpl(OpCode.CLOSE, payload.array(), true, callback);
	}
	
	/**
	 * Creates a new ping server frame.
	 * 
	 * @param message The ping message.
	 * @param callback The callback (or null) to be called after this frame is sent.
	 * @return A ping server frame.
	 */
	public static ServerFrame pingFrame(String message, Callback callback) {
		message = message == null ? "" : message;
		callback = callback == null ? Callback.NULL : callback;
		byte[] payload = message.getBytes(StandardCharsets.UTF_8);
		if (payload.length > 125) {
			throw new IllegalArgumentException("Ping message (size " + payload.length + ") exceeds control frame size!");
		}
		return new ServerFrameImpl(OpCode.PING, payload, true, callback);
	}
	
	/**
	 * Creates a new pong server frame.
	 * 
	 * @param message The pong message (from the ping which prompted it).
	 * @param callback The callback (or null) to be called after this frame is sent.
	 * @return A pong server frame.
	 */
	public static ServerFrame pongFrame(String message, Callback callback) {
		message = message == null ? "" : message;
		callback = callback == null ? Callback.NULL : callback;
		byte[] payload = message.getBytes(StandardCharsets.UTF_8);
		if (payload.length > 125) {
			throw new IllegalArgumentException("Pong message (size " + payload.length + ") exceeds control frame size!");
		}
		return new ServerFrameImpl(OpCode.PONG, payload, true, callback);
	}

	/**
	 * A class intended to call the function within it after another task is completed.
	 * 
	 * @author Winter Roberts
	 *
	 */
	public interface Callback {
		
		public static Callback NULL = new Callback() {

			@Override
			public void call() {
				// do nothing
			}
			
		};

		/**
		 * The method that will be run after the previous task, which accepts a {@link Callback}, is run.
		 */
		public void call();
		
	}

	
}
