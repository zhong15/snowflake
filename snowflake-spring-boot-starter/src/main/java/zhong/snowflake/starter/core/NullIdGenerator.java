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

/**
 * @author Zhong
 * @since 0.0.1
 */
public class NullIdGenerator implements IdGenerator {
    private static volatile NullIdGenerator instance;

    private NullIdGenerator() {
    }

    public static IdGenerator getInstance() {
        if (instance == null) {
            synchronized (NullIdGenerator.class) {
                if (instance == null) {
                    instance = new NullIdGenerator();
                }
            }
        }
        return instance;
    }

    @Override
    public Long getNextId() {
        return null;
    }
}
