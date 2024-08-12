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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Zhong
 * @since 0.0.1
 */
@RunWith(JUnit4.class)
public class SnowflakeIdGeneratorTest {
    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGeneratorTest.class);

    @Test
    public void test_getNextId_1() {
        test_getNextId_core(new SnowflakeIdGenerator(1));
    }

    @Test
    public void test_getNextId_2() {
        test_getNextId_core(new SnowflakeIdGenerator(1, 2));
    }

    private void test_getNextId_core(final SnowflakeIdGenerator s) {
        final AtomicBoolean isFailure = new AtomicBoolean(false);
        final int threadNumber = 10;
        final CyclicBarrier cb = new CyclicBarrier(threadNumber);
        final CountDownLatch cdl = new CountDownLatch(threadNumber);
        List<Thread> threadList = new ArrayList<>(threadNumber);

        final int len = 1024 * 10;
        final long[][] arr = new long[threadNumber][len];

        for (int i = 0; i < threadNumber; i++) {
            final int threadIndex = i;
            Thread thread = new Thread(() -> {
                log.info("线程 {} 开始", threadIndex);
                try {
                    cb.await();
                } catch (InterruptedException e) {
                    isFailure.set(true);
                    log.info("线程执行被中断", e);
                } catch (BrokenBarrierException e) {
                    isFailure.set(true);
                    log.info("线程执行错误", e);
                    cancelThreads(threadList, Thread.currentThread());
                }

                log.info("线程 {} 越过栅栏", threadIndex);

                for (int j = 0; j < len; j++) {
                    for (; ; ) {
                        Long id = s.getNextId();
                        if (id != null) {
                            arr[threadIndex][j] = id;
                            break;
                        }
                    }
                }
                Set<Long> set = new HashSet<>(len);
                for (long id : arr[threadIndex]) {
                    set.add(id);
                }
                if (len != set.size()) {
                    isFailure.set(true);
                    log.info("线程 {} 存在重复 ID", threadIndex);
                }

                cdl.countDown();
                log.info("线程 {} 结束", threadIndex);
            });
            threadList.add(thread);
            thread.start();
        }

        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error("线程执行被中断", e);
            Assert.fail();
        }

        if (isFailure.get()) {
            Assert.fail();
        }

        Set<Long> set = new HashSet<>(len * threadNumber);
        for (int i = 0; i < threadNumber; i++) {
            for (long id : arr[i]) {
                set.add(id);
            }
        }
        Assert.assertEquals(len * threadNumber, set.size());
    }

    private static void cancelThreads(List<Thread> list, Thread exclude) {
        for (Thread e : list) {
            if (e == exclude) {
                continue;
            }
            if (e.isAlive()) {
                e.interrupt();
            }
        }
    }
}
