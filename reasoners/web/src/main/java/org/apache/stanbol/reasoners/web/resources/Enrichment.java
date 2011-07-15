/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.stanbol.reasoners.web.resources;

import static javax.ws.rs.core.MediaType.*;
import static javax.ws.rs.core.Response.Status.*;

import java.io.File;
import java.net.URL;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.stanbol.commons.web.base.ContextHelper;
import org.apache.stanbol.commons.web.base.resource.BaseStanbolResource;
import org.apache.stanbol.ontologymanager.ontonet.api.ONManager;
import org.apache.stanbol.ontologymanager.ontonet.api.ontology.OntologyScope;
import org.apache.stanbol.ontologymanager.ontonet.api.ontology.OntologySpace;
import org.apache.stanbol.ontologymanager.ontonet.api.ontology.ScopeRegistry;
import org.apache.stanbol.ontologymanager.ontonet.api.ontology.SessionOntologySpace;
import org.apache.stanbol.ontologymanager.ontonet.impl.io.ClerezzaOntologyStorage;
import org.apache.stanbol.reasoners.base.commands.CreateReasoner;
import org.apache.stanbol.reasoners.base.commands.RunReasoner;
import org.apache.stanbol.reasoners.base.commands.RunRules;
import org.apache.stanbol.rules.base.api.NoSuchRecipeException;
import org.apache.stanbol.rules.base.api.Rule;
import org.apache.stanbol.rules.base.api.RuleStore;
import org.apache.stanbol.rules.base.api.util.RuleList;
import org.apache.stanbol.rules.manager.KB;
import org.apache.stanbol.rules.manager.changes.RuleStoreImpl;
import org.apache.stanbol.rules.manager.parse.RuleParserImpl;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.sun.jersey.api.view.Viewable;
import com.sun.jersey.multipart.FormDataParam;

/**
 *
 * 
 */
@Path("/reasoners/enrichment")
public class Enrichment extends BaseStanbolResource {

    private OWLOntology inputowl;

    private RuleStore kresRuleStore;

    private Logger log = LoggerFactory.getLogger(getClass());

    protected ONManager onm;

    protected ServletContext servletContext;

    protected ClerezzaOntologyStorage storage;

    /**
     * To get the RuleStoreImpl where are stored the rules and the recipes
     * 
     * @param servletContext
     *            {To get the context where the REST service is running.}
     */
    public Enrichment(@Context ServletContext servletContext) {
        this.servletContext = servletContext;

        // Retrieve the rule store
        this.kresRuleStore = (RuleStore) ContextHelper.getServiceFromContext(RuleStore.class, servletContext);

        // Retrieve the ontology network manager
        this.onm = (ONManager) ContextHelper.getServiceFromContext(ONManager.class, servletContext);
        this.storage = (ClerezzaOntologyStorage) ContextHelper.getServiceFromContext(
            ClerezzaOntologyStorage.class, servletContext);

        if (kresRuleStore == null) {
            log.warn("No KReSRuleStore with stored rules and recipes found in servlet context. Instantiating manually with default values...");
            this.kresRuleStore = new RuleStoreImpl(onm, new Hashtable<String,Object>(), "");
            log.debug("PATH TO OWL FILE LOADED: " + kresRuleStore.getFilePath());
        }
    }

