# snowflake
雪花算法分布式 ID

## 使用方式
- 引入 Maven 依赖
```xml
<dependency>
    <groupId>zhong</groupId>
    <artifactId>snowflake-spring-boot-starter</artifactId>
    <version>${revision}</version>
</dependency>
```
- 配置 application.yml
```yaml
zhong:
  snowflake:
    enable: true    # 是否启用，默认：false
    flags:          # 雪花算法标志位，范围 [0, 1024)
    data-center-id: # 雪花算法数据中心 ID，范围 [0, 32)，如果 flags 为 null 则生效
    worker-id:      # 雪花算法工作机器 ID，范围 [0, 32)，如果 flags 为 null 且 data-center-id 非 null 则生效
    keep-alive:     # 定时任务执行周期，默认 60000，最小值 5000，单位：ms
                    # 生效条件：flags 为 null 且 data-center-id、worker-id 至少一个为 null 则开启定时任务
    factor:         # keep-alive 系数，默认 1.5，最小值 1.1，即雪花算法锁定 Redis 标志位的时长为 keep-alive * factor
```
