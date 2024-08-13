/*
 * Copyright 2024 the original author or authors.
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

package zhong.snowflake.starter.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import zhong.redis.lock.starter.core.RedisLock;
import zhong.redis.lock.starter.utils.NamedThreadFactory;
import zhong.snowflake.starter.SnowflakeConfig;
import zhong.snowflake.starter.SnowflakeProps;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Zhong
 * @since 0.0.1
 */
@Component
public class RedisSnowflakeIdGenerator implements IdGenerator {
    private static final Logger log = LoggerFactory.getLogger(RedisSnowflakeIdGenerator.class);

    private static final String namespace = "snowflake:";

    private volatile boolean isOpen;
    private ScheduledExecutorService threadPool;
    private volatile boolean isTaskRunning;
    private volatile long syncTime;
    private int keepAlive;
    private double factor;

    @Autowired
    private SnowflakeProps snowflakeProps;
    @Autowired
    private RedisLock redisLock;

    private volatile IdGenerator proxyObject;

    @Override
    public Long getNextId() {
        if (isOpen) {
            return proxyObject.getNextId();
        } else {
            return null;
        }
    }

    @PostConstruct
    public void init() {
        log.info("init");
        initProp();
        boolean shouldStartKeepAliveTask = initIdGenerator(0);
        if (shouldStartKeepAliveTask) {
            initThreadPool();
            startKeepAliveTask();
        }
    }

    @PreDestroy
    public void destroy() {
        log.info("destroy");
        log.info("开始 threadPool shutdown");
        if (threadPool == null) {
            log.info("已跳过，原因：threadPool null");
            return;
        }
        if (threadPool.isShutdown()) {
            log.info("已跳过，原因：threadPool 已经 shutdown");
            return;
        }
        threadPool.shutdown();
        log.info("结束 threadPool shutdown");
    }

    private void initProp() {
        log.info("init properties");

        keepAlive = snowflakeProps.getKeepAlive();
        log.info("init keepAlive: {}", keepAlive);

        factor = snowflakeProps.getFactor();
        log.info("init factor: {}", factor);
    }

    private boolean initIdGenerator(int stage) {
        log.info("获取雪花算法 flags / dataCenterId workerId");

        if (snowflakeProps.getFlags() != null) {
            log.info("指定雪花算法 flags={}", snowflakeProps.getFlags());
            doInitIdGenerator(snowflakeProps.getFlags());
            return false;
        }
        if (snowflakeProps.getDataCenterId() != null && snowflakeProps.getWorkerId() != null) {
            log.info("指定雪花算法 dataCenterId={} workerId={}", snowflakeProps.getDataCenterId(), snowflakeProps.getWorkerId());
            doInitIdGenerator(snowflakeProps.getDataCenterId(), snowflakeProps.getWorkerId());
            return false;
        }

        int minDataCenterId = SnowflakeIdGenerator.MIN_DATA_CENTER_ID;
        int maxDataCenterId = SnowflakeIdGenerator.MAX_DATA_CENTER_ID;
        if (snowflakeProps.getDataCenterId() != null) {
            log.info("指定雪花算法 dataCenterId={}", snowflakeProps.getDataCenterId());
            minDataCenterId = maxDataCenterId = snowflakeProps.getDataCenterId();
        }
        final int minWorkerId = SnowflakeIdGenerator.MIN_WORKER_ID;
        final int maxWorkerId = SnowflakeIdGenerator.MAX_WORKER_ID;

        for (int i = minDataCenterId; i <= maxDataCenterId; i++) {
            for (int j = minWorkerId; j <= maxWorkerId; j++) {
                final int flags = SnowflakeIdGenerator.getFlagsValue(i, j);
                final boolean success = redisLock.lock(namespace + flags, SnowflakeConfig.SERVER_UUID, (long) (keepAlive * factor), TimeUnit.MILLISECONDS);
                if (success) {
                    log.info("获取到雪花算法 flags={}", flags);
                    doInitIdGenerator(flags);
                    return true;
                }
            }
        }
        if (stage == 0) {
            throw new IllegalStateException("获取雪花算法 flags / dataCenterId workerId 失败，原因：Redis 中暂时没有空闲的值！当前 keepAlive 值：" + factor + " x " + keepAlive);
        } else {
            log.info("获取雪花算法 flags / dataCenterId workerId 失败，原因：Redis 中暂时没有空闲的值！当前 keepAlive 值：" + factor + " x " + keepAlive);
        }
        return true;
    }

    private void doInitIdGenerator(int flags) {
        proxyObject = new SnowflakeIdGenerator(flags);
        isOpen = true;
        syncTime = System.currentTimeMillis();
    }

    private void doInitIdGenerator(int dataCenterId, int workerId) {
        proxyObject = new SnowflakeIdGenerator(dataCenterId, workerId);
        isOpen = true;
        syncTime = System.currentTimeMillis();
    }

    private void initThreadPool() {
        log.info("初始化线程池");
        threadPool = Executors.newScheduledThreadPool(1, new NamedThreadFactory("雪花算法定时任务"));
    }

    private void startKeepAliveTask() {
        threadPool.scheduleAtFixedRate(() -> {
            log.info("开始延长雪花算法时长定时任务");
            if (isTaskRunning) {
                log.info("已跳过，原因：上一个任务未结束");
                return;
            }
            try {
                isTaskRunning = true;
                final long now = System.currentTimeMillis();
                log.info("上一次成功时间：{}", syncTime);
                log.info("当前打开状态 isOpen={}", isOpen);
                if (isOpen) {
                    final int flags = ((SnowflakeIdGenerator) proxyObject).getFlagsValue();
                    log.info("当前 flags={}", flags);

                    final boolean expireSuccess = redisLock.expire(namespace + flags, SnowflakeConfig.SERVER_UUID, (long) (keepAlive * factor), TimeUnit.MILLISECONDS);
                    log.info("延长时长成功={}", expireSuccess);
                    if (expireSuccess) {
                        syncTime = now;
                        isOpen = true;
                    } else {
                        isOpen = false;
                        log.info("开始尝试重置");
                        Boolean resetSuccess = redisLock.lock(namespace + flags, SnowflakeConfig.SERVER_UUID, (long) (keepAlive * factor), TimeUnit.MILLISECONDS);
                        log.info("尝试重置成功={}", resetSuccess);
                        if (resetSuccess) {
                            syncTime = now;
                            isOpen = true;
                        } else {
                            log.info("重置 proxyObject=NullIdGenerator");
                            proxyObject = null;
                            log.info("开始尝试遍历获取");
                            initIdGenerator(1);
                        }
                    }
                } else {
                    log.info("开始尝试遍历获取");
                    initIdGenerator(1);
                }
            } catch (Exception e) {
                log.error("雪花算法定时任务失败", e);
            } finally {
                isTaskRunning = false;
            }
        }, keepAlive, keepAlive, TimeUnit.MILLISECONDS);
    }
}