    /**
     * To trasform a sequence of rules to a Jena Model
     * 
     * @param owl
     *            {OWLOntology object contains a single recipe}
     * @return {A jena rdf model contains the SWRL rule.}
     */
    private Model fromRecipeToModel(OWLOntology owl) throws NoSuchRecipeException {

        // FIXME: why the heck is this method re-instantiating a rule store?!?
        RuleStore store = new RuleStoreImpl(onm, new Hashtable<String,Object>(), owl);
        Model jenamodel = ModelFactory.createDefaultModel();

        OWLDataFactory factory = owl.getOWLOntologyManager().getOWLDataFactory();
        OWLClass ontocls = factory.getOWLClass(IRI
                .create("http://kres.iks-project.eu/ontology/meta/rmi.owl#Recipe"));
        Set<OWLClassAssertionAxiom> cls = owl.getClassAssertionAxioms(ontocls);
        Iterator<OWLClassAssertionAxiom> iter = cls.iterator();
        IRI recipeiri = IRI.create(iter.next().getIndividual().toStringID());

        OWLIndividual recipeIndividual = factory.getOWLNamedIndividual(recipeiri);

        OWLObjectProperty objectProperty = factory.getOWLObjectProperty(IRI
                .create("http://kres.iks-project.eu/ontology/meta/rmi.owl#hasRule"));
        Set<OWLIndividual> rules = recipeIndividual.getObjectPropertyValues(objectProperty,
            store.getOntology());
        String kReSRules = "";
        for (OWLIndividual rule : rules) {
            OWLDataProperty hasBodyAndHead = factory.getOWLDataProperty(IRI
                    .create("http://kres.iks-project.eu/ontology/meta/rmi.owl#hasBodyAndHead"));
            Set<OWLLiteral> kReSRuleLiterals = rule
                    .getDataPropertyValues(hasBodyAndHead, store.getOntology());

            for (OWLLiteral kReSRuleLiteral : kReSRuleLiterals) {
                kReSRules += kReSRuleLiteral.getLiteral() + System.getProperty("line.separator");
            }
        }

        // "ProvaParent = <http://www.semanticweb.org/ontologies/2010/6/ProvaParent.owl#> . rule1[ has(ProvaParent:hasParent, ?x, ?y) . has(ProvaParent:hasBrother, ?y, ?z) -> has(ProvaParent:hasUncle, ?x, ?z) ]");
        KB kReSKB = RuleParserImpl.parse(kReSRules);
        RuleList listrules = kReSKB.getkReSRuleList();
        Iterator<Rule> iterule = listrules.iterator();
        while (iterule.hasNext()) {
            Rule singlerule = iterule.next();
            Resource resource = singlerule.toSWRL(jenamodel);
        }

        return jenamodel;

    }

    @GET
    @Produces(TEXT_HTML)
    public Response getView() {
        return Response.ok(new Viewable("index", this), TEXT_HTML).build();
    }

