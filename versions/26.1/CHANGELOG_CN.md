# 更新日志
所有值得记录的项目变更都将记录在此文件中。

---

## [1.0.4] - 2026-07-24

### 新增
- 新增分页图标选择器，可从全部原版物品中自由选择 Home 或 Warp 图标
- Home GUI 支持鼠标中键直接设置默认 Home，之后可通过无参数 `/home` 传送
- Home 与 Warp GUI 的操作提示拆分为两排显示，避免单行文字过长
- Home GUI 使用 `Shift + 左键` 打开选择器，`Shift + 右键` 快速恢复默认图标
- Home 图标数据保存到 SQLite，并兼容没有图标字段的旧数据
- 新增 Warp GUI 自定义图标，打开图标选择器或重置 Warp 图标需要管理员权限
- SQLite 设为唯一运行时存储，数据固定保存在 `config/tpa/storage.db`
- 新增需要管理员权限的 `/tpastorage json-to-sqlite` 命令，将旧版 `storage.json` 导入 `storage.db`
- 新增需要管理员权限的 `/tpareload`，无需重启即可重载配置、SQLite 数据和语言文件
- 新增 SQLite JDBC 依赖，并将驱动打包到模组 JAR 中
- 新增 Minecraft 1.21.11、26.1、26.1.1、26.1.2、26.2 五个完全独立的版本项目

### 修复
- 修复配置中的 `back/home/tpa/warp/spawn/rtp.enabled` 开关不生效的问题
- 修复延迟传送被替换或取消后，已排队的旧任务仍可能执行传送的问题
- 修复传送粒子和倒计时 Timer 异步访问玩家/世界状态及阻止 JVM 退出的问题
- 修复 TPA 请求提醒被错误显示为“已过期”，以及请求未在 120 秒时精确移除的问题
- 修复控制台执行玩家专用的 `/tpaaccept`、`/tpadeny` 时可能出现上下文异常的问题
- 修复旧版或部分损坏存储中 null 字段可能导致启动失败的问题
- 修复模组更新后外部语言文件缺少新翻译键，导致聊天和 GUI 显示原始键名的问题
- 统一管理员命令权限：普通玩家的 `/tpals` 不再显示 Warp 管理与存储转换命令
- 修复 `/tpals` 未向 OP 显示 JSON → SQLite 导入命令的问题
- 修复 TPA 请求接受或拒绝后仍可能发送过期提醒的竞态问题
- 修复 `home.playerMaximum` 配置不生效的问题
- 修复玩家达到 Home 数量上限后仍可继续创建 Home 的问题
- 修复达到数量上限时，重复设置同名 Home 被错误提示为数量超限的问题

### 改进
- 内置中英文语言文件改为启动时增量同步：补充新键、移除废弃键并保留已有自定义值
- 语言文件损坏时自动备份并使用内置内容恢复
- 配置文件现在显式使用 UTF-8 编码写入
- 移除 `storage.backend` 配置、JSON 运行时后端和 SQLite → JSON 反向转换命令
- JSON 导入先写入临时数据库并回读校验，替换前自动备份已有数据库，成功后自动执行完整重载
- JSON 导入后立即加载新数据；源文件缺失或为空时拒绝导入，避免误覆盖
- 为负数延迟、非法 RTP 范围及超出请求生命周期的提醒时间增加自动校正
- GUI 自定义图标隐藏镐、武器等物品自带的攻击伤害、攻击速度和工具属性提示
- 对负数 `home.playerMaximum` 进行校验，并自动修正为 `0`
- 适配 Minecraft 26.2 将彩色床和染色玻璃板常量整合为 `ColorCollection` 的 API 变化
- 使用原版 Gradle Wrapper 的跨平台 `buildAllVersions` 任务替代 PowerShell 脚本，Windows 与 Linux 均可构建，五个版本的 JAR 统一输出到 `dist/`
- 更新中英文 README，补充固定 SQLite 存储和旧 JSON 数据导入说明

---

## [1.0.3] - 2026-03-31

### 重大变更
- **升级至 Minecraft 26.1** - 全面支持 Fabric 26.1（Java 25）
- **移除多平台支持** - 删除 NeoForge 和 Quilt 加载器支持，转为纯 Fabric 项目

> **注意**：从 v1.0.3 起，项目已转为纯 Fabric 模组，不再支持 NeoForge 和 Quilt。

### 技术更新
- **构建链升级**：
  - Fabric Loom 升级至 1.15-SNAPSHOT
  - Java 版本升级至 25
  - 移除 Mappings 依赖（使用 Mojang 官方命名）

