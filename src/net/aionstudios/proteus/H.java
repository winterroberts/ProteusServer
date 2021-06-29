package net.aionstudios.proteus;

import net.aionstudios.proteus.api.context.ProteusContext;
import net.aionstudios.proteus.api.context.ProteusHttpContext;
import net.aionstudios.proteus.request.ProteusHttpRequest;
import net.aionstudios.proteus.request.ProteusHttpResponse;

@ProteusContext(path="/h")
public class H extends ProteusHttpContext {

	@Override
	public void handle(ProteusHttpRequest request) {
		ProteusHttpResponse.getResponserForRequest(request).sendResponse("h");
	}

}
