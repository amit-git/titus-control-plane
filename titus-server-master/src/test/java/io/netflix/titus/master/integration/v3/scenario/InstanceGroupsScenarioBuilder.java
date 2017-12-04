package io.netflix.titus.master.integration.v3.scenario;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.protobuf.Empty;
import com.netflix.titus.grpc.protogen.AgentChangeEvent;
import io.netflix.titus.common.aws.AwsInstanceType;
import io.netflix.titus.testkit.embedded.EmbeddedTitusOperations;
import io.netflix.titus.testkit.embedded.cloud.agent.SimulatedTitusAgentCluster;
import io.netflix.titus.testkit.embedded.master.EmbeddedTitusMaster;
import io.netflix.titus.testkit.grpc.TestStreamObserver;
import io.netflix.titus.testkit.junit.master.TitusMasterResource;
import io.netflix.titus.testkit.junit.master.TitusStackResource;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import static io.netflix.titus.master.integration.v3.scenario.InstanceGroupScenarioBuilder.TIMEOUT_MS;
import static io.netflix.titus.testkit.embedded.cloud.agent.SimulatedTitusAgentCluster.aTitusAgentCluster;

public class InstanceGroupsScenarioBuilder extends ExternalResource {

    private static final Logger logger = LoggerFactory.getLogger(InstanceGroupsScenarioBuilder.class);

    private final TitusStackResource titusStackResource;
    private final TitusMasterResource titusMasterResource;

    private EmbeddedTitusMaster titusMaster;
    private EmbeddedTitusOperations titusOperations;

    private final TestStreamObserver<AgentChangeEvent> eventStreamObserver = new TestStreamObserver<>();

    private int instanceGroupIdx;
    private final Map<String, InstanceGroupScenarioBuilder> instanceGroupScenarioBuilders = new HashMap<>();

    public InstanceGroupsScenarioBuilder(TitusMasterResource titusMasterResource) {
        this.titusMasterResource = titusMasterResource;
        this.titusStackResource = null;
    }

    public InstanceGroupsScenarioBuilder(TitusStackResource titusStackResource) {
        this.titusMasterResource = null;
        this.titusStackResource = titusStackResource;
    }

    public InstanceGroupsScenarioBuilder(EmbeddedTitusMaster titusMaster) {
        this.titusStackResource = null;
        this.titusMasterResource = null;
        this.titusMaster = titusMaster;
        this.titusOperations = new EmbeddedTitusOperations(titusMaster);
        try {
            before();
        } catch (Throwable error) {
            throw new IllegalStateException(error);
        }
    }

    @Override
    protected void before() throws Throwable {
        if (titusStackResource != null) {
            titusMaster = titusStackResource.getMaster();
            titusOperations = titusStackResource.getOperations();
        }
        if (titusMasterResource != null) {
            titusMaster = titusMasterResource.getMaster();
            titusOperations = titusMasterResource.getOperations();
        }
        titusOperations.getSimulatedCloud().getAgentInstanceGroups().forEach(g ->
                instanceGroupScenarioBuilders.put(
                        g.getName(),
                        new InstanceGroupScenarioBuilder(titusOperations, g, this, toInstanceGroupEventStream(g.getName()))
                )
        );

        titusOperations.getV3GrpcAgentClient().observeAgents(Empty.getDefaultInstance(), eventStreamObserver);
    }

    @Override
    protected void after() {
        shutdown();
    }

    public void shutdown() {
        instanceGroupScenarioBuilders.values().forEach(InstanceGroupScenarioBuilder::shutdown);
    }

    public InstanceGroupsScenarioBuilder deployInstanceGroup(String name, AwsInstanceType instanceType, int desired) {
        SimulatedTitusAgentCluster agentCluster = aTitusAgentCluster(name, instanceGroupIdx++)
                .withInstanceType(instanceType)
                .withSize(desired)
                .withComputeResources(titusOperations.getSimulatedCloud().getComputeResources())
                .build();
        titusMaster.addAgentCluster(agentCluster);

        InstanceGroupScenarioBuilder instanceGroupScenarioBuilder = new InstanceGroupScenarioBuilder(titusOperations, agentCluster, this, toInstanceGroupEventStream(name));
        instanceGroupScenarioBuilders.put(name, instanceGroupScenarioBuilder);
        return this;
    }

    public InstanceGroupsScenarioBuilder template(Consumer<InstanceGroupsScenarioBuilder> templateFun) {
        templateFun.accept(this);
        return this;
    }

    public InstanceGroupsScenarioBuilder synchronizeWithCloud() {
        logger.info("Synchronizing all agent instance groups with the cloud...");
        Stopwatch timer = Stopwatch.createStarted();
        Throwable error = Observable.just(true).concatWith(eventStreamObserver.toObservable().map(e -> true))
                .takeUntil(tick -> isSynchronizedWithCloud())
                .toCompletable()
                .timeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .get();
        logger.info("All agent instance group synchronization with the cloud completed in {}ms", timer.elapsed(TimeUnit.MILLISECONDS));

        if (error != null) {
            throw new IllegalStateException(error);
        }
        return this;
    }

    public InstanceGroupsScenarioBuilder apply(String name, Consumer<InstanceGroupScenarioBuilder> transformer) {
        InstanceGroupScenarioBuilder groupScenarioBuilder = instanceGroupScenarioBuilders.get(name);
        Preconditions.checkNotNull(groupScenarioBuilder, "Instance group %s not found", name);
        transformer.accept(groupScenarioBuilder);
        return this;
    }

    private Observable<AgentChangeEvent> toInstanceGroupEventStream(String instanceGroupId) {
        return eventStreamObserver.toObservable().filter(e -> getInstanceGroupId(e).map(id -> id.equals(instanceGroupId)).orElse(false));
    }

    public static Optional<String> getInstanceGroupId(AgentChangeEvent event) {
        switch (event.getEventCase()) {
            case INSTANCEGROUPUPDATE:
                return Optional.of(event.getInstanceGroupUpdate().getInstanceGroup().getId());
            case INSTANCEGROUPREMOVED:
                return Optional.of(event.getInstanceGroupRemoved().getInstanceGroupId());
            case AGENTINSTANCEUPDATE:
                return Optional.of(event.getAgentInstanceUpdate().getInstance().getInstanceGroupId());
            case AGENTINSTANCEREMOVED:
                // FIXME We have to iterate through all instance groups to find the destination
                break;
            case SNAPSHOTEND:
                return Optional.empty();
        }
        throw new IllegalArgumentException("Cannot handle event " + event);
    }

    private boolean isSynchronizedWithCloud() {
        List<SimulatedTitusAgentCluster> simulatedAgentClusters = titusMaster.getSimulatedCloud().getAgentInstanceGroups();
        if (instanceGroupScenarioBuilders.size() != simulatedAgentClusters.size()) {
            return false;
        }
        Set<String> clusterNames = simulatedAgentClusters.stream().map(SimulatedTitusAgentCluster::getName).collect(Collectors.toSet());
        if (!clusterNames.containsAll(instanceGroupScenarioBuilders.keySet())) {
            return false;
        }
        return instanceGroupScenarioBuilders.values().stream().allMatch(InstanceGroupScenarioBuilder::isSynchronizedWithCloud);
    }
}
