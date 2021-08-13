package net.aionstudios.proteus.error;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import net.aionstudios.proteus.response.ResponseCode;

public class ErrorResponse {
	
	public static void sendUnmodifiableErrorResponse(ResponseCode rc, Socket client) {
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
	}

}