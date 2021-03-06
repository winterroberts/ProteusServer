package net.aionstudios.proteus.api.request;

import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import net.aionstudios.proteus.api.context.ProteusWebSocketContext;
import net.aionstudios.proteus.header.ProteusHttpHeaders;
import net.aionstudios.proteus.routing.CompositeRouter;
import net.aionstudios.proteus.routing.Hostname;
import net.aionstudios.proteus.routing.PathComprehension;
import net.aionstudios.proteus.routing.WebSocketRoute;
import net.aionstudios.proteus.util.RequestUtils;

public class ProteusWebSocketRequestImpl implements ProteusWebSocketRequest {
	
	private Hostname hostname;
	
	private ProteusHttpHeaders headers;
	
	private RequestBody body;
	private ParameterMap<String> urlParameters;
	private ParameterMap<String> cookies; // TODO cookies
	
	private WebSocketRoute route;
	
	/**
	 * Constructs a new ProteusWebSocketRequest object.
	 * 
	 * @param client The client socket connection (for input and output streams).
	 * @param path The path of this request.
	 * @param host The host (domain or ip) this request targeted.
	 * @param headers The {@link ProteusHttpHeaders} of this request.
	 * @param router The {@link CompositeRouter} used by the endpoint to resolve the path request.
	 */
	public ProteusWebSocketRequestImpl(Socket client, String path, String host, ProteusHttpHeaders headers, CompositeRouter router) {
		this.hostname = new Hostname(host);
		this.headers = headers;
		route = router.getWebSocketRoute(hostname, resolveURI(path));
		cookies = headers.hasHeader("Cookie") ? headers.getHeader("Cookie").getFirst().getParams() : null;
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
	
	public WebSocketRoute getRoute() {
		return route;
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
	public ProteusWebSocketContext getContext() {
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
	
}
