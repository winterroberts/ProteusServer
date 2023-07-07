package net.winrob.proteus.api.response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import net.winrob.proteus.ProteusServer;
import net.winrob.proteus.api.event.http.ClientKeepAliveEvent;
import net.winrob.proteus.api.event.http.SendResponseHeadersEvent;
import net.winrob.proteus.compression.CompressionEncoding;
import net.winrob.proteus.compression.Compressor;
import net.winrob.proteus.error.ErrorResponse;
import net.winrob.proteus.header.ProteusHeaderBuilder;
import net.winrob.proteus.util.FormatUtils;

public class ProteusHttpResponseImpl implements ProteusHttpResponse {
	
	private OutputStream outputStream;
	private CompressionEncoding encoding;
	
	private ProteusHeaderBuilder headerBuilder;
	
	private String mimeString;
	private Long modified = null;
	
	private ProteusServer server;
	
	// TODO cookies, special headers
	// TODO range request (which will be in an implementer...) but file support may also be native for pass-through.
	private boolean complete = false;
	
	private ClientKeepAliveEvent keepAlive;
	
	/**
	 * Creates a new ProteusHttpResponse with the given encoding and output stream (from client socket).
	 * 
	 * @param outputStream The output stream to be used when writing the response.
	 * @param encoding The {@link CompressionEncoding} to be used when writing the response.
	 */
	public ProteusHttpResponseImpl(ProteusServer server, ClientKeepAliveEvent keepAlive, OutputStream outputStream, CompressionEncoding encoding) {
		this.outputStream = outputStream;
		this.encoding = encoding;
		this.mimeString = "text/html";
		headerBuilder = ProteusHeaderBuilder.newBuilder();
		this.server = server;
		this.keepAlive = keepAlive;
	}
	
	@Override
	public void sendResponse(byte[] response) {
		sendResponse(ResponseCode.OK, response);
	}
	
	@Override
	public void sendResponse(String response) {
		sendResponse(ResponseCode.OK, response);
	}
	
	@Override
	public void sendResponse(ResponseCode responseCode, String response) {
		sendResponse(responseCode, response.getBytes());
	}
	
	@Override
	public void sendResponse(ResponseCode responseCode, byte[] response) {
		sendResponse(responseCode, new ByteArrayInputStream(response));
	}
	
	@Override
	public void sendResponse(InputStream response) {
		sendResponse(ResponseCode.OK, response);
	}

	@Override
	public void sendResponse(ResponseCode responseCode, InputStream response) {
		sendResponse(responseCode, response, false);
	}
	
	@Override
	public void sendResponse(ResponseCode responseCode, InputStream response, boolean ignoreCompressionDirective) {
		if (!complete) {
			complete = true;
			headerBuilder.putHeader("Server", "Proteus");
			if (keepAlive != null) {
				headerBuilder.putHeader("Connection", "Keep-Alive");
				headerBuilder.putHeader("Keep-Alive", "timeout=30, max=100");
			}
			headerBuilder.putHeader("Content-Type", mimeString);
			headerBuilder.putHeader("Last-Modified", FormatUtils.getLastModifiedAsHTTPString(modified != null ? modified : System.currentTimeMillis()));
			new SendResponseHeadersImpl(headerBuilder, responseCode, ignoreCompressionDirective ? CompressionEncoding.NONE : encoding, response).dispatch(server.getEventDispatcher());
		}
	}
	
	private void sendChunkedResponseHeaders(ResponseCode responseCode, CompressionEncoding ce) throws IOException {
		outputStream.write(("HTTP/1.1 " + responseCode.getCode() + " " + responseCode.getName() + "\r\n").getBytes());
		if (ce != CompressionEncoding.NONE) {
			headerBuilder.putHeader("Content-Encoding", ce.getName());
		}
		outputStream.write(("Transfer-Encoding: " + (ce != CompressionEncoding.NONE ? ce.getName() + ", " : "") + "chunked\r\n").getBytes());
        for (String header : headerBuilder.toList()) {
        	outputStream.write((header + "\r\n").getBytes());
        }
        outputStream.write("\r\n".getBytes());
	}
	
