package org.openrepose.nodeservice.distributed.cluster.utils;

import org.openrepose.core.systemmodel.Node;
import org.openrepose.core.systemmodel.ReposeCluster;
import org.openrepose.core.systemmodel.SystemModel;
import org.openrepose.core.services.datastore.DatastoreAccessControl;
import org.openrepose.core.services.datastore.distributed.config.DistributedDatastoreConfiguration;
import org.openrepose.core.services.datastore.distributed.config.HostAccessControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

/**
 * A collection of convenience methods around determining the Access list for a cluster for a dist datastore.
 */
public class AccessListDeterminator {

    private static final Logger LOG = LoggerFactory.getLogger(AccessListDeterminator.class);

    public static DatastoreAccessControl getAccessList(DistributedDatastoreConfiguration config, List<InetAddress> clusterMembers) {

        List<InetAddress> hostAccessList = new LinkedList<InetAddress>();


        boolean allowAll = config.getAllowedHosts().isAllowAll();

        //Automatically Adds all cluster members to access list
        hostAccessList.addAll(clusterMembers);

        if (!allowAll) {
            hostAccessList.addAll(getConfiguredAllowedHosts(config));
        }

        if (allowAll) {
            LOG.info("The distributed datastore component is configured in allow-all mode meaning that any host can access, store and delete cached objects.");
        } else {
            LOG.info("The distributed datastore component has access controls configured meaning that only the configured hosts and cluster members "
                    + "can access, store and delete cached objects.");
        }
        LOG.debug("Allowed Hosts: " + hostAccessList.toString());


        return new DatastoreAccessControl(hostAccessList, allowAll);

    }

    private static List<InetAddress> getConfiguredAllowedHosts(DistributedDatastoreConfiguration curDistributedDatastoreConfiguration) {

        final List<InetAddress> configuredAllowedHosts = new LinkedList<InetAddress>();

        for (HostAccessControl host : curDistributedDatastoreConfiguration.getAllowedHosts().getAllow()) {
            try {
                final InetAddress hostAddress = InetAddress.getByName(host.getHost());
                configuredAllowedHosts.add(hostAddress);
            } catch (UnknownHostException e) {
                LOG.warn("Unable to resolve host: " + host.getHost(), e);
            }
        }

        return configuredAllowedHosts;
    }

    public static List<InetAddress> getClusterMembers(SystemModel config, String clusterId) {

        ReposeCluster cluster = ClusterMemberDeterminator.getCurrentCluster(config.getReposeCluster(), clusterId);
        final List<InetAddress> reposeClusterMembers = new LinkedList<InetAddress>();

        for (Node node : cluster.getNodes().getNode()) {
            try {
                final InetAddress hostAddress = InetAddress.getByName(node.getHostname());
                reposeClusterMembers.add(hostAddress);
            } catch (UnknownHostException e) {
                LOG.warn("Unable to resolve host: " + node.getHostname() + "for Node " + node.getId() + " in Repose Cluster " + clusterId, e);
            }

        }

        return reposeClusterMembers;
    }
}
