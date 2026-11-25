/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.sparql;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import dk.aau.smartproductionlab.dmp.sparql.StateTerms.Cardinality;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.ValueType;

/**
 * The SPARQL update that sets one state variable to what its mapping produced: the variable's values replaced as a
 * whole (the old ones deleted, the new ones inserted; none clears it), and its provenance record replaced with this
 * write's. One request, so the store never shows half of it.
 *
 * <pre>
 *   GRAPH &lt;graph&gt;            &lt;subject&gt; &lt;property&gt; value .
 *   GRAPH &lt;graph&gt;/provenance urn:dmp:state:&lt;sha-256(subject, property)&gt;
 *                               dmp:subject, dmp:property, dmp:writer "dmp", dmp:source, dmp:session, dmp:time
 * </pre>
 *
 * The property is the variable's meaning (a vocabulary IRI), so a query reads {@code ?unit lab:HeldBy ?holder}.
 */
public record StateUpdate(String graph, String subject, String property, ValueType valueType, Cardinality cardinality,
		String source) {
	/** The provenance terms of state the DMP writes. */
	public static final String DMP = "https://smartproductionlab.aau.dk/dmp/state#";
	private static final String XSD_DATE_TIME = "http://www.w3.org/2001/XMLSchema#dateTime";

	public StateUpdate {
		StateTerms.iri(graph);
		StateTerms.iri(subject);
		StateTerms.iri(property);
		if (valueType == null || cardinality == null) throw new IllegalArgumentException("a state variable needs a ValueType and a Cardinality");
		if (source == null || source.isBlank()) throw new IllegalArgumentException("a state write needs its source");
	}

	/** The graph the provenance records lie in. */
	public String provenanceGraph() { return graph + "/provenance"; }

	/** The provenance record of this variable: the same IRI for every write of it. */
	public String record() { return "urn:dmp:state:" + sha256(subject + "\n" + property); }

	/**
	 * The values a sink's body sets: none for {@code null} (the variable is cleared), one for a scalar of a single
	 * variable, each element of a list for a set. Anything else is rejected before anything is written.
	 */
	public List<Object> values(Object body) {
		if (body == null) return List.of();
		if (cardinality == Cardinality.SET) {
			if (!(body instanceof List<?> list)) throw new IllegalArgumentException("a set-valued state variable takes a list, not "
					+ body.getClass().getSimpleName());
			for (Object member : list) if (member == null) throw new IllegalArgumentException("a set holds no null");
			return List.copyOf(list);
		}
		if (body instanceof List<?> || body instanceof java.util.Map<?, ?>)
			throw new IllegalArgumentException("a single-valued state variable takes one value, not a " + body.getClass().getSimpleName());
		return List.of(body);
	}

	/** The update for {@code values} (see {@link #values}), written at {@code time} by DMP session {@code session}. */
	public String sparql(List<Object> values, String session, Instant time) {
		String g = StateTerms.iri(graph), p = StateTerms.iri(provenanceGraph()), s = StateTerms.iri(subject),
				prop = StateTerms.iri(property), record = StateTerms.iri(record());
		StringBuilder update = new StringBuilder()
				.append("DELETE WHERE { GRAPH ").append(g).append(" { ").append(s).append(' ').append(prop).append(" ?value } };\n")
				.append("DELETE WHERE { GRAPH ").append(p).append(" { ").append(record).append(" ?predicate ?object } };\n")
				.append("INSERT DATA {\n");
		if (!values.isEmpty()) {
			update.append("  GRAPH ").append(g).append(" { ").append(s).append(' ').append(prop).append(' ');
			for (int i = 0; i < values.size(); i++) {
				if (i > 0) update.append(" , ");
				update.append(StateTerms.term(valueType, values.get(i)));
			}
			update.append(" }\n");
		}
		update.append("  GRAPH ").append(p).append(" { ").append(record)
				.append(" <").append(DMP).append("subject> ").append(s)
				.append(" ; <").append(DMP).append("property> ").append(prop)
				.append(" ; <").append(DMP).append("writer> \"dmp\"")
				.append(" ; <").append(DMP).append("source> ").append(StateTerms.string(source))
				.append(" ; <").append(DMP).append("session> ").append(StateTerms.string(session))
				.append(" ; <").append(DMP).append("time> \"").append(time).append("\"^^<").append(XSD_DATE_TIME).append("> }\n")
				.append("}");
		return update.toString();
	}

	private static String sha256(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 unavailable", impossible);
		}
	}
}
