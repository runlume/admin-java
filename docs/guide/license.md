# 开源协议

本项目采用 **MIT License**，完整文本见仓库根目录的 [LICENSE](https://github.com/runlume/admin-java/blob/main/LICENSE)。

```text
Copyright (c) 2026 Runlume
```

## 你可以做什么

- 商业使用：把模板复制进公司内部或商业产品，不需要开源你的业务代码；
- 修改、合并、发布、再许可都可以，只需保留版权与许可声明；
- 不提供担保：软件按"现状"提供，作者不承担使用产生的责任。

## 需要注意什么

| 事项       | 说明                                                                                                                                             |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| 保留声明   | 分发（含内部复制）时保留 `LICENSE` 里的版权与许可声明                                                                                            |
| 品牌资源   | `docs/brand/` 下的标识属于品牌资产，替换成你自己的；MIT 只覆盖代码                                                                               |
| 平台集成 SDK | `app.runlume.platform:platform-integration-sdk-java` 由 Runlume 单独发布，**不属于本仓库 MIT 的授权范围**，只能凭平台授予的凭据与许可使用，不要重新分发制品 |
| 第三方依赖 | Spring Boot、PostgreSQL JDBC Driver、Flyway、jOOQ 等各自遵循自身协议，见 `gradle/libs.versions.toml` 与实际解析出的依赖树                      |

## 想换协议

如果业务需要专利授权条款，可以换成 Apache-2.0：替换 `LICENSE` 文件，并同步本页与两份 README。
