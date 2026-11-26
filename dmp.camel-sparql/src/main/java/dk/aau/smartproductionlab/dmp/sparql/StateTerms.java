/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.sparql;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;

/**
 * The value types of state variables (the vocabulary's {@code lab:valueType}, written on each StateVariable) and how a
 * value of one is written in SPARQL: a number as {@code xsd:double}, a boolean as {@code xsd:boolean}, a string as
 * {@code xsd:string}, a reference as an IRI. A value of another kind is rejected, never converted.
 */
public final class StateTerms {
	public enum ValueType {
		NUMBER, BOOLEAN, STRING, REFERENCE;

		/** The type named as in the vocabulary ({@code number}, {@code boolean}, {@code string}, {@code reference}). */
		public static ValueType of(String name) {
			if (name == null) throw new IllegalArgumentException("a state variable needs a ValueType");
			return switch (name.strip()) {
				case "number" -> NUMBER;
				case "boolean" -> BOOLEAN;
				case "string" -> STRING;
				case "reference" -> REFERENCE;
				default -> throw new IllegalArgumentException("ValueType must be number, boolean, string or reference, not " + name);
			};
		}

		public String vocabularyName() { return name().toLowerCase(Locale.ROOT); }
	}

	/** Cardinality: one value at a time, or a set of values (the vocabulary's {@code lab:cardinality}). */
	public enum Cardinality {
		SINGLE, SET;

		public static Cardinality of(String name) {
			if (name == null) throw new IllegalArgumentException("a state variable needs a Cardinality");
			return switch (name.strip()) {
				case "single" -> SINGLE;
				case "set" -> SET;
				default -> throw new IllegalArgumentException("Cardinality must be single or set, not " + name);
			};
		}

		public String vocabularyName() { return name().toLowerCase(Locale.ROOT); }
	}

	private static final String XSD = "http://www.w3.org/2001/XMLSchema#";

	private StateTerms() { }

	/** An IRI as a SPARQL term; rejects what is not an absolute IRI or holds a character IRIs exclude. */
	public static String iri(String iri) {
		if (!isIri(iri)) throw new IllegalArgumentException("not an absolute IRI: " + iri);
		return "<" + iri + ">";
	}

	/** Whether {@code value} is an absolute IRI SPARQL accepts between angle brackets. */
	public static boolean isIri(String value) {
		if (value == null || value.isEmpty()) return false;
		int colon = value.indexOf(':');
		if (colon < 1 || !Character.isLetter(value.charAt(0))) return false;
		for (int i = 0; i < colon; i++) {
			char c = value.charAt(i);
			if (!(Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.')) return false;
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c <= 0x20 || "<>\"{}|^`\\".indexOf(c) >= 0) return false;
		}
		return true;
	}

	/** A value of the given type as a SPARQL term. */
	public static String term(ValueType type, Object value) {
		if (value == null) throw new IllegalArgumentException("no value");
		return switch (type) {
			case NUMBER -> {
				if (!(value instanceof Number number)) throw mismatch(type, value);
				yield '"' + number(number) + "\"^^<" + XSD + "double>";
			}
			case BOOLEAN -> {
				if (!(value instanceof Boolean flag)) throw mismatch(type, value);
				yield '"' + flag.toString() + "\"^^<" + XSD + "boolean>";
			}
			case STRING -> {
				if (!(value instanceof String text)) throw mismatch(type, value);
				yield string(text);
			}
			case REFERENCE -> {
				if (!(value instanceof String text)) throw mismatch(type, value);
				if (!isIri(text)) throw new IllegalArgumentException("a reference value must be an absolute IRI, not " + text);
				yield iri(text);
			}
		};
	}

	/** A string literal, escaped for SPARQL. */
	public static String string(String text) {
		StringBuilder quoted = new StringBuilder("\"");
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '"' -> quoted.append("\\\"");
				case '\\' -> quoted.append("\\\\");
				case '\n' -> quoted.append("\\n");
				case '\r' -> quoted.append("\\r");
				case '\t' -> quoted.append("\\t");
				default -> quoted.append(c);
			}
		}
		return quoted.append('"').toString();
	}

	private static String number(Number number) {
		if (number instanceof Double || number instanceof Float) {
			double value = number.doubleValue();
			if (!Double.isFinite(value)) throw new IllegalArgumentException("a number value must be finite, not " + value);
			return Double.toString(value);
		}
		if (number instanceof BigDecimal decimal) return decimal.toPlainString();
		if (number instanceof BigInteger || number instanceof Long || number instanceof Integer || number instanceof Short
				|| number instanceof Byte) return number.toString();
		throw new IllegalArgumentException("unsupported number " + number.getClass().getSimpleName());
	}

	private static IllegalArgumentException mismatch(ValueType type, Object value) {
		return new IllegalArgumentException("a " + type.vocabularyName() + " state variable cannot hold a "
				+ value.getClass().getSimpleName() + " (" + value + ")");
	}
}
