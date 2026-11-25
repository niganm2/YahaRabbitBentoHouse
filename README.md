# 呀哈兔便当屋（YahaRabbitBentoHouse）

基于 Spring Boot 的外卖点餐系统，包含管理端、用户端小程序和服务端三部分。

## 技术栈

- 后端：Spring Boot 2.7.3、MyBatis、Druid、MySQL、Redis、JWT、POI
- 管理端：Vue 2 + TypeScript + Element UI
- 用户端：微信小程序（`mini-program-dist/mp-weixin` 为编译产物）
- 第三方：阿里云 OSS（文件上传）、微信支付、百度地图

## 目录结构

```
backend/                 Maven 多模块后端
  sky-common/            公共工具、常量、配置属性
  sky-pojo/              实体、DTO、VO
  sky-server/            Controller、Service、Mapper，启动类 SkyApplication
takeOut-admin-vue/       管理端前端
mini-program-dist/       用户端小程序编译产物
sql/create_table.sql     建表脚本
```

## 环境要求

- JDK 8 及以上
- Maven 3.6+
- MySQL 8
- Redis 6+
- Node.js 14+（管理端）

## 启动步骤

1. 创建数据库并导入表结构：

   ```sql
   CREATE DATABASE sky_take_out DEFAULT CHARACTER SET utf8mb4;
   ```

   然后导入 `sql/create_table.sql`。

2. 准备本地配置：`backend/sky-server/src/main/resources/application-dev.yml` 已被 `.gitignore` 忽略。仓库内已提供一份本地示例，数据库账号密码默认取环境变量 `SKY_DB_USERNAME` / `SKY_DB_PASSWORD`（默认 `root/root`），可按需修改或设置环境变量。配置项模板见 `application-dev-template.yml`。

3. 启动 Redis。

4. 微信支付证书：将 `apiclient_key.pem`、`apiclient_cert.pem` 放到 `backend/sky-server/src/main/resources/cert/` 下。该目录已被忽略，禁止提交。若只是本地调试不下单支付，可先不配置，但需要保证 `application-dev.yml` 中证书路径存在，否则支付工具类初始化会失败。

5. 启动后端：

   ```bash
   mvn -f backend/pom.xml clean package -DskipTests
   java -jar backend/sky-server/target/sky-server-1.0-SNAPSHOT.jar
   ```

   或在 IDE 中运行 `com.sky.SkyApplication`。默认端口 `8080`，接口文档：`http://localhost:8080/doc.html`。

6. 启动管理端：

   ```bash
   cd takeOut-admin-vue
   npm install
   npm run serve
   ```

7. 小程序：用微信开发者工具打开 `mini-program-dist/mp-weixin`，并在开发工具中关闭域名校验或配置合法域名。

## 配置与安全说明

- `application-dev.yml`、`cert/` 目录均已加入 `.gitignore`，不要把数据库密码、OSS/微信支付密钥、商户私钥提交到仓库。
- 如果私钥曾经提交过，即使现在删除，它仍存在于 Git 历史中。请立即到微信支付商户平台作废并重新生成证书，必要时使用 `git filter-repo` 清理历史。
- JWT 密钥目前为示例值（`itcast` / `itheima`），部署到公网前请替换为足够长的随机串。

## 测试

```bash
mvn -f backend/pom.xml test
```

当前仓库暂无自动化测试用例，测试目录已不再被 `.gitignore` 排除，可以正常提交。
