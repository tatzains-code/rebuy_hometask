package com.example.shortener.service;

import com.example.shortener.config.AppProperties;
import com.example.shortener.exception.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/** Parses, validates and canonicalizes a submitted URL; never re-encodes path/query/fragment. */
@Component
public class UrlValidator {

    private final String ownHost;

    public UrlValidator(AppProperties appProperties) {
        this.ownHost = hostOf(appProperties.baseUrl());
    }

    public String normalize(String raw) {
        URI uri = parse(raw.trim());
        String scheme = requireHttpScheme(uri);
        String host = requireNonEmptyHost(uri);
        rejectSelfReference(host);
        return rebuild(uri, scheme.toLowerCase(), host.toLowerCase());
    }

    private static URI parse(String raw) {
        try {
            return new URI(raw);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Not a valid URL: " + raw);
        }
    }

    private static String requireHttpScheme(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidUrlException("URL scheme must be http or https");
        }
        return scheme;
    }

    private static String requireNonEmptyHost(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL must have a host");
        }
        return host;
    }

    private void rejectSelfReference(String host) {
        if (host.equalsIgnoreCase(ownHost)) {
            throw new InvalidUrlException("Cannot shorten a URL pointing back at this service");
        }
    }

    private static String rebuild(URI uri, String scheme, String host) {
        StringBuilder result = new StringBuilder(scheme).append("://").append(host);

        int port = uri.getPort();
        boolean isDefaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
        if (port != -1 && !isDefaultPort) {
            result.append(':').append(port);
        }

        appendIfPresent(result, "", uri.getRawPath());
        appendIfPresent(result, "?", uri.getRawQuery());
        appendIfPresent(result, "#", uri.getRawFragment());
        return result.toString();
    }

    private static void appendIfPresent(StringBuilder result, String prefix, String value) {
        if (value != null) {
            result.append(prefix).append(value);
        }
    }

    private static String hostOf(String url) {
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("app.base-url is not a valid URL: " + url, e);
        }
    }
}
