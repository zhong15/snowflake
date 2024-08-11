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

package zhong.snowflake.starter.test;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import zhong.snowflake.starter.core.IdGenerator;

/**
 * @author Zhong
 * @since 0.0.1
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {Application.class})
public class IdGeneratorTest {
    private static final Logger log = LoggerFactory.getLogger(IdGeneratorTest.class);

    @Autowired
    private IdGenerator idGenerator;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    public void test_bean() {
        Assert.assertNotNull(idGenerator);
        Assert.assertNotNull(redisTemplate);
    }
}
