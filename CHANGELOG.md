# 更新日志
所有值得记录的项目变更都将记录在此文件中。

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