    /**
     * To perform a rule based reasoning with a given recipe and scope (or an ontology) to a RDF input specify
     * via its IRI.
     * 
     * @param session
     *            {A string contains the session IRI used to inference the input.}
     * @param scope
     *            {A string contains either ontology or the scope IRI used to inference the input.}
     * @param recipe
     *            {A string contains the recipe IRI from the service
     *            http://localhost:port/kres/recipe/recipeName.}
     * @Param file {A file in a RDF (eihter RDF/XML or owl) to inference.}
     * @Param input_graph {A string contains the IRI of RDF (either RDF/XML or OWL) to inference.}
     * @Param owllink_endpoint {A string contains the reasoner server end-point URL.}
     * @return Return: <br/>
     *         200 Returns a graph with the enrichments <br/>
     *         204 No enrichments have been produced from the given graph <br/>
     *         400 To run the session is needed the scope <br/>
     *         404 The recipe/ontology/scope/input doesn't exist in the network <br/>
     *         409 Too much RDF inputs <br/>
     *         500 Some error occurred
     */
    @POST
    @Consumes(MULTIPART_FORM_DATA)
    @Produces("application/rdf+xml")
    public Response ontologyEnrichment(@FormDataParam(value = "session") String session,
                                       @FormDataParam(value = "scope") String scope,
                                       @FormDataParam(value = "recipe") String recipe,
                                       @FormDataParam(value = "input-graph") String input_graph,
                                       @FormDataParam(value = "file") File file,
                                       @FormDataParam(value = "owllink-endpoint") String owllink_endpoint) {

        try {

            if ((session != null) && (scope == null)) {
                log.error("Unspecified scope parameter for session {} , cannot classify.", session);
                return Response.status(BAD_REQUEST).build();
            }

            // Check for input conflict. Only one input at once is allowed
            if ((file != null) && (input_graph != null)) {
                log.error("Parameters file and input-graph are mutually exclusive and cannot be specified together.");
                return Response.status(CONFLICT).build();
            }

            // Load input file or graph
            if (file != null) this.inputowl = OWLManager.createOWLOntologyManager()
                    .loadOntologyFromOntologyDocument(file);
            if (input_graph != null) this.inputowl = OWLManager.createOWLOntologyManager()
                    .loadOntologyFromOntologyDocument(IRI.create(input_graph));

            if (inputowl == null) return Response.status(NOT_FOUND).build();

            // Create list to add ontologies as imported
            OWLOntologyManager mgr = inputowl.getOWLOntologyManager();
            OWLDataFactory factory = inputowl.getOWLOntologyManager().getOWLDataFactory();
            List<OWLOntologyChange> additions = new LinkedList<OWLOntologyChange>();

            // Load ontologies from scope, RDF input and recipe
            // Try to resolve scope IRI
            if ((scope != null) && (session == null)) try {
                IRI iri = IRI.create(scope);
                ScopeRegistry reg = onm.getScopeRegistry();
                OntologyScope ontoscope = reg.getScope(iri);
                Iterator<OWLOntology> importscope = ontoscope.getCustomSpace().getOntologies().iterator();
                Iterator<OntologySpace> importsession = ontoscope.getSessionSpaces().iterator();

                // Add ontology as import form scope, if it is anonymus we
                // try to add single axioms.
                while (importscope.hasNext()) {
                    OWLOntology auxonto = importscope.next();
                    if (!auxonto.getOntologyID().isAnonymous()) {
                        additions.add(new AddImport(inputowl, factory.getOWLImportsDeclaration(auxonto
                                .getOWLOntologyManager().getOntologyDocumentIRI(auxonto))));
                    } else {
                        mgr.addAxioms(inputowl, auxonto.getAxioms());
                    }
                }

                // Add ontology form sessions
                while (importsession.hasNext()) {
                    Iterator<OWLOntology> sessionontos = importsession.next().getOntologies().iterator();
                    while (sessionontos.hasNext()) {
                        OWLOntology auxonto = sessionontos.next();
                        if (!auxonto.getOntologyID().isAnonymous()) {
                            additions.add(new AddImport(inputowl, factory.getOWLImportsDeclaration(auxonto
                                    .getOWLOntologyManager().getOntologyDocumentIRI(auxonto))));
                        } else {
                            mgr.addAxioms(inputowl, auxonto.getAxioms());
                        }
                    }

                }

            } catch (Exception e) {
                throw new WebApplicationException(e, INTERNAL_SERVER_ERROR);
            }

            // Get Ontologies from session
            if ((session != null) && (scope != null)) try {
                IRI iri = IRI.create(scope);
                ScopeRegistry reg = onm.getScopeRegistry();
                OntologyScope ontoscope = reg.getScope(iri);
                SessionOntologySpace sos = ontoscope.getSessionSpace(IRI.create(session));

                Set<OWLOntology> ontos = sos.getOntologyManager().getOntologies();
                Iterator<OWLOntology> iteronto = ontos.iterator();

                // Add session ontologies as import, if it is anonymous we
                // try to add single axioms.
                while (iteronto.hasNext()) {
                    OWLOntology auxonto = iteronto.next();
                    if (!auxonto.getOntologyID().isAnonymous()) {
                        additions.add(new AddImport(inputowl, factory.getOWLImportsDeclaration(auxonto
                                .getOWLOntologyManager().getOntologyDocumentIRI(auxonto))));
                    } else {
                        mgr.addAxioms(inputowl, auxonto.getAxioms());
                    }
                }

            } catch (Exception e) {
                throw new WebApplicationException(e, INTERNAL_SERVER_ERROR);
            }

            // After gathered the all ontology as imported now we apply the changes
            if (additions.size() > 0) mgr.applyChanges(additions);

            // Run HermiT if the reasonerURL is null;
            if (owllink_endpoint == null) {

                try {
                    if (recipe != null) {
                        OWLOntology recipeowl = OWLManager.createOWLOntologyManager()
                                .loadOntologyFromOntologyDocument(IRI.create(recipe));
                        // Get Jea RDF model of SWRL rule contained in the recipe
                        Model swrlmodel = fromRecipeToModel(recipeowl);

                        // Create a reasoner to run rules contained in the recipe
                        RunRules rulereasoner = new RunRules(swrlmodel, inputowl);
                        // Run the rule reasoner to the input RDF with the added top-ontology
                        inputowl = rulereasoner.runRulesReasoner();
                    }

                    // Create the reasoner for the enrichment
                    CreateReasoner newreasoner = new CreateReasoner(inputowl);
                    // Prepare and start the reasoner to enrich ontology's resources
                    RunReasoner reasoner = new RunReasoner(newreasoner.getReasoner());

                    // Create a new OWLOntology model where to put the inferred axioms
                    OWLOntology output = OWLManager.createOWLOntologyManager().createOntology(
                        inputowl.getOntologyID());
                    // Initial input axioms count
                    int startax = output.getAxiomCount();
                    // Run the classification
                    output = reasoner.runGeneralInference(output);
                    // End output axioms count
                    int endax = output.getAxiomCount();

                    if ((endax - startax) > 0) {
                        // Some inference is retrieved
                        return Response.ok(output).build();
                    } else {
                        // No data is retrieved
                        return Response.status(NOT_FOUND).build();
                    }
                } catch (InconsistentOntologyException exc) {
                    log.error("Cannot classify ionconsistent ontology " + inputowl.getOntologyID(), exc);
                    return Response.status(PRECONDITION_FAILED).build();
                }
                // If there is an owl-link server end-point specified in the form
            } else {

                try {
                    if (recipe != null) {
                        OWLOntology recipeowl = OWLManager.createOWLOntologyManager()
                                .loadOntologyFromOntologyDocument(IRI.create(recipe));
                        // Get Jea RDF model of SWRL rule contained in the recipe
                        Model swrlmodel = fromRecipeToModel(recipeowl);
                        // Create a reasoner to run rules contained in the recipe by using the server
                        // and-point
                        RunRules rulereasoner = new RunRules(swrlmodel, inputowl, new URL(owllink_endpoint));
                        // Run the rule reasoner to the input RDF with the added top-ontology
                        inputowl = rulereasoner.runRulesReasoner();
                    }

                    // Create a new OWLOntology model where to put the inferred axioms
                    OWLOntology output = OWLManager.createOWLOntologyManager().createOntology(
                        inputowl.getOntologyID());

                    // Create the reasoner for the enrichment
                    CreateReasoner newreasoner = new CreateReasoner(inputowl, new URL(owllink_endpoint));
                    // Prepare and start the reasoner to enrich ontology resources
                    RunReasoner reasoner = new RunReasoner(newreasoner.getReasoner());

                    // Initial input axioms count
                    int startax = output.getAxiomCount();
                    // Run the rule reasoner
                    output = reasoner.runGeneralInference(output);
                    // End output axioms count
                    int endax = output.getAxiomCount();

                    if ((endax - startax) > 0) {
                        // No data is retrieved, the graph IS consistent
                        return Response.ok(output).build();
                    } else {
                        // No data is retrieved, the graph IS NOT consistent
                        return Response.status(NO_CONTENT).build();
                    }
                } catch (InconsistentOntologyException exc) {
                    log.error("Cannot classify ionconsistent ontology " + inputowl.getOntologyID(), exc);
                    return Response.status(PRECONDITION_FAILED).build();
                }
            }
        } catch (Exception e) {
            throw new WebApplicationException(e, INTERNAL_SERVER_ERROR);
        }

    }

}
