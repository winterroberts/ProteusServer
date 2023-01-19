package net.winrob.proteus.api.request;

import java.nio.ByteBuffer;

import net.winrob.proteus.api.request.ProteusWebSocketConnection;
import net.winrob.proteus.api.request.WebSocketBuffer;
import net.winrob.proteus.api.websocket.DataType;
import net.winrob.proteus.util.StreamUtils;

/**
 * Buffers web socket data from one start (and finish) frame which may consist of 0 or more continuation frames.
 * 
 * @author Winter Roberts
 *
 */
public class WebSocketBufferImpl implements WebSocketBuffer {
	
	private ByteBuffer buffer;
	private int bufferSize;
	private DataType dataType;
	
	/**
	 * Creates a new WebSocketBuffer of the specified data type.
	 * @param dataType The {@link DataType} of this WebSocketBuffer.
	 */
	public WebSocketBufferImpl(DataType dataType) {
		buffer = ByteBuffer.allocate(0);
		bufferSize = 0;
		this.dataType = dataType;
	}
	
	@Override
	public boolean put(byte[] data) {
		if (bufferSize + data.length <= ProteusWebSocketConnection.MAX_SIZE) {
			buffer = StreamUtils.joinByteArrayToBuffer(buffer.array(), data);
			bufferSize += data.length;
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public DataType getDataType() {
		return dataType;
	}
	
	@Override
	public int getBufferSize() {
		return bufferSize;
	}
	
	@Override
	public byte[] getData() {
		return buffer.array();
	}
	
}