	private void sendResponseHeaders(ResponseCode responseCode, CompressionEncoding ce, int length) throws IOException {
		outputStream.write(("HTTP/1.1 " + responseCode.getCode() + " " + responseCode.getName() + "\r\n").getBytes());
		if (ce != CompressionEncoding.NONE) {
			headerBuilder.putHeader("Content-Encoding", encoding.getName());
		}
		outputStream.write(("Content-Length: " + length + "\r\n").getBytes());
        for (String header : headerBuilder.toList()) {
        	outputStream.write((header + "\r\n").getBytes());
        }
        outputStream.write("\r\n".getBytes());
	}
	
	/**
	 * Closes the given stream safely to prevent errors in data integrity and thread crashes.
	 * @param os	The output stream to be closed.
	 */
	private static void safeCloseStream(OutputStream os) {
		try {
			os.flush();
		} catch (Exception e) {
			//ignore
		}
		try {
			os.close();
		} catch (Exception e) {
			//ignore
		}
	}
	
	private static void safeFlushStream(OutputStream os) {
		try {
			os.flush();
		} catch (Exception e) {
			//ignore
		}
	}
	
	@Override
	public void setModified(long time) {
		if (!complete) {
			modified = time;
		}
	}
	
	@Override
	public void setMimeString(String mime) {
		if (!complete) {
			mimeString = mime;
		}
	}
	
	private boolean sent;
	
	public boolean isCompleted() {
		return sent;
	}
	
	public class SendResponseHeadersImpl extends SendResponseHeadersEvent {
		
		private InputStream in;
		
		private ProteusHeaderBuilder headerBuilder;
		private ResponseCode rc;
		private CompressionEncoding ce;
		
		public SendResponseHeadersImpl(ProteusHeaderBuilder headerBuilder, ResponseCode rc, CompressionEncoding ce, InputStream in) {
			this.in = in;
			this.headerBuilder = headerBuilder;
			this.ce = ce;
			this.rc = rc;
		}

		@Override
		public ProteusHeaderBuilder getHeaderBuilder() {
			return headerBuilder;
		}

		@Override
		public ResponseCode getResponseCode() {
			return rc;
		}

		@Override
		protected boolean run() {
			try {
				sent = true;
				byte[] compressed = Compressor.compress(in, ce);
				InputStream s = new ByteArrayInputStream(compressed);
				byte[] bytes = s.readNBytes(65536);
				if (bytes.length < 65536) {
					sendResponseHeaders(rc, encoding != ce ? encoding : ce, bytes.length);
					outputStream.write(bytes);
					if (keepAlive != null) keepAlive.dispatch(server.getEventDispatcher());
					return true;
				} else if (bytes.length > 0) {
					sendChunkedResponseHeaders(rc, encoding != ce ? encoding : ce);
					do {
						outputStream.write((Integer.toHexString(bytes.length) + "\r\n").getBytes());
						outputStream.write(bytes);
						outputStream.write("\r\n".getBytes());
					} while ((bytes = s.readNBytes(65536)).length > 0);
					outputStream.write("0\r\n".getBytes());
					outputStream.write("\r\n".getBytes());
					if (keepAlive != null) keepAlive.dispatch(server.getEventDispatcher());
					return true;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		}
		
	}

	@Override
	public void error(ResponseCode code) {
		ErrorResponse.sendErrorResponse(server.getEventDispatcher(), code, outputStream, true);
	}

	@Override
	public ProteusHeaderBuilder getHeaderBuilder() {
		return headerBuilder;
	}

	@Override
	public void error(ResponseCode code, String message) {
		ErrorResponse.sendErrorResponse(server.getEventDispatcher(), code, outputStream, message, true);
	}

	@Override
	public void error(ResponseCode code, byte[] message) {
		ErrorResponse.sendErrorResponse(server.getEventDispatcher(), code, outputStream, message, true);
	}

}
