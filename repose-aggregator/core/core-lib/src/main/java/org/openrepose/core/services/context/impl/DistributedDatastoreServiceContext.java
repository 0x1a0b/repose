package org.openrepose.core.services.context.impl;

import org.openrepose.commons.config.manager.UpdateListener;
import org.openrepose.core.domain.ReposeInstanceInfo;
import org.openrepose.core.domain.ServicePorts;
import org.openrepose.core.services.datastore.distributed.impl.distributed.jetty.DistributedDatastoreLauncherServiceImpl;
import org.openrepose.core.systemmodel.ReposeCluster;
import org.openrepose.core.systemmodel.Service;
import org.openrepose.core.systemmodel.SystemModel;
import org.openrepose.core.services.ServiceRegistry;
import org.openrepose.core.services.config.ConfigurationService;
import org.openrepose.core.services.context.ServiceContext;
import org.openrepose.services.datastore.DatastoreService;
import org.openrepose.core.services.datastore.DistributedDatastoreLauncherService;
import org.openrepose.core.services.routing.RoutingService;
import org.openrepose.core.servlet.InitParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.servlet.ServletContextEvent;
import java.net.URL;

/*
 * Class that will listen to system-model.cfg.xml and dist-datastore.cfg.xml file to launch the distributed-datastore servlet
 */
@Deprecated
@Component("distributedDatastoreServiceContext")
public class DistributedDatastoreServiceContext implements ServiceContext<DistributedDatastoreLauncherServiceImpl> {
    DistributedDatastoreLauncherServiceImpl distDatastoreServiceLauncher;
    private boolean initialized = false;
    private SystemModel systemModel;
    private ReposeInstanceInfo instanceInfo;
    private ConfigurationService configurationManager;
    private SystemModelConfigurationListener systemModelConfigurationListener;
    private DatastoreService datastoreService;
    private ServiceRegistry registry;
    private ServicePorts servicePorts;
    private RoutingService routingService;
    private String configDirectory;

    @Autowired
    public DistributedDatastoreServiceContext(@Qualifier("distributedDatastoreLauncher") DistributedDatastoreLauncherServiceImpl service,
                                              @Qualifier("reposeInstanceInfo") ReposeInstanceInfo reposeInstanceInfo,
                                              @Qualifier("configurationManager") ConfigurationService configurationManager,
                                              @Qualifier("datastoreService") DatastoreService datastoreService,
                                              @Qualifier("serviceRegistry") ServiceRegistry registry,
                                              @Qualifier("servicePorts") ServicePorts servicePorts,
                                              @Qualifier("routingService") RoutingService routingService) {

        this.distDatastoreServiceLauncher = service;
        this.instanceInfo = reposeInstanceInfo;
        this.configurationManager = configurationManager;
        this.systemModelConfigurationListener = new SystemModelConfigurationListener();
        this.datastoreService = datastoreService;
        this.registry = registry;
        this.servicePorts = servicePorts;
        this.routingService = routingService;
    }

    public void register() {
        if (registry != null) {
            registry.addService(this);
        }
    }

    @Override
    public String getServiceName() {
        return "DistributedDatastoreLauncherService";
    }

    @Override
    public DistributedDatastoreLauncherServiceImpl getService() {
        return distDatastoreServiceLauncher;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        final String configProp = InitParameter.POWER_API_CONFIG_DIR.getParameterName();
        configDirectory = System.getProperty(configProp, sce.getServletContext().getInitParameter(configProp));
        URL xsdURL = getClass().getResource("/META-INF/schema/system-model/system-model.xsd");
        configurationManager.subscribeTo("system-model.cfg.xml", xsdURL, systemModelConfigurationListener, SystemModel.class);
        register();
    }

    private class SystemModelConfigurationListener implements UpdateListener<SystemModel> {

        @Override
        public void configurationUpdated(SystemModel configurationObject) {
            ReposeCluster cluster = findCluster(configurationObject);

            boolean listed = serviceListed(cluster);
            if (!(listed && initialized)) {
                //launch dist-datastore servlet!!! Pass down the datastore service
                //distDatastoreServiceLauncher.initialize(configurationManager, instanceInfo, datastoreService, servicePorts, routingService, configDirectory);
                distDatastoreServiceLauncher.startDistributedDatastoreServlet();
                initialized = true;
            } else if (!listed && initialized) {
                // case when someone has turned off an existing datastore
                distDatastoreServiceLauncher.stopDistributedDatastoreServlet();
            }
            systemModel = configurationObject;
        }

        @Override
        public boolean isInitialized() {
            return initialized;
        }

        private ReposeCluster findCluster(SystemModel sysModel) {
            for (ReposeCluster cls : sysModel.getReposeCluster()) {
                if (cls.getId().equals(instanceInfo.getClusterId())) {
                    return cls;
                }

            }
            return null;
        }

        private boolean serviceListed(ReposeCluster cluster) {
            if (cluster.getServices() != null) {
                for (Service service : cluster.getServices().getService()) {
                    if (service.getName().equalsIgnoreCase("dist-datastore")) {
                        //launch dist-datastore servlet!!! Pass down the datastore service
                        return true;
                    }
                }
            }

            return false;
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        distDatastoreServiceLauncher.destroy();
        configurationManager.unsubscribeFrom("system-model.cfg.xml", systemModelConfigurationListener);
    }
}
