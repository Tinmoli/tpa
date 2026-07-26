# tpa <img alt="tpa Logo" src="https://github.com/Tinmoli/Tpa/fabric/src/main/resources/tpa.png" width="30"/>

> **[English Documentation](https://github.com/Tinmoli/tpa/blob/main/README.md)** | 点击这里查看英文文档

一个 Minecraft 服务端模组，添加了各种与传送相关的指令，包括 /home、/tpa、/back、/rtp 等

当前版本：**1.0.5**

项目地址：[https://github.com/Tinmoli/tpa](https://github.com/Tinmoli/tpa)

这里是[更新日志](https://github.com/Tinmoli/tpa/blob/main/CHANGELOG_CN.md)

## 支持的平台

| 平台 | 支持版本 |
|------|----------|
| Fabric | 1.21.11、26.1、26.1.1、26.1.2、26.2 |

> **注意**：从 v1.0.3 起，项目已转为纯 Fabric 模组，不再支持 NeoForge 和 Quilt。

## 依赖

- Fabric Loader（使用各版本 JAR 所声明的最低版本）
- Minecraft 1.21.11、26.1、26.1.1、26.1.2 或 26.2
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
```

### Home GUI 自定义图标

在 `/homes` GUI 中：

- 左键 Home：传送
- 鼠标中键 Home：将其设置为默认 Home
- 右键 Home：删除
- `Shift + 左键` Home：打开全部原版物品图标选择器
- `Shift + 右键` Home：快速恢复默认床图标

设置默认 Home 后，直接执行 `/home`（不填写名称）即可传送到该位置。默认 Home
在 GUI 中以金色名称显示；没有自定义图标时使用黄色床图标。原有
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

模组现在固定使用 `config/tpa/storage.db` 作为运行时存储，不再提供
JSON 运行时后端，也不再需要 `storage.backend` 配置。

从旧版本升级时，请先备份数据，然后将旧 `storage.json` 放在
`config/tpa/` 中，由 OP 执行：

```text
/tpastorage json-to-sqlite
```

导入成功后会自动执行与 `/tpareload` 相同的完整重载，新数据会立即载入，
无需重启。命令不会删除源 JSON 文件；
已有 `storage.db` 会先保存为带时间戳的备份。导入数据会先写入临时数据库并
回读校验，校验成功后才替换正式数据库。确认数据无误后可自行归档或删除
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

该跨平台 Gradle 任务会构建全部五个独立版本，产物统一汇总到项目根目录的
`dist/`。

如有问题欢迎提交 [Issue](https://github.com/Tinmoli/tpa/issues)

<br>

## 鸣谢

- [TeleportCommands](https://github.com/MrSn0wy/TeleportCommands) — 本项目的灵感来源与参考实现
- [Dalict](https://github.com/Dalict) — 感谢贡献与支持
