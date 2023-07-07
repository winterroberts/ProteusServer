package net.winrob.proteus.error;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import net.winrob.commons.saon.EventDispatcher;
import net.winrob.proteus.api.event.http.RequestErrorEvent;
import net.winrob.proteus.api.response.ProteusHttpResponse;
import net.winrob.proteus.api.response.ResponseCode;

/**
 * A class which statically handles error pages.
 * 
 * @author Winter Roberts
 *
 */
public class ErrorResponse {
	
	/**
	 * Display an error response, generally, on the connection.
	 * 
	 * @param rc The ResponseCode to be used by the server.
	 * @param client The connection to send the error response on.
	 */
	public static void sendUnmodifiableErrorResponse(EventDispatcher dispatcher, ResponseCode rc, OutputStream out) {
		sendErrorResponse(dispatcher, rc, out, false);
	}
	
	public static void sendErrorResponse(EventDispatcher dispatcher, ResponseCode rc, OutputStream out, boolean cancellable) {
		sendErrorResponse(dispatcher, rc, out, "<h1>" + rc.getName() + "</h1><p>" + rc.getDesc() + "</p>", cancellable);
	}
	
	public static void sendErrorResponse(EventDispatcher dispatcher, ResponseCode rc, OutputStream out, String message, boolean cancellable) {
		sendErrorResponse(dispatcher, rc, out, message.getBytes(), cancellable);
	}
	
	public static void sendErrorResponse(EventDispatcher dispatcher, ResponseCode rc, OutputStream out, byte[] message, boolean cancellable) {
		new RequestErrorEventImpl(out, rc, message, cancellable).dispatch(dispatcher);
	}
	
	private static class RequestErrorEventImpl extends RequestErrorEvent {
		
		private final OutputStream out;
		private final ResponseCode rc;
		private final byte[] message;
		
		public RequestErrorEventImpl(OutputStream out, ResponseCode rc, byte[] message, boolean cancellable) {
			super(cancellable);
			this.out = out;
			this.rc = rc;
			this.message = message;
		}
		
		@Override
		public ResponseCode getResponseCode() {
			return rc;
		}
		
		@Override
		public OutputStream getOutputStream() {
			return out;
		}

		@Override
		protected boolean run() {
			try {
				OutputStream clientOutput = out;
		        clientOutput.write(("HTTP/1.1 " + rc.getCode() + " " + rc.getName() + "\r\n").getBytes());
		        clientOutput.write(("ContentType: text/html\r\n").getBytes());
		        clientOutput.write("\r\n".getBytes());
		        clientOutput.write(message);
				clientOutput.write("\r\n\r\n".getBytes());
		        clientOutput.flush();
		        clientOutput.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return true;
		}
		
	}

}
