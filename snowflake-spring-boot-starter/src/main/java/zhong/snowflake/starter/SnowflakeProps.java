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

package zhong.snowflake.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import zhong.snowflake.starter.core.SnowflakeIdGenerator;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * @author Zhong
 * @since 0.0.1
 */
@Validated
@ConfigurationProperties(prefix = SnowflakeProps.PROPERTIES_PREFIX)
public class SnowflakeProps {
    static final String PROPERTIES_PREFIX = "zhong.snowflake";

    private static final int MIN_KEEP_ALIVE = 5_000;
    private static final int DEFAULT_KEEP_ALIVE = 60_000;
    private static final String MIN_FACTOR = "1.1";
    private static final double DEFAULT_FACTOR = 1.5;

    @Min(value = MIN_KEEP_ALIVE)
    private Integer keepAlive = DEFAULT_KEEP_ALIVE;

    @DecimalMin(value = MIN_FACTOR)
    private Double factor = DEFAULT_FACTOR;

    @Max(value = SnowflakeIdGenerator.MAX_FLAGS)
    @Min(value = SnowflakeIdGenerator.MIN_FLAGS)
    private Integer flags;

    @Max(value = SnowflakeIdGenerator.MAX_DATA_CENTER_ID)
    @Min(value = SnowflakeIdGenerator.MIN_DATA_CENTER_ID)
    private Integer dataCenterId;

    @Max(value = SnowflakeIdGenerator.MAX_WORKER_ID)
    @Min(value = SnowflakeIdGenerator.MIN_WORKER_ID)
    private Integer workerId;

    public Integer getKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(Integer keepAlive) {
        this.keepAlive = keepAlive;
    }

    public void setFactor(Double factor) {
        this.factor = factor;
    }

    public Double getFactor() {
        return factor;
    }

    public Integer getFlags() {
        return flags;
    }

    public void setFlags(Integer flags) {
        this.flags = flags;
    }

    public Integer getDataCenterId() {
        return dataCenterId;
    }

    public void setDataCenterId(Integer dataCenterId) {
        this.dataCenterId = dataCenterId;
    }

    public Integer getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Integer workerId) {
        this.workerId = workerId;
    }
}
