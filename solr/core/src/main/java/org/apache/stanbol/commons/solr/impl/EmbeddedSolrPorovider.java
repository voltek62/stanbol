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
package org.apache.stanbol.commons.solr.impl;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.collections.map.ReferenceMap;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.ReferenceStrategy;
import org.apache.felix.scr.annotations.Service;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.SolrCore;
import org.apache.stanbol.commons.solr.SolrServerProvider;
import org.apache.stanbol.commons.solr.SolrServerTypeEnum;
import org.apache.stanbol.commons.solr.SolrConstants;
import org.apache.stanbol.commons.solr.utils.ConfigUtils;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * Support for the use of {@link EmbeddedSolrPorovider} in combination with the SolrYard implementation. This
 * implements the {@link SolrServerProvider} interface for the {@link SolrServerTypeEnum#EMBEDDED}.
 * <p>
 * 
 * TODO: add functionality to lookup the internally managed {@link CoreContainer}. Maybe this requires to add
 * a second service
 * 
 * @author Rupert Westenthaler
 * 
 */
@Component(immediate = true, metatype = true)
@Service
public class EmbeddedSolrPorovider implements SolrServerProvider {
    private final Logger log = LoggerFactory.getLogger(EmbeddedSolrPorovider.class);
    // define the default values here because they are not accessible via the Solr API
    public static final String SOLR_XML_NAME = "solr.xml";
    public static final String SOLR_CONFIG_NAME = "solrconfig.xml";
    public static final String SOLR_SCHEMA_NAME = "schema.xml";

    /**
     * internally used to keep track of active {@link CoreContainer}s for requested paths.
     */
    @SuppressWarnings("unchecked")
    private Map<String,CoreContainer> coreContainers = new ReferenceMap();
    /**
     * The {@link ComponentContext} as set in the {@link #activate(ComponentContext)}
     * method
     */
    private ComponentContext context;
    private ServiceTracker defaultSolrServerTracker;

    private static String filter = "("+SolrConstants.PROPERTY_SERVER_NAME+"=embedded)";

    public EmbeddedSolrPorovider() {}

//    protected void bindSolrDirectoryManager(SolrDirectoryManager solrDirectoryManager){
//        this.solrDirectoryManager = solrDirectoryManager;
//    }
//    protected void unbindSolrDirectoryManager(SolrDirectoryManager solrDirectoryManager) {
//        this.solrDirectoryManager = null;
//    }
//    protected SolrDirectoryManager getSolrDirectoryManager(){
//        return this.solrDirectoryManager;
//    }

    @Override
    public SolrServer getSolrServer(SolrServerTypeEnum type, String uriOrPath, String... additional) throws NullPointerException,
                                                                                      IllegalArgumentException {
        log.debug(String.format("getSolrServer Request for %s and path %s", type, uriOrPath));
        if (uriOrPath == null) {
            throw new IllegalArgumentException("The Path to the Index MUST NOT be NULL!");
        }
        log.info("parsed solr server location " + uriOrPath);
        // first try as file (but keep in mind it could also be an URI)
        File index = ConfigUtils.toFile(uriOrPath);
        if(!index.isAbsolute()){
            CoreContainer defaultServer = defaultSolrServerTracker == null ? null : (CoreContainer)defaultSolrServerTracker.getService();
            if(defaultServer != null){
                if(defaultServer.getCoreNames().contains(index.getName())){
                    //create an EmbeddedSolrServer
                    return new EmbeddedSolrServer(defaultServer, index.getName());
                } else {
                    log.info("Internally Managed SolrServer does not know a SolrCore with the name '{}'",uriOrPath);
                }
            } else if(context != null){
                log.warn("Internally Managed SolrServer not available: Unable to lookup SolrCore '{}'!",uriOrPath);
            }
        } 
        if (!index.exists()) {
            throw new IllegalArgumentException(String.format("The parsed Index Path '%s' does not " +
            		"refer to an internally managed SolrServer nor to a Directory on the FileSystem",
                uriOrPath));
        }
        //TODO: refactor that so that also external SolrServer are initialised
        //      using ManagedSsolrServerImpl
        log.info("get solr server for location " + index);
        File coreDir = null;
        if (index.isDirectory()) {
            File solr = getFile(index, SOLR_XML_NAME);
            String coreName;
            if (solr != null) {
                // in that case we assume that this is a single core installation
                coreName = "";
            } else {
                solr = getFile(index.getParentFile(), SOLR_XML_NAME);
                if (solr != null) {
                    // assume this is a multi core
                    coreName = index.getName();
                    coreDir = index;
                    index = index.getParentFile(); // set the index dir to the parent
                } else {
                    throw new IllegalArgumentException(String.format(
                        "The parsed Index Path %s is not an Solr "
                                + "Index nor a Core of an Multi Core Configuration " + "(no \""
                                + SOLR_XML_NAME + "\" was found in this nor the parent directory!)",
                        uriOrPath));
                }
            }
            // now init the EmbeddedSolrServer
            log.info("Create EmbeddedSolrServer for index {} and core {}",
                index.getAbsolutePath(), coreName);
            CoreContainer coreContainer = getCoreContainer(index.getAbsolutePath(), solr);
            // if we have a multi core environment and the core is not yet registered
            if (!coreName.isEmpty() && !coreContainer.getCoreNames().contains(coreName)) {
                // register this core first
                /*
                 * NOTE: We need to reset the ContextClassLoader to the one used for this Bundle, because Solr
                 * uses this ClassLoader to load all the plugins configured in the SOLR_XML_NAME and
                 * schema.xml. The finally block resets the context class loader to the previous value.
                 * (Rupert Westenthaler 20010209)
                 */
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(EmbeddedSolrPorovider.class.getClassLoader());
                try {
                    // SolrResourceLoader solrLoader = new SolrResourceLoader(coreDir.getAbsolutePath());
                    CoreDescriptor coreDescriptor = new CoreDescriptor(coreContainer, coreName,
                            coreDir.getAbsolutePath());
                    SolrCore core;
                    try {
                        core = coreContainer.create(coreDescriptor);
                    } catch (Exception e) {
                        throw new IllegalStateException(String.format("Unable to load/register Solr Core %s "
                                                                      + "to SolrServer %s!", coreName,
                            index.getAbsoluteFile()), e);
                    }
                    coreContainer.register(coreName, core, false);
                    // persist the new core to have it available on the next start
                    coreContainer.persist();
                } finally {
                    Thread.currentThread().setContextClassLoader(classLoader);
                }
            }
            return (SolrServer)new EmbeddedSolrServer(coreContainer, coreName);
        } else {
            throw new IllegalArgumentException(String.format("The parsed Index Path %s is no Directory",
                uriOrPath));
        }
    }

    protected final CoreContainer getCoreContainer(File solrDir) throws IllegalArgumentException,
                                                                IllegalStateException {
        return getCoreContainer(solrDir.getAbsolutePath(), new File(solrDir, SOLR_XML_NAME));
    }

    protected final CoreContainer getCoreContainer(String solrDir, File solrConf) throws IllegalArgumentException,
                                                                                         IllegalStateException {
        CoreContainer container = coreContainers.get(solrDir);
        if (container == null) {
            container = new CoreContainer(solrDir);
            /*
             * NOTE: We need to reset the ContextClassLoader to the one used for this Bundle, because Solr
             * uses this ClassLoader to load all the plugins configured in the SOLR_XML_NAME and schema.xml.
             * The finally block resets the context class loader to the previous value. (Rupert Westenthaler
             * 20010209)
             */
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(EmbeddedSolrPorovider.class.getClassLoader());
            try {
                container.load(solrDir, solrConf);
            } catch (ParserConfigurationException e) {
                throw new IllegalStateException("Unable to parse Solr Configuration", e);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to access Solr Configuration", e);
            } catch (SAXException e) {
                throw new IllegalStateException("Unable to parse Solr Configuration", e);
            } finally {
                Thread.currentThread().setContextClassLoader(loader);
            }
            coreContainers.put(solrDir, container);
        }
        return container;
    }

    @Override
    public Set<SolrServerTypeEnum> supportedTypes() {
        return Collections.singleton(SolrServerTypeEnum.EMBEDDED);
    }

    @Activate
    protected void activate(ComponentContext context) throws InvalidSyntaxException {
        log.debug("activating" + EmbeddedSolrPorovider.class.getSimpleName());
        this.context = context;
        String filterString = String.format("(&(%s=%s)(%s=%s))",
            Constants.OBJECTCLASS,CoreContainer.class.getName(),
            SolrConstants.PROPERTY_SERVER_NAME,"default");

        Filter filter = context.getBundleContext().createFilter(filterString);
        defaultSolrServerTracker = new ServiceTracker(
            context.getBundleContext(), filter, null);
        defaultSolrServerTracker.open();

    }

    @Deactivate
    protected void deactivate(ComponentContext context) {
        log.debug("deactivating" + EmbeddedSolrPorovider.class.getSimpleName());
        defaultSolrServerTracker.close();
        defaultSolrServerTracker = null;
        //shutdown externally managed CoreContainers
        for(CoreContainer coreContainer : coreContainers.values()){
            coreContainer.shutdown();
        }
        this.context = null;
    }

    // Keeping for now because this might be useful when checking for required files
    // /**
    // * Checks if the parsed directory contains a file that starts with the parsed
    // * name. Parsing "hallo" will find "hallo.all", "hallo.ween" as well as "hallo".
    // * @param dir the Directory. This assumes that the parsed File is not
    // * <code>null</code>, exists and is an directory
    // * @param name the name. If <code>null</code> any file is accepted, meaning
    // * that this will return true if the directory contains any file
    // * @return the state
    // */
    // private boolean hasFile(File dir, String name){
    // return dir.list(new NameFileFilter(name)).length>0;
    // }
    // /**
    // * Returns the first file that matches the parsed name.
    // * Parsing "hallo" will find "hallo.all", "hallo.ween" as well as "hallo".
    // * @param dir the Directory. This assumes that the parsed File is not
    // * <code>null</code>, exists and is an directory.
    // * @param name the name. If <code>null</code> any file is accepted, meaning
    // * that this will return true if the directory contains any file
    // * @return the first file matching the parsed prefix.
    // */
    // private File getFileByPrefix(File dir, String prefix){
    // String[] files = dir.list(new PrefixFileFilter(prefix));
    // return files.length>0?new File(dir,files[0]):null;
    // }
    /**
     * Returns the first file that matches the parsed name (case sensitive)
     * 
     * @param dir
     *            the Directory. This assumes that the parsed File is not <code>null</code>, exists and is an
     *            directory.
     * @param name
     *            the name. If <code>null</code> any file is accepted, meaning that this will return true if
     *            the directory contains any file
     * @return the first file matching the parsed name.
     */
    private File getFile(File dir, String name) {
        String[] files = dir.list(new NameFileFilter(name));
        return files.length > 0 ? new File(dir, files[0]) : null;
    }
}
