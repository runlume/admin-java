# 开源协议

本项目采用 **Apache License 2.0**，完整文本见仓库根目录的
[LICENSE](https://github.com/runlume/admin-java/blob/main/LICENSE)，署名信息见
[NOTICE](https://github.com/runlume/admin-java/blob/main/NOTICE)。

```text
Copyright 2026 Runlume
```

## 你可以做什么

- 商业使用：把模板复制进公司内部或商业产品，不需要开源你的业务代码；
- 修改、合并、发布、再分发（含闭源发布）都可以，只需保留版权与许可声明；
- 专利授权：贡献者就本作品所必需且可授权的专利，授予你永久、全球、免许可费的许可；若你发起专利诉讼，该授权终止；
- 不提供担保：软件按"现状"提供，作者不承担使用产生的责任。

## 需要注意什么

| 事项         | 说明                                                                                                                                                     |
| ------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 保留声明     | 分发（含内部复制）时保留 `LICENSE` 的许可文本；本仓库带 `NOTICE`，分发时需一并提供其中的署名信息                                                          |
| 标注改动     | 修改过的文件要注明"已修改"；整份复制进业务系统后，按自己的方式在改动文件上标注即可                                                                        |
| 商标         | 协议不授予商标权；`docs/brand/` 下的标识属于品牌资产，替换成你自己的                                                                                      |
| 平台集成 SDK | `app.runlume.platform:platform-integration-sdk-java` 由 Runlume 单独发布，**不属于本仓库 Apache-2.0 的授权范围**，只能凭平台授予的凭据与许可使用，不要重新分发制品 |
| 第三方依赖   | Spring Boot、PostgreSQL JDBC Driver、Flyway、jOOQ 等各自遵循自身协议，完整清点见 [第三方许可与署名](https://github.com/runlume/admin-java/blob/main/docs/licenses/THIRD-PARTY-NOTICES.md)（随 jar 打入 `META-INF/licenses/`） |

## 与 MIT 的差别

MIT 只要求保留版权与许可声明；Apache-2.0 在此基础上额外要求保留 `NOTICE`、标注修改过的文件，并显式约定
专利授权与商标保留。对"整份复制进业务系统、不再回传"的用法，两者的实际约束差别不大；差异主要体现在
企业法务对专利条款的要求上。
