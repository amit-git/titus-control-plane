/*
 * Copyright 2018 Netflix, Inc.
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

import java.util.Optional;

import com.netflix.fenzo.ConstraintEvaluator;
import com.netflix.fenzo.TaskRequest;
import com.netflix.fenzo.TaskTrackerState;
import com.netflix.fenzo.VirtualMachineCurrentState;
import com.netflix.titus.api.agent.model.AgentInstance;
import com.netflix.titus.api.agent.service.AgentManagementService;
import com.netflix.titus.master.scheduler.SchedulerConfiguration;
import com.netflix.titus.master.scheduler.SchedulerUtils;

/**
 * Constraint such that workloads can prefer a machine in a specific availability zone.
 */
public class AvailabilityZoneConstraint implements ConstraintEvaluator {

    public static final String NAME = "AvailabilityZoneConstraint";

    private static final Result VALID = new Result(true, null);
    private static final Result MACHINE_DOES_NOT_EXIST = new Result(false, "The machine does not exist");
    private static final Result AVAILABILITY_ZONE_DOES_NOT_MATCH = new Result(false, "The availability zone does not match the specified availability zone");
    private final SchedulerConfiguration configuration;
    private final AgentManagementService agentManagementService;
    private final String availabilityZone;

    public AvailabilityZoneConstraint(SchedulerConfiguration configuration, AgentManagementService agentManagementService, String availabilityZone) {
        this.configuration = configuration;
        this.agentManagementService = agentManagementService;
        this.availabilityZone = availabilityZone;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Result evaluate(TaskRequest taskRequest, VirtualMachineCurrentState targetVM, TaskTrackerState taskTrackerState) {
        Optional<AgentInstance> instanceOpt = SchedulerUtils.findInstance(agentManagementService, configuration.getInstanceAttributeName(), targetVM);
        if (!instanceOpt.isPresent()) {
            return MACHINE_DOES_NOT_EXIST;
        }

        AgentInstance agentInstance = instanceOpt.get();
        String instanceAvailabilityZone = agentInstance.getAttributes().getOrDefault(configuration.getAvailabilityZoneAttributeName(), "");
        return instanceAvailabilityZone.equalsIgnoreCase(availabilityZone) ? VALID : AVAILABILITY_ZONE_DOES_NOT_MATCH;
    }
}