- **API 适配**：
  - 修复 `ServerPlayer.displayClientMessage()` 移除导致的编译错误
  - 新增 `tools.sendPlayerMessage()` 统一消息发送方法（Action Bar 使用 `ClientboundSetActionBarTextPacket`，聊天使用 `sendSystemMessage`）
  - 适配 sgui 2.0+ 新 API（`setCallback` 签名变更）
  - 移除 `GuiElementBuilder.setSkullOwner()` 调用（API 已移除）

### 修复
- 修复 GUI 回调方法歧义错误
- 修复所有命令类中已弃用的消息发送 API 调用

---

## [1.0.2] - 2026-03-23

### 新增
- 配置项 `tpa.requestExpireReminder`：TPA 请求过期前的提醒时间（秒），设为 `0` 可禁用提醒（原固定 30 秒）
- `/spawn` 命令现在读取配置中的 `spawn.world_id`，支持自定义出生点所在维度（此前硬编码为主世界）

### 修复
- 修复 `StorageManager.cleanup()` 在 for-each 循环中直接删除元素导致的 `ConcurrentModificationException`
- 修复 `StorageLoader` 和 `StorageMigrator` 中 `FileReader` 未关闭导致的资源泄漏
- 修复 `StorageLoader` 在存储文件不存在时初始化后未提前返回，导致继续执行迁移逻辑的问题
- 修复 `tpa.java` 中 `exportBuiltinLangFiles()` 重复调用 `listFiles()` 存在的竞态隐患
- 修复 TPA 请求接受/拒绝后，过期提醒 Timer 仍会触发并发送无效消息的问题
- 修复 TPA 接受/拒绝按钮的点击命令未对玩家名加引号，导致名字含空格时命令解析失败的问题
- 修复 `DeathLocationStorage` 使用非线程安全 `HashMap`，改为 `ConcurrentHashMap`
- 修复 `/rtp` 命令仅尝试一次随机位置，在海洋、空岛等地形下几乎必然失败的问题，改为最多重试 10 次
- 删除 `warp.java` 中遗留的调试用 `System.out.println` 输出

### 改进
- `tools.getTranslatedText()` 添加语言文件内存缓存，避免每次发送消息都读取磁盘，提升性能
- `TeleportDelayManager` 和 `tools` 中的 `Random` 改为 `ThreadLocalRandom`，消除多线程竞争
- 移除未使用的 `ModCommand` 枚举
- 移除已被 GUI 替代的 `PrintHomes()` 和 `PrintWarps()` 死代码方法

---

## [1.0.1] - 2026-03-21

### 新增
- `/rtp` 指令：随机传送到世界各处，支持指定维度
- 配置项 `rtp.enabled`：启用/禁用随机传送功能
- 配置项 `rtp.minRange` 和 `rtp.maxRange`：设置随机传送范围（方块）
- 配置文件自动升级：新版本启动时自动检测并补全缺失的配置项
- 配置文件每项配置自动写入中文注释，方便直接阅读和编辑
- 添加 Quilt 支持（基于 Fabric 兼容层构建）
- 创建 NeoForge 子项目

### 修复
- 修复 `/tpaaccept` 和 `/tpadeny` 命令权限检查导致需要确认执行的问题
- 修复 `/back` 和 `/spawn` 命令的强制传送按钮权限检查问题
- 修复 snakeyaml 依赖在服务器重启后丢失的问题（使用 Gradle `include` 正确打包依赖）
- 修复 tpa 传送倒计时期间移动检测不生效的问题（改为主线程读取玩家位置）

### 改进
- 配置系统支持增量更新，新增配置项不会覆盖用户已修改的内容
- 所有聊天框按钮统一使用链式写法
- `settings.gradle` 新增 NeoForge / Quilt Maven 仓库，并通过 `exclusiveContent` 精确过滤依赖来源

---

## [1.0.0] - 2026-03-16

### 新增
- `/tpals` 指令：显示所有可用指令及其说明
- `/tpa` 延迟传送系统：接受请求后倒计时（默认 3 秒），期间显示 actionbar 倒计时与附魔台粒子效果
- 配置项 `tpa.delay`：设置传送前等待秒数，0 为立即传送
- 配置项 `tpa.cancelOnMove`：移动时自动取消待执行的传送
- 支持语言：`zh_cn`（简体中文）、`en_us`（English）

### 修复
- 无
