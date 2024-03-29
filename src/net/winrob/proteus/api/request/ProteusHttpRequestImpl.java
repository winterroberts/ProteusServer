package net.winrob.proteus.api.request;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import net.winrob.commons.saon.EventDispatcher;
import net.winrob.proteus.api.context.ProteusHttpContext;
import net.winrob.proteus.api.request.ParameterMap;
import net.winrob.proteus.api.request.ProteusHttpRequest;
import net.winrob.proteus.api.request.RequestBody;
import net.winrob.proteus.header.ProteusHttpHeaders;
import net.winrob.proteus.routing.CompositeRouter;
import net.winrob.proteus.routing.Hostname;
import net.winrob.proteus.routing.HttpRoute;
import net.winrob.proteus.routing.PathComprehension;
import net.winrob.proteus.util.RequestUtils;

public class ProteusHttpRequestImpl implements ProteusHttpRequest {
	
	private InputStream inputStream;
	
	private String remoteAddress;
	
	private String method;
	private String httpVersion;
	private String path;
	private Hostname hostname;
	
	private ProteusHttpHeaders headers;
	
	private RequestBody body;
	private ParameterMap<String> urlParameters;
	private ParameterMap<String> cookies; // TODO Flesh out cookies!
	
	private HttpRoute route;
	
	private EventDispatcher dispatcher;
	
	/**
	 * Constructs a new ProteusHttpRequest object.
	 * 
	 * @param client The client socket connection (for input and output streams).
	 * @param method The HTTP method specified by this request.
	 * @param httpVersion The HTTP Version of this request (1.1 is supported).
	 * @param path The path of this request.
	 * @param headers The {@link ProteusHttpHeaders} of this request.
	 * @param router The {@link CompositeRouter} used by the endpoint to resolve the path request.
	 */
	public ProteusHttpRequestImpl(Socket client, String method, String httpVersion, String path, ProteusHttpHeaders headers, CompositeRouter router, EventDispatcher dispatcher) {
		try {
			this.inputStream = client.getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.remoteAddress = client.getInetAddress().toString();
		this.method = method;
		this.httpVersion = httpVersion;
		this.path = path;
		this.hostname = new Hostname(headers.getHeader("Host").getFirst().getValue());
		this.headers = headers;
		route = router.getHttpRoute(hostname, resolveURI(path));
		if (method.equals("POST")) {
			body = RequestBodyImpl.createRequestBody(this, inputStream);
		}
		cookies = headers.hasHeader("Cookie") ? headers.getHeader("Cookie").getFirst().getParams() : null;
		if (body != null) {
			ParameterMap<String> post = body.getBodyParams();
			for (String s : post.keySet()) {
				System.out.println(s + ": " + post.getParameter(s));
			}
		}
		this.dispatcher = dispatcher;
	}
	
	// Decomposes the path and query string components of the URI.
	private String resolveURI(String path) {
		String[] requestSplit;
		if(path.contains("?")) {
			requestSplit = path.split("\\?", 2);
		} else {
			requestSplit = new String[2];
			requestSplit[0] = path.toString();
			requestSplit[1] = "";
		}
		Map<String, String> getP = new HashMap<String, String>();
		if(requestSplit.length>1) {
			getP = RequestUtils.resolveQueryString(requestSplit[1]);
		}
		urlParameters = new ParameterMap<>(getP);
		return requestSplit[0];
	}
	
	public HttpRoute getRoute() {
		return route;
	}

	@Override
	public String getMethod() {
		return method;
	}
	
	@Override
	public String getHttpVersion() {
		return httpVersion;
	}
	
	@Override
	public Hostname getHostname() {
		return hostname;
	}

	@Override
	public ParameterMap<String> getUrlParameters() {
		return urlParameters;
	}

	@Override
	public RequestBody getRequestBody() {
		return body;
	}

	@Override
	public ParameterMap<String> getCookies() {
		return cookies;
	}
	
	@Override
	public ProteusHttpHeaders getHeaders() {
		return headers;
	}
	
	@Override
	public String getRemoteAddress() {
		return remoteAddress;
	}
	
	@Override
	public ProteusHttpContext getContext() {
		return route != null ? route.getContext() : null;
	}

	@Override
	public PathComprehension getPathComprehension() {
		return route != null ? route.getPathComprehension() : null;
	}
	
	@Override
	public boolean routed() {
		return route != null;
	}

	@Override
	public EventDispatcher getEventDispatcher() {
		return dispatcher;
	}

	@Override
	public String getPath() {
		return path;
	}
	
}
