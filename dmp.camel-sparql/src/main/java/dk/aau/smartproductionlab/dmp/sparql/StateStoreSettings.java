/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.sparql;

import java.net.URI;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The one state store the DMP writes observed state to: a SPARQL 1.1 Update endpoint ({@value #UPDATE_URL}). A
 * StateDeclaration's {@code Target/Endpoint} names the store by its origin, which must be this endpoint's (scheme,
 * host and port); the update path is the deployment's (Oxigraph {@code /update}, Fuseki {@code /<dataset>/update}).
 * {@code timeout} bounds one update request.
 */
public record StateStoreSettings(URI update, Duration timeout) {
	public static final String UPDATE_URL = "DMP_STATE_STORE_UPDATE_URL";
	public static final String TIMEOUT = "DMP_STATE_STORE_TIMEOUT";
	public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

	public StateStoreSettings(URI update) { this(update, DEFAULT_TIMEOUT); }

	public StateStoreSettings {
		Objects.requireNonNull(update, "update");
		if (update.getHost() == null || update.getUserInfo() != null || update.getQuery() != null || update.getFragment() != null
				|| !("http".equalsIgnoreCase(update.getScheme()) || "https".equalsIgnoreCase(update.getScheme())))
			throw new IllegalArgumentException(UPDATE_URL + " must be a credential-free HTTP(S) URI without a query");
		if (timeout == null || timeout.toMillis() < 1)
			throw new IllegalArgumentException(TIMEOUT + " must be a positive ISO-8601 duration of at least one millisecond");
	}

	/** The configured state store, or empty when {@value #UPDATE_URL} is unset. */
	public static Optional<StateStoreSettings> fromEnvironment(Map<String, String> environment) {
		String url = environment.get(UPDATE_URL);
		if (url == null || url.isBlank()) return Optional.empty();
		String timeout = environment.get(TIMEOUT);
		Duration bound;
		try { bound = timeout == null || timeout.isBlank() ? DEFAULT_TIMEOUT : Duration.parse(timeout.strip()); }
		catch (DateTimeParseException invalid) {
			throw new IllegalArgumentException(TIMEOUT + " must be an ISO-8601 duration such as PT5S, not " + timeout);
		}
		return Optional.of(new StateStoreSettings(URI.create(url.strip()), bound));
	}

	/** Whether a StateDeclaration's {@code Target/Endpoint} names this store: the same scheme, host and port. */
	public boolean serves(URI endpoint) {
		if (endpoint == null || endpoint.getHost() == null || endpoint.getScheme() == null) return false;
		return endpoint.getScheme().equalsIgnoreCase(update.getScheme()) && endpoint.getHost().equalsIgnoreCase(update.getHost())
				&& port(endpoint) == port(update);
	}

	private static int port(URI uri) {
		if (uri.getPort() >= 0) return uri.getPort();
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}
}
