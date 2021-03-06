/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.titus.master.scheduler.constraint;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.google.common.base.Preconditions;
import com.netflix.fenzo.TaskRequest;
import com.netflix.fenzo.TaskTrackerState;
import com.netflix.fenzo.VirtualMachineCurrentState;
import com.netflix.titus.common.util.guice.annotation.Activator;
import com.netflix.titus.master.scheduler.systemselector.SystemSelectorConstraintEvaluator;

import static com.netflix.titus.master.scheduler.SchedulerModule.KUBE_CONSTRAINT;
import static java.util.Arrays.asList;

@Singleton
public class DefaultSystemHardConstraint implements SystemHardConstraint {

    public static final String NAME = "DefaultSystemHardConstraint";

    private final AgentManagementConstraint agentManagementConstraint;
    private final KubeApiNotReadySystemConstraint kubeApiNotReadySystemConstraint;
    private final AgentLaunchGuardConstraint agentLaunchGuardConstraint;
    private final AgentContainerLimitSystemConstraint agentContainerLimitSystemConstraint;
    private final SystemSelectorConstraintEvaluator systemSelectorConstraintEvaluator;
    private final IpAllocationConstraint ipAllocationConstraint;
    private final OpportunisticCpuConstraint opportunisticCpuConstraint;
    private final SystemConstraint kubeConstraint;

    private CompositeSystemConstraint delegate;

    @Inject
    public DefaultSystemHardConstraint(AgentManagementConstraint agentManagementConstraint,
                                       KubeApiNotReadySystemConstraint kubeApiNotReadySystemConstraint,
                                       AgentLaunchGuardConstraint agentLaunchGuardConstraint,
                                       AgentContainerLimitSystemConstraint agentContainerLimitSystemConstraint,
                                       SystemSelectorConstraintEvaluator systemSelectorConstraintEvaluator,
                                       IpAllocationConstraint ipAllocationConstraint,
                                       OpportunisticCpuConstraint opportunisticCpuConstraint,
                                       @Named(KUBE_CONSTRAINT) SystemConstraint kubeConstraint) {
        this.agentManagementConstraint = agentManagementConstraint;
        this.kubeApiNotReadySystemConstraint = kubeApiNotReadySystemConstraint;
        this.agentLaunchGuardConstraint = agentLaunchGuardConstraint;
        this.agentContainerLimitSystemConstraint = agentContainerLimitSystemConstraint;
        this.systemSelectorConstraintEvaluator = systemSelectorConstraintEvaluator;
        this.ipAllocationConstraint = ipAllocationConstraint;
        this.opportunisticCpuConstraint = opportunisticCpuConstraint;
        this.kubeConstraint = kubeConstraint;
    }

    @Activator
    public void enterActiveMode() {
        this.delegate = new CompositeSystemConstraint(asList(
                agentManagementConstraint,
                kubeApiNotReadySystemConstraint,
                agentLaunchGuardConstraint,
                agentContainerLimitSystemConstraint,
                systemSelectorConstraintEvaluator,
                ipAllocationConstraint,
                opportunisticCpuConstraint,
                kubeConstraint
        ));
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Result evaluate(TaskRequest taskRequest, VirtualMachineCurrentState targetVM, TaskTrackerState taskTrackerState) {
        Preconditions.checkNotNull(delegate, "System activation not finished yet");
        return delegate.evaluate(taskRequest, targetVM, taskTrackerState);
    }

    @Override
    public void prepare() {
        delegate.prepare();
    }
}