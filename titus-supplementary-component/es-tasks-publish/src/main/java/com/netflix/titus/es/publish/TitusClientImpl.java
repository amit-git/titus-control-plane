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
package com.netflix.titus.es.publish;

import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.titus.grpc.protogen.Job;
import com.netflix.titus.grpc.protogen.JobChangeNotification;
import com.netflix.titus.grpc.protogen.JobId;
import com.netflix.titus.grpc.protogen.JobManagementServiceGrpc;
import com.netflix.titus.grpc.protogen.ObserveJobsQuery;
import com.netflix.titus.grpc.protogen.Task;
import com.netflix.titus.grpc.protogen.TaskId;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TitusClientImpl implements TitusClient {
    private static final Logger logger = LoggerFactory.getLogger(TitusClientImpl.class);
    private final JobManagementServiceGrpc.JobManagementServiceStub jobManagementService;
    private AtomicInteger numJobUpdates = new AtomicInteger(0);
    private AtomicInteger numTaskUpdates = new AtomicInteger(0);
    private AtomicInteger numSnapshotUpdates = new AtomicInteger(0);
    private AtomicInteger apiErrors = new AtomicInteger(0);


    @Inject
    public TitusClientImpl(JobManagementServiceGrpc.JobManagementServiceStub jobManagementService,
                           Registry registry) {
        this.jobManagementService = jobManagementService;
        configureMetrics(registry);
    }

    @Override
    public Mono<Task> getTask(String taskId) {
        logger.debug("Getting Task information about taskId {}", taskId);
        return Mono.create(sink ->
                jobManagementService.findTask(TaskId.newBuilder().setId(taskId).build(), new StreamObserver<Task>() {
                    @Override
                    public void onNext(Task task) {
                        sink.success(task);
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.error("Error fetching task information for task ID = {}", taskId);
                        apiErrors.incrementAndGet();
                        sink.error(t);
                    }

                    @Override
                    public void onCompleted() {

                    }
                }));
    }

    @Override
    public Flux<Task> getTaskUpdates() {
        return Flux.create(sink -> jobManagementService.observeJobs(ObserveJobsQuery.newBuilder().build(), new StreamObserver<JobChangeNotification>() {
            @Override
            public void onNext(JobChangeNotification jobChangeNotification) {
                switch (jobChangeNotification.getNotificationCase()) {
                    case JOBUPDATE:
                        logger.debug("<{}> JobUpdate {}", Thread.currentThread().getName(), jobChangeNotification.getJobUpdate().getJob().getId());
                        numJobUpdates.incrementAndGet();
                        break;
                    case TASKUPDATE:
                        logger.debug("<{}> TaskUpdate {}", Thread.currentThread().getName(), jobChangeNotification.getTaskUpdate().getTask().getId());
                        final Task task = jobChangeNotification.getTaskUpdate().getTask();
                        sink.next(task);
                        numTaskUpdates.incrementAndGet();
                        break;
                    case SNAPSHOTEND:
                        logger.info("<{}> SnapshotEnd {}", Thread.currentThread().getName(), jobChangeNotification);
                        numSnapshotUpdates.incrementAndGet();
                        break;
                    default:
                        logger.error("<{}> Unknown Notification ? {}", Thread.currentThread().getName(), jobChangeNotification.getNotificationCase());
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Exception in ObserveJobs :: ", t);
                apiErrors.incrementAndGet();
                sink.error(t);
            }

            @Override
            public void onCompleted() {
                logger.info("STREAM completed ?");
                sink.complete();
            }
        }));
    }

    @Override
    public Mono<Job> getJobById(String jobId) {
        return Mono.create(sink -> jobManagementService.findJob(JobId.newBuilder().setId(jobId).build(), new StreamObserver<Job>() {
            @Override
            public void onNext(Job job) {
                logger.debug("<{}> Getting Job for Id {}", Thread.currentThread().getName(), job.getId());
                sink.success(job);
            }

            @Override
            public void onError(Throwable t) {
                logger.error(t.getMessage());
                apiErrors.incrementAndGet();
                sink.error(t);
            }

            @Override
            public void onCompleted() {
            }
        }));
    }

    private void configureMetrics(Registry registry) {
        PolledMeter.using(registry)
                .withId(registry.createId(EsTaskPublisherMetrics.METRIC_ES_PUBLISHER + "titusApi.errors"))
                .monitorValue(apiErrors);
        PolledMeter.using(registry)
                .withId(registry.createId(EsTaskPublisherMetrics.METRIC_ES_PUBLISHER + "titusApi.numSnapshotUpdates"))
                .monitorValue(numSnapshotUpdates);
        PolledMeter.using(registry)
                .withId(registry.createId(EsTaskPublisherMetrics.METRIC_ES_PUBLISHER + "titusApi.numTaskUpdates"))
                .monitorValue(numTaskUpdates);
        PolledMeter.using(registry)
                .withId(registry.createId(EsTaskPublisherMetrics.METRIC_ES_PUBLISHER + "titusApi.numJobUpdates"))
                .monitorValue(numJobUpdates);
    }

}