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

package org.apache.stanbol.reasoners.base;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.apache.stanbol.reasoners.base.commands.CreateReasoner;
import org.apache.stanbol.reasoners.base.commands.RunReasoner;
import org.apache.stanbol.reasoners.base.commands.RunRules;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.rdf.model.ModelFactory;

/**
 * 
 * @author elvio
 */
public class RunRulesTest {

    private Logger log = LoggerFactory.getLogger(getClass());

    public OWLOntologyManager owlmanagertarget;

    public OWLOntologyManager owlnamagerswrlt;

    public OWLOntology owltarget;

    public OWLOntology owlswrl;

    public OntModel jenaswrl;

    public RunRulesTest() throws OWLOntologyCreationException, IOException {
        this.owltarget = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(
            new File("./src/main/resources/TestFile/ProvaParent.owl"));
        this.owlswrl = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(
            new File("./src/main/resources/TestFile/OnlyRuledProvaParent.owl"));
        this.owlmanagertarget = owltarget.getOWLOntologyManager();
        this.owlnamagerswrlt = owlswrl.getOWLOntologyManager();
        this.jenaswrl = ModelFactory.createOntologyModel();
        this.jenaswrl.read("file:./src/main/resources/TestFile/OnlyRuledProvaParentRDFXML.owl", "RDF/XML");
    }

    @BeforeClass
    public static void setUpClass() throws Exception {}

    @AfterClass
    public static void tearDownClass() throws Exception {}

    @Before
    public void setUp() {}

    @After
    public void tearDown() {}

    /**
     * Test of runRulesReasoner method, of class RunRules.
     */
    @Test
    public void testRunRulesReasoner_OWLOntology_1() throws OWLOntologyCreationException {

        OWLOntology newmodel = OWLManager.createOWLOntologyManager()
                .createOntology(owltarget.getOntologyID());
        RunRules instance = new RunRules(owlswrl, owltarget);

        newmodel = instance.runRulesReasoner(newmodel);

        CreateReasoner reasonerforcheck = new CreateReasoner(newmodel);
        RunReasoner run = new RunReasoner(reasonerforcheck.getReasoner());
        log.debug("Ontology {} is " + (run.isConsistent() ? "consistent" : "NOT consistent") + ".",
            newmodel.getOntologyID());

        Iterator<OWLAxiom> axiom = newmodel.getAxioms().iterator();
        Iterator<OWLAxiom> axt = owltarget.getAxioms().iterator();

        String inferedaxiom = "ObjectPropertyAssertion(<http://www.semanticweb.org/ontologies/2010/6/ProvaParent.owl#hasUncle> <http://www.semanticweb.org/ontologies/2010/6/ProvaParent.owl#Tom> <http://www.semanticweb.org/ontologies/2010/6/ProvaParent.owl#John>)";
        String ax;

        assertNotNull(newmodel);

        while (axt.hasNext()) {
            ax = axt.next().toString();
            if (ax.equals(inferedaxiom)) fail("Some errors occur with runRulesReasoner with new ontology in KReSRunRules.");
        }

        while (axiom.hasNext()) {
            ax = axiom.next().toString();
            if (ax.equals(inferedaxiom)) assertEquals(inferedaxiom, ax.toString());
        }

    }

    /**
     * Test of runRulesReasoner method, of class RunRules.
     */
    @Test
    public void testRunRulesReasoner_0args_1() {

        RunRules instance = new RunRules(owlswrl, owltarget);
        OWLOntology newmodel = instance.runRulesReasoner();

        CreateReasoner reasonerforcheck = new CreateReasoner(newmodel);
        RunReasoner run = new RunReasoner(reasonerforcheck.getReasoner());
        log.debug("Ontology {} is " + (run.isConsistent() ? "consistent" : "NOT consistent") + ".",
            newmodel.getOntologyID());

        Iterator<OWLAxiom> axiom = newmodel.getAxioms().iterator();

        String inferedaxiom = "ObjectPropertyAssertion(<http://www.semanticweb.org/ontologies/2010/6/ProvaParent.owl#hasUncle> <http://www.semanticweb.org/ontologies/2010/6/ProvaParent.owl#Tom> <http://www.semanticweb.org/ontologies/2010/6/ProvaParent.owl#John>)";
        String ax;

        assertNotNull(newmodel);

        while (axiom.hasNext()) {
            ax = axiom.next().toString();
            if (ax.equals(inferedaxiom)) assertEquals(inferedaxiom, ax.toString());
        }

    }

    /**
     * Test of runRulesReasoner method, of class RunRules.
     */
    @Test
    public void testRunRulesReasoner_OWLOntology_2() throws OWLOntologyCreationException {

        OWLOntology newmodel = OWLManager.createOWLOntologyManager()
                .createOntology(owltarget.getOntologyID());
        RunRules instance = new RunRules(jenaswrl.getBaseModel(), owltarget);

        newmodel = instance.runRulesReasoner(newmodel);

        CreateReasoner reasonerforcheck = new CreateReasoner(newmodel);
        RunReasoner run = new RunReasoner(reasonerforcheck.getReasoner());
        log.debug("Ontology {} is " + (run.isConsistent() ? "consistent" : "NOT consistent") + ".",
            newmodel.getOntologyID());

        Iterator<OWLAxiom> axiom = newmodel.getAxioms().iterator();
        Iterator<OWLAxiom> axt = owltarget.getAxioms().iterator();

        String inferedaxiom = "ObjectPropertyAssertion(<http://www.semanticweb.org/ontologies/2010/6/ProvaParent.owl#hasUncle> <http://www.semanticweb.org/ontologies/2010/6/ProvaParent.owl#Tom> <http://www.semanticweb.org/ontologies/2010/6/ProvaParent.owl#John>)";
        String ax;

        assertNotNull(newmodel);

        while (axt.hasNext()) {
            ax = axt.next().toString();
            if (ax.equals(inferedaxiom)) fail("Some errors occur with runRulesReasoner with new ontology in KReSRunRules.");
        }

        while (axiom.hasNext()) {
            ax = axiom.next().toString();
            if (ax.equals(inferedaxiom)) assertEquals(inferedaxiom, ax.toString());
        }

    }

    /**
     * Test of runRulesReasoner method, of class RunRules.
     */
    @Test
    public void testRunRulesReasoner_0args_2() {

        RunRules instance = new RunRules(jenaswrl, owltarget);
        OWLOntology newmodel = instance.runRulesReasoner();

        CreateReasoner reasonerforcheck = new CreateReasoner(newmodel);
        RunReasoner run = new RunReasoner(reasonerforcheck.getReasoner());
        log.debug("Ontology {} is " + (run.isConsistent() ? "consistent" : "NOT consistent") + ".",
            newmodel.getOntologyID());

        Iterator<OWLAxiom> axiom = newmodel.getAxioms().iterator();

        String inferedaxiom = "ObjectPropertyAssertion(<http://www.semanticweb.org/ontologies/2010/6/ProvaParent.owl#hasUncle> <http://www.semanticweb.org/ontologies/2010/6/ProvaParent.owl#Tom> <http://www.semanticweb.org/ontologies/2010/6/ProvaParent.owl#John>)";
        String ax;

        assertNotNull(newmodel);

        while (axiom.hasNext()) {
            ax = axiom.next().toString();
            if (ax.equals(inferedaxiom)) assertEquals(inferedaxiom, ax.toString());
        }

    }

}