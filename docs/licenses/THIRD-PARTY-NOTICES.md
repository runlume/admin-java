# 第三方许可与署名

本文件列出会打进 `admin-java-<version>.jar`（Spring Boot 可执行 jar）的第三方组件及其许可。
清点口径是 `BOOT-INF/lib/` 下的实际制品，而不是 `build.gradle` 里的声明 —— 传递依赖也计入。

本文件与仓库根目录的 `LICENSE`、`NOTICE` 会一起打进 jar 的 `META-INF/` 与 `META-INF/licenses/`，
见 `build.gradle` 中 `tasks.withType(Jar)` 的配置。

## 组件清单

| 组件 | 版本 | 许可 |
| --- | --- | --- |
| Spring Boot（25 个模块） | 4.1.1 | Apache-2.0 |
| Spring Framework（aop、beans、context、core、expression、jdbc、tx、web、webmvc） | 7.0.9 | Apache-2.0 |
| Spring Security（config、core、crypto、oauth2-core、oauth2-jose、oauth2-resource-server、web） | 7.1.1 | Apache-2.0 |
| Spring Session（core、jdbc） | 4.1.1 | Apache-2.0 |
| Spring Modulith API | 2.1.0 | Apache-2.0 |
| Apache Tomcat（embed-core、embed-el、embed-websocket） | 11.0.24 | Apache-2.0 |
| Micrometer（commons、core、jakarta9、observation） | 1.17.1 | Apache-2.0 |
| Flyway（core、database-postgresql） | 12.4.0 | Apache-2.0 |
| jOOQ | 3.21.7 | Apache-2.0 |
| HikariCP | 7.0.2 | Apache-2.0 |
| Hibernate Validator | 9.1.3.Final | Apache-2.0 |
| Jackson（annotations 2.21、core 3.1.5、databind 3.1.5） | 2.21 / 3.1.5 | Apache-2.0 |
| Log4j（log4j-api、log4j-to-slf4j） | 2.25.5 | Apache-2.0 |
| Nimbus JOSE + JWT | 10.9.1 | Apache-2.0 |
| jakarta.validation-api | 3.1.1 | Apache-2.0 |
| jboss-logging | 3.6.3.Final | Apache-2.0 |
| r2dbc-spi | 1.0.0.RELEASE | Apache-2.0 |
| SnakeYAML | 2.6 | Apache-2.0 |
| ClassMate | 1.7.3 | Apache-2.0 |
| commons-logging | 1.3.6 | Apache-2.0 |
| JSpecify | 1.0.1 | Apache-2.0 |
| checker-qual | 3.55.1 | MIT |
| SLF4J（slf4j-api、jul-to-slf4j） | 2.0.18 | MIT |
| reactive-streams | 1.0.4 | MIT-0 |
| PostgreSQL JDBC Driver | 42.7.13 | BSD-2-Clause |
| jakarta.annotation-api | 3.0.0 | EPL-2.0 或 GPL-2.0 with Classpath Exception |
| Logback（logback-classic、logback-core） | 1.5.38 | EPL-1.0 或 LGPL-2.1 |
| HdrHistogram | 2.2.2 | CC0-1.0（公有领域） |
| Runlume 平台组件（platform-integration-sdk-java、platform-integration-spring-boot-starter、platform-deployment-license-core、platform-deployment-license-sdk-java） | 1.3.0 / 1.0.6 / 2.0.0 | 专有，**不属于本仓库 Apache-2.0 的授权范围** |

## 分发时需要注意

1. 每个第三方 jar 内部都自带自己的许可与署名文件（例如 `spring-boot-*.jar` 内的 `LICENSE.txt`、
   `NOTICE.txt`），随 fat jar 一起分发时它们仍在 `BOOT-INF/lib/*.jar` 内，不需要额外处理。
2. 仓库自己的 `LICENSE`、`NOTICE` 与本清单会被打进 `META-INF/`、`META-INF/licenses/`；
   交付客户可自行运行的镜像时，也可以把本文件单独附在镜像或交付包里。
3. **Logback** 是 EPL-1.0 或 LGPL-2.1 双许可，模板未修改地打包使用，属于聚合分发，不触发源码公开义务；
   若交付对象的法务不接受弱 copyleft 依赖，可改用 `spring-boot-starter-log4j2` 替换掉它。
4. **jakarta.annotation-api** 同理为 EPL-2.0 或 GPL-2.0 with Classpath Exception 双许可，未修改地打包使用。
5. **HdrHistogram** 采用 CC0-1.0，属于公有领域贡献，无署名义务。
6. 平台 SDK 与 Deployment License 组件由 Runlume 单独发布，只能凭平台授予的凭据与许可使用，
   不要重新分发制品；它们的许可边界另见 `docs/guide/license.md` 与仓库根目录的 `NOTICE`。

## 如何更新

依赖升级后按同一口径重新清点：构建出 fat jar，枚举 `BOOT-INF/lib/*.jar`，
逐个读取嵌套 jar 内 `META-INF/maven/**/pom.xml` 的 `<licenses>`；
未声明许可的制品按其内部 `META-INF/LICENSE*` 文本判定。本文件记录的是清点时的版本快照。
