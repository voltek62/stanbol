package org.apache.stanbol.ontologymanager.store.rest.resources;

import java.util.List;

import javax.servlet.ServletContext;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.stanbol.commons.web.base.ContextHelper;
import org.apache.stanbol.commons.web.base.resource.BaseStanbolResource;
import org.apache.stanbol.ontologymanager.store.api.LockManager;
import org.apache.stanbol.ontologymanager.store.api.PersistenceStore;
import org.apache.stanbol.ontologymanager.store.rest.LockManagerImp;
import org.apache.stanbol.ontologymanager.store.rest.ResourceManagerImp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/ontology/{ontologyPath:.+}/classes/{classPath:.+}/superClasses/")
public class ParticularClassSuperClasses extends BaseStanbolResource{
    private static final Logger logger = LoggerFactory.getLogger(ParticularClassSuperClasses.class);

    private PersistenceStore persistenceStore;

    public ParticularClassSuperClasses(@Context ServletContext context) {
        this.persistenceStore = ContextHelper.getServiceFromContext(PersistenceStore.class, context);
    }

    @POST
    public Response addSuperClasses(@PathParam("ontologyPath") String ontologyPath,
                                    @PathParam("classPath") String classPath,
                                    @FormParam("superClassURIs") List<String> superClassURIs) {
        LockManager lockManager = LockManagerImp.getInstance();
        lockManager.obtainWriteLockFor(ontologyPath);
        try {
            ResourceManagerImp resourceManager = ResourceManagerImp.getInstance();
            String classURI = resourceManager.getResourceURIForPath(ontologyPath, classPath);
            if (classURI == null) {
                throw new WebApplicationException(Status.NOT_FOUND);
            } else {
                for (String superClassURI : superClassURIs) {
                    try {
                        persistenceStore.makeSubClassOf(classURI, superClassURI);
                    } catch (Exception e) {
                        throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
                    }
                }
            }
        } finally {
            lockManager.releaseWriteLockFor(ontologyPath);
        }
        return Response.ok().build();
    }

    @DELETE
    @Path("/ontologymanager/store{superClassPath:.+}/")
    public Response removeSuperClass(@PathParam("ontologyPath") String ontologyPath,
                                     @PathParam("classPath") String classPath,
                                     @PathParam("superClassPath") String superClassPath) {
        LockManager lockManager = LockManagerImp.getInstance();
        lockManager.obtainWriteLockFor(ontologyPath);
        try {
            ResourceManagerImp resourceManager = ResourceManagerImp.getInstance();
            String classURI = resourceManager.getResourceURIForPath(ontologyPath, classPath);
            String superClassURI = resourceManager.convertEntityRelativePathToURI(superClassPath);
            if (classURI == null) {
                throw new WebApplicationException(Status.NOT_FOUND);
            } else {
                try {
                    persistenceStore.deleteSuperClass(classURI, superClassURI);
                } catch (Exception e) {
                    throw new WebApplicationException(Status.INTERNAL_SERVER_ERROR);
                }
            }
        } finally {
            lockManager.releaseWriteLockFor(ontologyPath);
        }
        return Response.ok().build();
    }
}
