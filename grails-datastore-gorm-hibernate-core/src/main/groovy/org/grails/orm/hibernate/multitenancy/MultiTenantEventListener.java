package org.grails.orm.hibernate.multitenancy;

import grails.gorm.multitenancy.Tenants;
import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.engine.event.PersistenceEventListener;
import org.grails.datastore.mapping.engine.event.PreInsertEvent;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.TenantId;
import org.grails.datastore.mapping.multitenancy.exceptions.TenantException;
import org.grails.datastore.mapping.query.Query;
import org.grails.datastore.mapping.query.event.PreQueryEvent;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.grails.orm.hibernate.AbstractHibernateDatastore;
import org.springframework.context.ApplicationEvent;

import java.io.Serializable;

/**
 * An event listener that hooks into persistence events to enable discriminator based multi tenancy (ie {@link org.grails.datastore.mapping.multitenancy.MultiTenancySettings.MultiTenancyMode#DISCRIMINATOR}
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public class MultiTenantEventListener implements PersistenceEventListener {
    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return PreQueryEvent.class.isAssignableFrom(eventType) || PreInsertEvent.class.isAssignableFrom(eventType);
    }

    @Override
    public boolean supportsSourceType(Class<?> sourceType) {
        return AbstractHibernateDatastore.class.isAssignableFrom(sourceType);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if(supportsEventType(event.getClass())) {
            Datastore hibernateDatastore = (Datastore) event.getSource();
            if(event instanceof  PreQueryEvent) {
                PreQueryEvent preQueryEvent = (PreQueryEvent) event;
                Query query = preQueryEvent.getQuery();

                PersistentEntity entity = query.getEntity();
                if(entity.isMultiTenant()) {
                    if(hibernateDatastore == null) {
                        hibernateDatastore = GormEnhancer.findDatastore(entity.getJavaClass());
                    }
                    if(supportsSourceType(hibernateDatastore.getClass())) {
                        ((AbstractHibernateDatastore)hibernateDatastore).enableMultiTenancyFilter();
                    }
                }
            }
            else if(event instanceof PreInsertEvent) {
                PreInsertEvent preInsertEvent = (PreInsertEvent) event;
                PersistentEntity entity = preInsertEvent.getEntity();
                if(entity.isMultiTenant()) {
                    TenantId tenantId = entity.getTenantId();
                    EntityReflector reflector = entity.getReflector();
                    if(hibernateDatastore == null) {
                        hibernateDatastore = GormEnhancer.findDatastore(entity.getJavaClass());
                    }
                    if(supportsSourceType(hibernateDatastore.getClass())) {
                        Serializable currentId = Tenants.currentId(hibernateDatastore.getClass());
                        if(currentId != null) {
                            try {
                                reflector.setProperty(preInsertEvent.getEntityObject(), tenantId.getName(), currentId);
                            } catch (Exception e) {
                                throw new TenantException("Could not assigned tenant id ["+currentId+"] to property ["+tenantId+"], probably due to a type mismatch. You should return a type from the tenant resolver that matches the property type of the tenant id!: " + e.getMessage(), e);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }
}

