package org.openrepose.core.services.datastore.distributed.impl.distributed.cluster

import org.openrepose.commons.config.resource.ConfigurationResource
import org.openrepose.commons.config.resource.ConfigurationResourceResolver
import org.openrepose.core.domain.ReposeInstanceInfo
import org.openrepose.core.services.ServiceRegistry
import org.openrepose.core.services.config.ConfigurationService
import org.openrepose.core.services.reporting.metrics.impl.MetricsServiceImpl
import org.openrepose.services.healthcheck.HealthCheckService
import org.openrepose.services.healthcheck.HealthCheckServiceProxy
import org.openrepose.services.healthcheck.Severity
import org.junit.Before
import org.junit.Test

import javax.servlet.ServletContext
import javax.servlet.ServletContextEvent

import static org.mockito.Matchers.any
import static org.mockito.Matchers.eq
import static org.mockito.Mockito.*

class DistributedDatastoreServiceClusterContextTest {

    DistributedDatastoreServiceClusterContext distributedDatastoreServiceClusterContext;
    HealthCheckService healthCheckService;
    HealthCheckServiceProxy healthCheckServiceProxy
    ConfigurationService configurationService;
    DistributedDatastoreServiceClusterViewService datastoreServiceClusterViewService;
    ReposeInstanceInfo reposeInstanceInfo;
    ServiceRegistry serviceRegistry;
    ServletContextEvent sce;


    @Before
    void setUp() {

        healthCheckService = mock(HealthCheckService.class);
        healthCheckServiceProxy = mock(HealthCheckServiceProxy)
        when(healthCheckService.register()).thenReturn(healthCheckServiceProxy)
        configurationService = mock(ConfigurationService.class)
        datastoreServiceClusterViewService = mock(DistributedDatastoreServiceClusterViewService.class)
        reposeInstanceInfo = mock(ReposeInstanceInfo.class)
        serviceRegistry = mock(ServiceRegistry.class)
        distributedDatastoreServiceClusterContext = new DistributedDatastoreServiceClusterContext(configurationService,
        datastoreServiceClusterViewService, reposeInstanceInfo, serviceRegistry, healthCheckService);
        sce = mock(ServletContextEvent.class)
    }


    @Test
    void shouldHaveRegisteredToHealthCheckService(){

        verify(healthCheckService, times(1)).register()
    }

    @Test
    void shouldHaveRegisteredInitialErrorReports(){

        ConfigurationResourceResolver resourceResolver = mock(ConfigurationResourceResolver.class);
        ConfigurationResource configurationResource = mock(ConfigurationResource.class);
        when(configurationService.getResourceResolver()).thenReturn(resourceResolver);
        when(resourceResolver.resolve(MetricsServiceImpl.DEFAULT_CONFIG_NAME)).thenReturn(configurationResource); //TODO: WAT
        when(configurationService.getResourceResolver().resolve(DistributedDatastoreServiceClusterContext.DEFAULT_CONFIG)).thenReturn(configurationResource);
        when(configurationResource.exists()).thenReturn(false);

        ServletContext servletContext = mock(ServletContext.class)
        when(servletContext.getInitParameter(eq("datastoreServicePort"))).thenReturn("100001")
        when(sce.getServletContext()).thenReturn(servletContext)
        distributedDatastoreServiceClusterContext.contextInitialized(sce)


        verify(healthCheckServiceProxy, times(2)).reportIssue(any(String), any(String), any(Severity))
    }

}
