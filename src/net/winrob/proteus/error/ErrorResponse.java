package net.winrob.proteus.error;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import net.winrob.commons.saon.EventDispatcher;
import net.winrob.proteus.api.event.RequestErrorEvent;
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
	public static void sendUnmodifiableErrorResponse(EventDispatcher dispatcher, ResponseCode rc, Socket client) {
		sendErrorResponse(dispatcher, rc, client, false);
	}
	
	public static void sendErrorResponse(EventDispatcher dispatcher, ResponseCode rc, Socket client, boolean cancellable) {
		new RequestErrorEvent(cancellable) {

			@Override
			public ResponseCode getResponseCode() {
				return rc;
			}
			
			@Override
			public OutputStream getOutputStream() {
				try {
					return client.getOutputStream();
				} catch (IOException e) {
					e.printStackTrace();
				}
				return null;
			}

			@Override
			protected boolean run() {
				try {
					OutputStream clientOutput = client.getOutputStream();
			        clientOutput.write(("HTTP/1.1 " + rc.getCode() + " " + rc.getName() + "\r\n").getBytes());
			        clientOutput.write(("ContentType: text/html\r\n").getBytes());
			        clientOutput.write("\r\n".getBytes());
					clientOutput.write(("<h1>" + rc.getName() + "</h1>").getBytes());
					clientOutput.write(("<p>" + rc.getDesc() + "</p>").getBytes());
					clientOutput.write("\r\n\r\n".getBytes());
			        clientOutput.flush();
			        client.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				return true;
			}
			
		}.dispatch(dispatcher);
	}
	
	// TODO: Add an error response which is sensitive to the endpoint the error occurs on.

}
