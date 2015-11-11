package org.radargun.service;

import org.infinispan.client.hotrod.configuration.ClusterConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.radargun.Service;
import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.utils.ReflexiveConverters;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Matej Cimbora
 */
@Service(doc = Infinispan60HotrodService.SERVICE_DESCRIPTION)
public class Infinispan81HotrodService extends Infinispan80HotrodService {

    @Property(doc = "Configuration of backup clusters used for XSite failover of HotRod client.", complexConverter = BackupClusterConfigurationConverter.class)
    protected List<BackupClusterConfiguration> backupClusters = new ArrayList<>(0);

    @Override
    protected ConfigurationBuilder getDefaultHotRodConfig() {
        ConfigurationBuilder hotRodConfigBuilder = super.getDefaultHotRodConfig();
        for (BackupClusterConfiguration clusterConfig : backupClusters) {
            ClusterConfigurationBuilder clusterConfigBuilder = hotRodConfigBuilder.addCluster(clusterConfig.name);
            for (ClusterNode clusterNode : clusterConfig.nodes) {
                clusterConfigBuilder.addClusterNode(clusterNode.host, clusterNode.port);
            }
        }
        return hotRodConfigBuilder;
    }

    @DefinitionElement(name = "cluster", doc = "Holder for cluster configuration of HotRod client.")
    private static class BackupClusterConfiguration {
        @Property(doc = "Cluster name.", optional = false)
        private String name;
        @Property(doc = "List of nodes in the cluster.", optional = false, complexConverter = ClusterNodeConverter.class)
        private List<ClusterNode> nodes = new ArrayList<>();
    }

    @DefinitionElement(name = "node", doc = "Representation of a single cluster node.")
    private static class ClusterNode {
        @Property(doc = "Hostname. Default is 127.0.0.1")
        private String host = "127.0.0.1";
        @Property(doc = "Port. Default is 11222.")
        private int port = 11222;
    }

    private static class BackupClusterConfigurationConverter extends ReflexiveConverters.ListConverter {
        public BackupClusterConfigurationConverter() {
            super(new Class[] { BackupClusterConfiguration.class });
        }
    }

    private static class ClusterNodeConverter extends ReflexiveConverters.ListConverter {
        public ClusterNodeConverter() {
            super(new Class[] { ClusterNode.class });
        }
    }

}
