package org.openrepose.filters.clientauth;

import org.openrepose.filters.clientauth.config.ClientAuthConfig;
import org.openrepose.core.filter.FilterConfigHelper;
import org.openrepose.core.filter.logic.impl.FilterLogicHandlerDelegate;
import org.openrepose.nodeservice.jmx.ConfigurationInformation;
import org.openrepose.core.services.config.ConfigurationService;
import org.openrepose.core.services.context.ContextAdapter;
import org.openrepose.core.services.context.ServletContextHelper;
import org.openrepose.services.datastore.Datastore;
import org.openrepose.services.datastore.DatastoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import java.io.IOException;
import java.net.URL;

/**
 *
 * @author jhopper
 */
public class ClientAuthenticationFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(ClientAuthenticationFilter.class);
    private static final String DEFAULT_CONFIG = "client-auth-n.cfg.xml";
    private String config;
    private ClientAuthenticationHandlerFactory handlerFactory;
    private ConfigurationService configurationManager;
    private ConfigurationInformation configurationInformation;

    @Override
    public void destroy() {
        handlerFactory.stopFeeds();
        configurationManager.unsubscribeFrom(config, handlerFactory);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        new FilterLogicHandlerDelegate(request, response, chain).doFilter(handlerFactory.newHandler());
    }

    private Datastore getDatastore(DatastoreService datastoreService) {
        return datastoreService.getDefaultDatastore();
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        final ContextAdapter ctx = ServletContextHelper.getInstance(filterConfig.getServletContext()).getPowerApiContext();

        config = new FilterConfigHelper(filterConfig).getFilterConfig(DEFAULT_CONFIG);
        LOG.info("Initializing filter using config " + config);
        handlerFactory = new ClientAuthenticationHandlerFactory(getDatastore(ctx.datastoreService()),ctx.httpConnectionPoolService(),ctx.akkaServiceClientService());
        configurationManager = ctx.configurationService();
        URL xsdURL = getClass().getResource("/META-INF/schema/config/client-auth-n-configuration.xsd");
        configurationManager.subscribeTo(filterConfig.getFilterName(),config,xsdURL , handlerFactory, ClientAuthConfig.class);
        
       if(handlerFactory.isInitialized()){
        configurationInformation=ServletContextHelper.getInstance(filterConfig.getServletContext()).getPowerApiContext().reposeConfigurationInformation();

       }
    }
}
