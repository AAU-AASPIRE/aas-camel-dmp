/* Copyright (C) 2026 the aas-camel-dmp Authors
 * SPDX-License-Identifier: MIT */
package dk.aau.smartproductionlab.dmp.management.model;

import java.net.URI;
import java.util.List;

import org.eclipse.digitaltwin.aas4j.v3.model.Key;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.Reference;
import org.eclipse.digitaltwin.aas4j.v3.model.ReferenceElement;
import org.eclipse.digitaltwin.aas4j.v3.model.Submodel;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;
import dk.aau.smartproductionlab.dmp.aas.model.Aas;
import dk.aau.smartproductionlab.dmp.management.repository.AasReferenceResolver;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.Cardinality;
import dk.aau.smartproductionlab.dmp.sparql.StateTerms.ValueType;

/**
 * Projects a StateVariable of a StateDeclaration (aas-model, plan D36) into what a state sink writes: the variable's
 * subject and meaning as IRIs, its value type and cardinality, and the store and graph of its declaration's
 * {@code Target}. Elements are recognised by semanticId; the meaning is the variable's one supplemental semanticId.
 * Nothing missing is filled in: a variable without a Subject, ValueType or Cardinality cannot be written.
 *
 * <p>A subject's IRI: an AAS is its id; an element is {@code <submodel id>#<idShortPath>}.
 */
public final class StateProjection {
	private static final String BASE = "https://smartproductionlab.aau.dk";
	public static final String DECLARATION = BASE + "/submodels/StateDeclaration/1/0";
	public static final String VARIABLE = BASE + "/state/StateVariable/1/0";
	public static final String SUBJECT = BASE + "/state/StateVariable/Subject/1/0";
	public static final String VALUE_TYPE = BASE + "/state/StateVariable/ValueType/1/0";
	public static final String CARDINALITY = BASE + "/state/StateVariable/Cardinality/1/0";
	public static final String TARGET = BASE + "/state/SparqlTarget/1/0";
	public static final String ENDPOINT = BASE + "/state/SparqlTarget/Endpoint/1/0";
	public static final String GRAPH = BASE + "/state/SparqlTarget/Graph/1/0";
	/** The idShort the template gives a declaration's Target. */
	public static final String TARGET_ID_SHORT = "Target";

	/**
	 * What one state variable is written as. {@code subjectReference} is the variable's Subject, an element of an AAS
	 * whose existence the compiler checks, or an AAS ({@code null} then).
	 */
	public record Variable(URI endpoint, String graph, String subject, String property, ValueType valueType,
			Cardinality cardinality, Reference subjectElement) { }

	private StateProjection() { }

	/** Whether an element is a StateVariable. */
	public static boolean isVariable(SubmodelElement element) {
		return element instanceof SubmodelElementCollection && Aas.hasSemantic(element, VARIABLE);
	}

	/**
	 * Projects {@code variable}, an element of {@code declaration} at the top level, with the declaration's
	 * {@code target}. Fails with {@link IllegalArgumentException} naming what is missing or wrong.
	 */
	public static Variable project(Submodel declaration, SubmodelElement variable, List<Key> variableKeys, SubmodelElement target) {
		if (declaration.getSemanticId() != null && !Aas.hasSemantic(declaration, DECLARATION))
			throw error("the variable's submodel is not a StateDeclaration (" + DECLARATION + ")");
		if (variableKeys.size() != 2) throw error("a StateVariable lies directly in its StateDeclaration");
		if (!isVariable(variable)) throw error("the target is not a StateVariable (" + VARIABLE + ")");
		List<Reference> meanings = Aas.nonNull(variable.getSupplementalSemanticIds());
		if (meanings.size() != 1 || Aas.keys(meanings.get(0)).size() != 1)
			throw error("a StateVariable carries exactly one meaning as its supplemental semanticId");
		String property = Aas.keys(meanings.get(0)).get(0).getValue();
		if (!StateTerms.isIri(property)) throw error("the StateVariable's meaning is not an IRI: " + property);

		List<SubmodelElement> members = Aas.children(variable);
		SubmodelElement subjectElement = only(members, SUBJECT, "Subject");
		if (!(subjectElement instanceof ReferenceElement subjectReference) || subjectReference.getValue() == null)
			throw error("the StateVariable's Subject must be a ReferenceElement with a value; a sunk variable names its subject");
		Reference subject = subjectReference.getValue();
		String subjectIri = subjectIri(subject);
		ValueType valueType = ValueType.of(value(only(members, VALUE_TYPE, "ValueType"), "ValueType"));
		Cardinality cardinality = Cardinality.of(value(only(members, CARDINALITY, "Cardinality"), "Cardinality"));

		if (!(target instanceof SubmodelElementCollection) || !Aas.hasSemantic(target, TARGET))
			throw error("the StateDeclaration's " + TARGET_ID_SHORT + " is not a SparqlTarget (" + TARGET + ")");
		List<SubmodelElement> store = Aas.children(target);
		URI endpoint;
		try { endpoint = URI.create(value(only(store, ENDPOINT, "Endpoint"), "Target Endpoint")); }
		catch (IllegalArgumentException invalid) { throw error("the Target's Endpoint is not a URI"); }
		String graph = value(only(store, GRAPH, "Graph"), "Target Graph");
		if (!StateTerms.isIri(graph)) throw error("the Target's Graph is not an IRI: " + graph);
		boolean element = "Submodel".equals(Aas.keyType(Aas.keys(subject).get(0)));
		return new Variable(endpoint, graph, subjectIri, property, valueType, cardinality, element ? subject : null);
	}

	/** The IRI of a subject reference: an AAS's id, or {@code <submodel id>#<idShortPath>} for an element. */
	public static String subjectIri(Reference reference) {
		if (!Aas.isModelReference(reference)) throw error("the Subject must be a ModelReference");
		List<Key> keys = Aas.keys(reference);
		if (keys.isEmpty()) throw error("the Subject references nothing");
		String first = Aas.keyType(keys.get(0));
		String iri;
		if ("AssetAdministrationShell".equals(first) && keys.size() == 1) iri = keys.get(0).getValue();
		else if ("Submodel".equals(first) && keys.size() >= 2) iri = keys.get(0).getValue() + "#" + AasReferenceResolver.idShortPath(keys);
		else throw error("the Subject must reference an AAS, or an element of a submodel");
		if (iri.contains("{aas_id")) throw error("the Subject contains an unresolved {aas_id} template");
		if (!StateTerms.isIri(iri)) throw error("the Subject's IRI is not an absolute IRI: " + iri);
		return iri;
	}

	private static SubmodelElement only(List<SubmodelElement> children, String semantic, String what) {
		List<SubmodelElement> matches = children.stream().filter(child -> Aas.hasSemantic(child, semantic)).toList();
		if (matches.size() != 1) throw error(matches.isEmpty() ? what + " (" + semantic + ") is missing" : what + " is declared twice");
		return matches.get(0);
	}

	private static String value(SubmodelElement element, String what) {
		if (!(element instanceof Property property) || property.getValue() == null || property.getValue().isBlank())
			throw error(what + " must be a Property with a value");
		return property.getValue().strip();
	}

	private static IllegalArgumentException error(String message) { return new IllegalArgumentException(message); }
}
