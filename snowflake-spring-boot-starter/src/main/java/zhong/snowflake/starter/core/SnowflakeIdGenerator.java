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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 雪花算法<p>
 * <p>
 * [0, 1)   固定 0<br>
 * [1, 42)  时间戳<br>
 * [42, 52) 标识位（存储机器码）<br>
 * [42, 47) 数据中心 ID<br>
 * [47, 52) 工作机器 ID<br>
 * [52, 64) 序号
 *
 * @author Zhong
 * @since 0.0.1
 */
public class SnowflakeIdGenerator implements IdGenerator {
    private static final int TIMESTAMP_BITS = 41;
    private static final int FLAGS_BITS = 10;
    private static final int DATA_CENTER_ID_BITS = 5;
    private static final int WORKER_ID_BITS = 5;
    private static final int SEQUENCE_BITS = 12;

    private static final int TIMESTAMP_SHIFT = FLAGS_BITS + SEQUENCE_BITS;
    private static final int FLAGS_SHIFT = SEQUENCE_BITS;
    private static final int DATA_CENTER_ID_SHIFT = WORKER_ID_BITS + SEQUENCE_BITS;
    private static final int WORKER_ID_SHIFT = SEQUENCE_BITS;

    private static final long TIMESTAMP_MASK = (~(-1L << TIMESTAMP_BITS)) << TIMESTAMP_SHIFT;
    private static final long FLAGS_MASK = (~(-1L << (FLAGS_BITS))) << FLAGS_SHIFT;
    private static final long DATA_CENTER_ID_MASK = (~(-1L << DATA_CENTER_ID_BITS)) << DATA_CENTER_ID_SHIFT;
    private static final long WORKER_ID_MASK = (~(-1L << WORKER_ID_BITS)) << WORKER_ID_SHIFT;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    public static final int MIN_FLAGS = 0;
    public static final int MAX_FLAGS = (int) ~(-1L << FLAGS_BITS);
    public static final int MIN_DATA_CENTER_ID = 0;
    public static final int MAX_DATA_CENTER_ID = (int) ~(-1L << DATA_CENTER_ID_BITS);
    public static final int MIN_WORKER_ID = 0;
    public static final int MAX_WORKER_ID = (int) ~(-1L << WORKER_ID_BITS);
    public static final int MIN_SEQUENCE = 0;
    public static final int MAX_SEQUENCE = (int) ~(-1L << SEQUENCE_BITS);

    private static final long START_TIME;

    static {
        String date = "2024-01-01 00:00:00.000";
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        try {
            Date startDate = format.parse(date);
            START_TIME = startDate.getTime();
        } catch (ParseException e) {
            throw new IllegalStateException("初始化雪花算法起始时间错误：" + date, e);
        }
    }

    private volatile ReentrantLock lock;
    private volatile long timestamp;
    private final long flags;
    private volatile long sequence;

    public static void main(String[] args) throws ParseException {
        SnowflakeIdGenerator snowflake = new SnowflakeIdGenerator(5, 6);
        System.out.println(snowflake.getNextId());
        System.out.println(snowflake.getNextId());
        System.out.println(snowflake.getNextId());
        System.out.println(snowflake.getNextId());
    }

    public SnowflakeIdGenerator(final int flags) {
        if (flags < MIN_FLAGS || flags > MAX_FLAGS) {
            throw new IllegalArgumentException("flags 无效：" + flags + "，参考值 [" + MIN_FLAGS + ", " + MAX_FLAGS + "]");
        }
        this.flags = flags << FLAGS_SHIFT;
    }

    public SnowflakeIdGenerator(final int dataCenterId, final int workerId) {
        if (dataCenterId < MIN_DATA_CENTER_ID || dataCenterId > MAX_DATA_CENTER_ID) {
            throw new IllegalArgumentException("dataCenterId 无效：" + dataCenterId + "，参考值 [" + MIN_DATA_CENTER_ID + ", " + MAX_DATA_CENTER_ID + "]");
        }
        if (workerId < MIN_WORKER_ID || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 无效：" + workerId + "，参考值 [" + MIN_WORKER_ID + ", " + MAX_WORKER_ID + "]");
        }
        this.flags = (((long) dataCenterId) << DATA_CENTER_ID_SHIFT) | (((long) workerId) << WORKER_ID_SHIFT);
    }

