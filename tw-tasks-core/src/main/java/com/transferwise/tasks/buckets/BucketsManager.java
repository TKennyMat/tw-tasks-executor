package com.transferwise.tasks.buckets;

import com.transferwise.common.baseutils.ExceptionUtils;
import com.transferwise.tasks.IPriorityManager;
import com.transferwise.tasks.TasksProperties;
import com.transferwise.tasks.helpers.ICoreMetricsTemplate;
import com.transferwise.tasks.processing.GlobalProcessingState;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

public class BucketsManager implements IBucketsManager, InitializingBean {

  @Autowired
  private TasksProperties tasksProperties;
  @Autowired
  private GlobalProcessingState globalProcessingState;
  @Autowired
  private IPriorityManager priorityManager;
  @Autowired
  private ICoreMetricsTemplate coreMetricsTemplate;

  private final Map<String, BucketProperties> bucketsProperties = new ConcurrentHashMap<>();
  private List<String> bucketIds;

  @Override
  public void afterPropertiesSet() {
    ExceptionUtils.doUnchecked(() -> {
      add(IBucketsManager.DEFAULT_ID, new BucketProperties()
          .setMaxTriggersInMemory(tasksProperties.getMaxTriggersInMemory())
          .setTriggeringTopicPartitionsCount(tasksProperties.getMaxNodeCount())
          .setTriggerSameTaskInAllNodes(tasksProperties.isTriggerSameTaskInAllNodes())
          .setTriggersFetchSize(tasksProperties.getTriggerFetchSize())
          .setAutoResetOffsetTo(tasksProperties.getAutoResetOffsetTo())
          .setTriggerInSameProcess(tasksProperties.isTriggerInSameProcess())
          .setAutoStartProcessing(tasksProperties.isAutoStartProcessing())
          .setTaskGrabbingMaxConcurrency(tasksProperties.getTaskGrabbingMaxConcurrency())
      );

      registerUniqueBucketIds();
    });
    coreMetricsTemplate.registerBucketsCount(() -> bucketIds.size());
  }

  protected void registerUniqueBucketIds() {
    TreeSet<String> uniqueBucketIds = new TreeSet<>();
    uniqueBucketIds.add(IBucketsManager.DEFAULT_ID);
    uniqueBucketIds.addAll(tasksProperties.getAdditionalProcessingBuckets());

    bucketIds = new CopyOnWriteArrayList<>(uniqueBucketIds);
    bucketIds.sort(Comparator.naturalOrder());

    for (String bucketId : bucketIds) {
      globalProcessingState.getBuckets().put(bucketId,
          new GlobalProcessingState.Bucket(priorityManager.getHighestPriority(), priorityManager.getLowestPriority())
              .setBucketId(bucketId)
              .setTasksGrabbingSemaphore(new Semaphore(tasksProperties.getTaskGrabbingMaxConcurrency()))
      );
    }
  }

  @SuppressWarnings("SameParameterValue")
  protected void add(String bucketId, BucketProperties properties) {
    ExceptionUtils.doUnchecked(() -> {
      bucketsProperties.put(bucketId, properties);
    });
  }

  protected BucketProperties getDefaultProperties() {
    return bucketsProperties.get(IBucketsManager.DEFAULT_ID);
  }

  @Override
  public BucketProperties getBucketProperties(String bucketId) {
    BucketProperties bucketProperties = bucketsProperties.get(bucketId);
    if (bucketProperties == null) {
      return getDefaultProperties();
    }
    return bucketProperties;
  }

  @Override
  public void registerBucketProperties(String bucketId, BucketProperties bucketProperties) {
    ExceptionUtils.doUnchecked(() -> {
      if (bucketsProperties.containsKey(bucketId)) {
        throw new IllegalStateException("BucketProperties for '" + bucketId + "' are already registered.");
      }
      BucketProperties defaultProperties = getDefaultProperties();

      if (bucketProperties.getTriggerSameTaskInAllNodes() == null) {
        bucketProperties.setTriggerSameTaskInAllNodes(defaultProperties.getTriggerSameTaskInAllNodes());
      }
      if (bucketProperties.getMaxTriggersInMemory() == null) {
        bucketProperties.setMaxTriggersInMemory(defaultProperties.getMaxTriggersInMemory());
      }
      if (bucketProperties.getTriggeringTopicPartitionsCount() == null) {
        bucketProperties.setTriggeringTopicPartitionsCount(defaultProperties.getTriggeringTopicPartitionsCount());
      }
      if (bucketProperties.getTriggersFetchSize() == null) {
        bucketProperties.setTriggersFetchSize(defaultProperties.getTriggersFetchSize());
      }
      if (bucketProperties.getAutoResetOffsetTo() == null) {
        bucketProperties.setAutoResetOffsetTo(defaultProperties.getAutoResetOffsetTo());
      }
      if (bucketProperties.getTriggerInSameProcess() == null) {
        bucketProperties.setTriggerInSameProcess(defaultProperties.getTriggerInSameProcess());
      }
      if (bucketProperties.getAutoStartProcessing() == null) {
        bucketProperties.setAutoStartProcessing(defaultProperties.getAutoStartProcessing());
      }
      if (bucketProperties.getTaskGrabbingMaxConcurrency() == null) {
        bucketProperties.setTaskGrabbingMaxConcurrency(defaultProperties.getTaskGrabbingMaxConcurrency());
      }
      bucketsProperties.put(bucketId, bucketProperties);
    });
  }

  @Override
  public List<String> getBucketIds() {
    return bucketIds;
  }

  @Override
  public boolean isConfiguredBucket(String bucketId) {
    return DEFAULT_ID.equals(bucketId) || bucketIds.contains(bucketId);
  }
}
