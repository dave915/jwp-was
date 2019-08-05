package webserver;

import com.github.jknack.handlebars.internal.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import webserver.http.HttpRequest;
import webserver.http.HttpResponse;
import webserver.http.HttpStatus;

import java.io.*;
import java.net.Socket;

public class RequestHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);
    private static final String REDIRECT_START_WITH = "redirect:";
    private static final String HEADER_HOST_KEY = "Host";
    private static final String REDIRECT_URL_FORMAT = "http://%s%s";

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        logger.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            DataOutputStream dos = new DataOutputStream(out);
            HttpRequest httpRequest = HttpRequest.parse(new BufferedReader(new InputStreamReader(in)));
            logger.debug("Request {}", httpRequest.getRequestLine());

            HttpResponse httpResponse = getResponse(httpRequest);
            httpResponse.responseByStatus(dos);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    HttpResponse getResponse(HttpRequest httpRequest) {
        String requestPath = httpRequest.getUri().getPath();
        HttpResponse httpResponse = new HttpResponse();

        return FileResponseEnum.getFileResponse(requestPath)
                .orElse(getViewMappingResponse(httpRequest, httpResponse));
    }

    private HttpResponse getViewMappingResponse(HttpRequest httpRequest, HttpResponse httpResponse) {
        String viewName = getViewName(httpRequest, httpResponse);
        if (viewName.startsWith(REDIRECT_START_WITH)) {
            return getRedirectHttpResponse(httpRequest, httpResponse, viewName);
        }

        try {
            httpResponse.setBody(ViewResolver.mapping(httpRequest, httpResponse).getBytes());
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return httpResponse;
    }

    private HttpResponse getRedirectHttpResponse(HttpRequest httpRequest, HttpResponse httpResponse, String viewName) {
        String redirectPath = viewName.replace(REDIRECT_START_WITH, StringUtils.EMPTY);
        String redirectUrl = String.format(REDIRECT_URL_FORMAT, httpRequest.getHeaderValue(HEADER_HOST_KEY), redirectPath);
        httpResponse.setRedirectPath(redirectUrl);
        httpResponse.setHttpStatus(HttpStatus.REDIRECT);
        return httpResponse;
    }

    private String getViewName(HttpRequest httpRequest, HttpResponse httpResponse) {
        return Router.route(httpRequest, httpResponse).orElse(StringUtils.EMPTY);
    }
}
