package org.openrepose.core.services.datastore.distributed.impl.distributed.cluster.utils;

import org.openrepose.commons.utils.StringUtilities;
import org.openrepose.core.systemmodel.Node;
import org.openrepose.core.systemmodel.ReposeCluster;
import org.openrepose.core.systemmodel.SystemModel;
import org.openrepose.core.services.datastore.distributed.config.DistributedDatastoreConfiguration;
import org.openrepose.core.services.datastore.distributed.config.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

/**
 * This class is like the system-model-interrogator (and will probably be renamed)
 * it finds stuff for the dist datastore based on the system model and the DistDatastore config for a clusterId
 */
public class DistDatastoreClusterInterrogator {

   private static final Logger LOG = LoggerFactory.getLogger(DistDatastoreClusterInterrogator.class);

   public static List<InetSocketAddress> getClusterMembers(SystemModel config, DistributedDatastoreConfiguration ddConfig, String clusterId) {

      final List<InetSocketAddress> cacheSiblings = new LinkedList<InetSocketAddress>();
      ReposeCluster cluster = getCurrentCluster(config.getReposeCluster(), clusterId);

      try {
         if (cluster != null) {
            for (Node node : cluster.getNodes().getNode()) {

               final InetAddress hostAddress = InetAddress.getByName(node.getHostname());
               final int port = getNodeDDPort(ddConfig, cluster.getId(), node.getId());
               final InetSocketAddress hostSocketAddress = new InetSocketAddress(hostAddress, port);
               cacheSiblings.add(hostSocketAddress);
            }
         }
      } catch (UnknownHostException ex) {
         LOG.error(ex.getMessage(), ex);
      }



      return cacheSiblings;

   }

   public static int getNodeDDPort(DistributedDatastoreConfiguration config, String clusterId, String nodeId) {

      int port = getDefaultDDPort(config, clusterId);
      for (Port curPort : config.getPortConfig().getPort()) {
         if (curPort.getCluster().equalsIgnoreCase(clusterId) && curPort.getNode().equalsIgnoreCase(nodeId)) {
            port = curPort.getPort();
            break;

         }
      }
      return port;
   }

   public static int getDefaultDDPort(DistributedDatastoreConfiguration config, String clusterId) {

      int port = -1;
      for (Port curPort : config.getPortConfig().getPort()) {
         if (curPort.getCluster().equalsIgnoreCase(clusterId) && "-1".equals(curPort.getNode())) {
            port = curPort.getPort();
         }
      }
      return port;
   }

   public static ReposeCluster getCurrentCluster(List<ReposeCluster> clusters, String clusterId) {

      for (ReposeCluster cluster : clusters) {

         if (StringUtilities.nullSafeEquals(clusterId, cluster.getId())) {
            return cluster;
         }
      }

      return null;

   }
}