    private ReentrantLock getLock() {
        if (lock == null) {
            synchronized (this) {
                if (lock == null) {
                    lock = new ReentrantLock();
                }
            }
        }
        return lock;
    }

    @Override
    public Long getNextId() {
        try {
            getLock().lock();
            final long now = System.currentTimeMillis();
            /*
             * 上一次获取时间小于 now，当前 now 一定可以获取到 ID
             */
            return getNextId(now);
        } finally {
            getLock().unlock();
        }
    }

    /**
     * 获取下一个可用 ID
     *
     * @param now 获取时间，必须大于等于 time
     * @return null 指定的 now 时间 ID 已达上限
     * @throws IllegalArgumentException 如果 now 小于 time
     */
    private Long getNextId(final long now) {
        if (now < timestamp) {
            throw new IllegalArgumentException("now 不能小于 time，[now=" + now + ",time=" + timestamp + "]");
        }
        if (timestamp == 0) {
            /*
             * 第一次获取
             */
            // 时间戳重置
            return (((timestamp = now) - START_TIME) << TIMESTAMP_SHIFT) | flags;
        } else if (timestamp != now) {
            /*
             * now > time，肯定有可用 ID
             */
            // 序号重置为 0
            sequence = MIN_SEQUENCE;
            // 时间戳重置
            return (((timestamp = now) - START_TIME) << TIMESTAMP_SHIFT) | flags;
        } else {
            /*
             * now == time，当前 now/time 可能 ID 已达上限
             */
            if (sequence == MAX_SEQUENCE) {
                // 同一毫秒内生成的 ID 已达到最大值
                return null;
            } else {
                // 序号递增
                return ((timestamp - START_TIME) << TIMESTAMP_SHIFT) | flags | (++sequence);
            }
        }
    }

    public long getTimestampValue() {
        return timestamp;
    }

    public int getFlagsValue() {
        return (int) (flags >> FLAGS_BITS);
    }

    public int getDataCenterIdValue() {
        return (int) ((flags & DATA_CENTER_ID_MASK) >> DATA_CENTER_ID_SHIFT);
    }

    public int getWorkerIdValue() {
        return (int) ((flags & WORKER_ID_MASK) >> WORKER_ID_SHIFT);
    }

    public static long getTimestampValue(long id) {
        return START_TIME + ((id & TIMESTAMP_MASK) >> TIMESTAMP_SHIFT);
    }

    public static int getFlagsValue(long id) {
        return (int) ((id & FLAGS_MASK) >> FLAGS_SHIFT);
    }

    public static int getFlagsValue(int dataCenterId, int workerId) {
        if (dataCenterId < MIN_DATA_CENTER_ID || dataCenterId > MAX_DATA_CENTER_ID) {
            throw new IllegalArgumentException("dataCenterId 无效：" + dataCenterId + "，参考值 [" + MIN_DATA_CENTER_ID + ", " + MAX_DATA_CENTER_ID + "]");
        }
        if (workerId < MIN_WORKER_ID || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 无效：" + workerId + "，参考值 [" + MIN_WORKER_ID + ", " + MAX_WORKER_ID + "]");
        }
        return (dataCenterId << WORKER_ID_BITS) | workerId;
    }

    public static int getDataCenterIdValue(long id) {
        return (int) ((id & DATA_CENTER_ID_MASK) >> DATA_CENTER_ID_SHIFT);
    }

    public static int getWorkerIdValue(long id) {
        return (int) ((id & WORKER_ID_MASK) >> WORKER_ID_SHIFT);
    }

    public static int getSequenceValue(long id) {
        return (int) (id & SEQUENCE_MASK);
    }
}
