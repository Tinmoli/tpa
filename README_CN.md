# tpa <img alt="tpa Logo" src="fabric/src/main/resources/assets/tpa/icon.png" width="30"/>

> **[English Documentation](https://github.com/Tinmoli/tpa/blob/main/README.md)** | 点击这里查看英文文档

一个 Minecraft 服务端模组，添加了各种与传送相关的指令，包括 /home、/tpa、/back、/rtp 等

当前版本：**1.0.7**

项目地址：[https://github.com/Tinmoli/tpa](https://github.com/Tinmoli/tpa)

这里是[更新日志](https://github.com/Tinmoli/tpa/blob/main/CHANGELOG_CN.md)

## 支持的平台

| 平台 | 支持版本 |
|------|----------|
| Fabric | 1.21.11、26.1、26.1.1、26.1.2、26.2、26.3 |

> 仅支持 Fabric。

## 依赖

- Fabric Loader（使用各版本 JAR 所声明的最低版本）
- Minecraft 1.21.11、26.1、26.1.1、26.1.2、26.2 或 26.3
- Java 21（Minecraft 1.21.11）或 Java 25（Minecraft 26.x 系列）

## 目前可用的指令

- `/tpals` - 获取可用命令
- `/spawn [<禁用安全检查>]` - 传送到主世界出生点，传入 `true` 跳过安全检查
- `/back [<禁用安全检查>]` - 传送到上次死亡地点，传入 `true` 跳过安全检查
- `/sethome <名称>` - 设置一个传送点
- `/home [<名称>]` - 回到传送点，不填名称则前往默认传送点
- `/delhome <名称>` - 删除一个传送点
- `/renamehome <名称> <新名称>` - 重命名一个传送点
- `/defaulthome <名称>` - 设置默认传送点
- `/homes` - 查看所有传送点；可在 GUI 的全部原版物品列表中选择 Home 图标
- `/warp <名称>` - 传送到地标
- `/warps` - 查看所有公共传送点；管理员可在 GUI 中自定义 Warp 图标
- `/setwarp <名称>` - 设置公共传送点（需要管理员权限）
- `/delwarp <名称>` - 删除公共传送点（需要管理员权限）
- `/renamewarp <名称> <新名称>` - 重命名公共传送点（需要管理员权限）
- `/tpa <玩家>` - 向玩家发送传送请求
- `/tpahere <玩家>` - 请求将玩家传送到你这里
- `/tpaaccept <玩家>` - 接受传送请求
- `/tpadeny <玩家>` - 拒绝传送请求
- `/rtp [<维度>]` - 随机传送到世界各处，可指定维度
- `/tpastorage json-to-sqlite` - 将旧版 JSON 数据导入 SQLite，并在成功后自动重载（需要管理员权限）
- `/tpareload` - 重新加载配置、SQLite 数据和内置语言文件（需要管理员权限）

<br>

## 配置文件

配置文件位于 `config/tpa/config.yml`，每项配置均附有中文注释，支持以下配置：

```yaml
# TPA 插件配置文件
# 修改后执行 /tpareload 生效，也可以重启服务器

# 语言设置，可选值: zh_cn, en_us
language: zh_cn
# /back 命令配置
back:
  # 是否启用该命令
  enabled: true
  # 传送后是否删除死亡位置记录
  deleteAfterTeleport: false
# /home 命令配置
home:
  enabled: true
  # 每位玩家最多可以设置的家的数量
  playerMaximum: 20
  # 是否自动删除无效的位置（世界不存在时）
  deleteInvalid: false
  # 传送等待时间（秒），0 表示立即传送
  delay: 0
# /tpa 命令配置
tpa:
  enabled: true
  delay: 3
  # 传送等待期间移动是否取消传送
  cancelOnMove: true
  # 请求过期前多少秒提醒；请求固定 120 秒过期，0 表示不提醒
  requestExpireReminder: 30
# /warp 命令配置
warp:
  enabled: true
  deleteInvalid: false
# /spawn 命令配置
spawn:
  enabled: true
  # 出生点所在世界的 ID，默认为主世界
  world_id: minecraft:overworld
# /rtp 命令配置
rtp:
  enabled: true
  # 随机传送最小范围（方块）
  minRange: 1000
  # 随机传送最大范围（方块）
  maxRange: 2000
  # 是否启用 RTP 请求冷却
  cooldownEnabled: true
  # RTP 成功后的冷却时间（秒），0 表示不冷却
  cooldownSeconds: 30
  # 全服同时进行的 RTP 请求数量上限（1-10），满额时拒绝新请求
  maxConcurrentLoads: 10
  # RTP 最终失败或取消后的冷却秒数，0 表示不冷却
  failureCooldownSeconds: 30
  # 每个请求最多尝试的随机位置数量（1-100）
  maxAttempts: 10
  # 寻找安全位置的总超时时间（秒，1-120）
  timeoutSeconds: 15
  # 单次区块加载的超时时间（秒，1-30）
  loadTimeoutSeconds: 5
  # 传送后的普通伤害保护时长（tick，0-1200），0 表示关闭
  invulnerabilityTicks: 60
  # 禁用群系：支持完整 ID 和以 # 开头的标签（标签需加引号）
  biomeBlacklist: ['#minecraft:is_ocean', '#minecraft:is_river']
  # 危险方块：支持完整 ID 和标签；液体、树叶、基岩始终禁止
  floorBlacklist: [minecraft:lava, minecraft:water, minecraft:magma_block, minecraft:powder_snow, '#minecraft:leaves', minecraft:cactus, minecraft:fire, minecraft:soul_fire, minecraft:sweet_berry_bush, minecraft:cobweb, minecraft:campfire, minecraft:soul_campfire]
  # 按维度 ID 设置搜索模式、中心、范围和黑名单
  dimensions: {}
```

### 随机传送

执行 `/rtp` 在当前维度寻找安全位置；也可使用 `/rtp minecraft:the_nether`、`/rtp minecraft:the_end` 或指定已加载的自定义维度 ID。搜索期间底部持续显示提示，找到安全位置后直接传送；没有固定倒计时，超时或尝试次数用尽会提示失败。

默认以目标维度出生点为中心，在 minRange 到 maxRange 之间随机搜索。开放维度只传地表，有顶维度寻找内部安全地面；排除液体、树叶和危险方块。雪层按实际碰撞判断能否站立。地形中没有安全落点时会失败。

冷却从成功或最终失败后开始。cooldownEnabled=false 关闭两类冷却；cooldownSeconds=0 只关闭成功冷却，failureCooldownSeconds=0 只关闭失败冷却。再次使用时会提示剩余秒数。并发满额的请求直接拒绝，不计冷却。传送后的保护不拦截绕过无敌的伤害，如虚空和 `/kill`。

如需为某个维度单独设置规则，在 rtp.dimensions 下填写完整维度 ID。mode 支持 auto（自动判断）、surface（地表）和 interior（内部地面）；未填写的范围及黑名单继承全局设置，中心默认使用该维度出生点。例如：

```yaml
  dimensions:
    'custom:moon':
      mode: surface
      centerX: 0
      centerZ: 0
      minRange: 100
      maxRange: 800
      biomeBlacklist: []
```

修改配置后执行 `/tpareload`。进行中的搜索会取消；配置补全时会写入内置注释。提高并发会增加服务器负载，新区块生成耗时取决于服务器性能和地形。

### Home GUI 自定义图标

在 `/homes` GUI 中：

- 左键 Home：传送
- 点击底部指南针，再左键选择 Home：设置默认 Home，不传送、不改变名称和图标颜色；再次点击指南针取消选择。中键在客户端允许时仍可使用，原版生存模式请使用指南针。
- 右键 Home：进入删除确认页面，核对名称和坐标后左键确认；取消或关闭页面不会删除。
- `Shift + 左键` Home：打开全部原版物品图标选择器
- `Shift + 右键` Home：快速恢复默认床图标

设置默认 Home 后，直接执行 `/home`（不填写名称）即可传送到该位置。设置默认 Home 不改变名称颜色或图标颜色。原有
`/defaulthome <名称>` 命令仍可使用。

图标选择器每页显示 45 个原版物品，并提供上一页、下一页、返回和恢复默认按钮。
选择器只显示 `minecraft` 命名空间下的物品，不显示其他 Mod 的物品。图标只保存
物品注册 ID，并保存到 SQLite；旧数据会继续使用默认床图标。

### Warp GUI 自定义图标与管理员权限

所有玩家都可以通过 `/warps` 打开 GUI，并左键 Warp 进行传送。以下操作需要管理员权限：

- 右键 Warp：删除公共传送点
- `Shift + 左键` Warp：打开全部原版物品图标选择器
- `Shift + 右键` Warp：快速恢复默认末影之眼图标

`/setwarp`、`/delwarp`、`/renamewarp`、`/tpastorage` 和 `/tpareload`
均需要管理员权限。`/tpals` 只会向管理员显示这些管理员命令，普通玩家不会
看到无法执行的命令。

`/tpareload` 会从磁盘重新读取 `config.yml` 和 `storage.db`，同步内置中英文
语言文件并清空语言缓存。为避免旧回调在重载后执行，进行中的延迟传送和 TPA
请求会被取消；玩家当前的 `/back` 死亡位置不会被清除。

## SQLite 存储与旧数据导入

玩家、Home 和 Warp 存储在 SQLite 中。升级旧数据库时，首次启动会自动迁移，无需执行命令；迁移前生成 storage.db.pre-v2-*.bak 备份，失败时回滚并保留旧库。

若不存在 storage.db 而存在 storage.json，首次启动会自动导入，源 JSON 保留；若已有数据库，则优先使用数据库，手动导入命令用于明确覆盖。回退旧版模组时，应停服并恢复迁移前备份，旧模组不能读取新表结构。


运行时数据库位于 `config/tpa/storage.db`。

如果需要用旧 JSON 替换现有数据库中的数据，请将 `storage.json` 放在
`config/tpa/` 中，由 OP 手动执行以下命令。普通 SQLite 升级无需执行：

```text
/tpastorage json-to-sqlite
```

导入成功后会自动执行与 `/tpareload` 相同的完整重载，新数据会立即载入，
无需重启。命令不会删除源 JSON 文件；
已有 `storage.db` 会先保存为带时间戳的备份。导入数据会先写入临时数据库并
回读校验，校验成功后才通过事务更新正式数据库。确认数据无误后可自行归档或删除
JSON 文件。为防止误覆盖，当 JSON 文件不存在或为空时，导入命令会拒绝执行。

## 语言文件

语言文件位于 `config/tpa/lang/`。模组每次启动时会同步内置的 `zh_cn.json`
和 `en_us.json`：

- 外部文件缺少的翻译键会使用新版内置翻译自动补充
- 新版内置文件已经移除的键会从外部文件同步移除
- 内外都存在的键会保留服务器已有值，不覆盖自定义翻译
- 其他自建语言文件不会被修改
- 如果内置语言文件损坏，会先生成 `.bak` 备份，再恢复并同步

因此更新模组后，新功能所需的语言键会自动加入，不再需要手动删除旧语言文件。
也可以新建其他语言文件，并在配置中指定语言名称。

<br>

## 数据存储

- 配置文件：`config/tpa/config.yml`
- 语言文件：`config/tpa/lang/`
- SQLite 玩家数据：`config/tpa/storage.db`
- 旧版 JSON 导入源（可选）：`config/tpa/storage.json`

<br>

## 如何构建

使用仓库自带的原版 Gradle Wrapper，无需安装 Gradle，也不依赖 PowerShell：

```bat
:: Windows
gradlew.bat buildAllVersions
```

```sh
# Linux / macOS
./gradlew buildAllVersions
```

该跨平台 Gradle 任务会构建全部六个版本，产物统一汇总到项目根目录的
`dist/`。

如有问题欢迎提交 [Issue](https://github.com/Tinmoli/tpa/issues)

<br>

## 鸣谢

- [TeleportCommands](https://github.com/MrSn0wy/TeleportCommands) — 本项目的灵感来源与参考实现
- [Dalict](https://github.com/Dalict) — 感谢贡献与支持

## 开发结构

所有版本共享 `fabric/src/main/java` 和 `fabric/src/main/resources`。`versions/` 保留各 Minecraft 版本的构建配置，构建时需要完整项目。修改业务代码只需修改根目录，`buildAllVersions` 会编译并打包六个版本。构建使用 JDK 25；1.21.11 产物以 Java 21 为目标。
