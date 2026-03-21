# tpa <img alt="tpa Logo" src="https://github.com/Tinmoli/tpa/blob/main/common/src/main/resources/tpa.png" width="30"/>
一个 Minecraft 服务端模组，添加了各种与传送相关的指令，包括 /home、/tpa、/back、/rtp 等

项目地址：[https://github.com/Tinmoli/tpa](https://github.com/Tinmoli/tpa)

这里是[更新日志](https://github.com/Tinmoli/tpa/blob/main/CHANGELOG.md)

## 支持的平台

| 平台 | 状态 |
|------|------|
| Fabric | 支持 |
| Quilt | 支持 |
| NeoForge | 开发中 |

## 目前可用的指令

- `/tpals` - 获取可用命令
- `/spawn [<禁用安全检查>]` - 传送到主世界出生点，传入 `true` 跳过安全检查
- `/back [<禁用安全检查>]` - 传送到上次死亡地点，传入 `true` 跳过安全检查
- `/sethome <名称>` - 设置一个传送点
- `/home [<名称>]` - 回到传送点，不填名称则前往默认传送点
- `/delhome <名称>` - 删除一个家
- `/renamehome <名称> <新名称>` - 重命名一个传送点
- `/defaulthome <名称>` - 设置默认传送点
- `/homes` - 查看所有传送点
- `/warp <名称>` - 传送到地标
- `/warps` - 查看所有公共传送点
- `/setwarp <名称>` - 设置公共传送点（需要管理员权限）
- `/delwarp <名称>` - 删除公共传送点（需要管理员权限）
- `/renamewarp <名称> <新名称>` - 重命名公共传送点（需要管理员权限）
- `/tpa <玩家>` - 向玩家发送传送请求
- `/tpahere <玩家>` - 请求将玩家传送到你这里
- `/tpaaccept <玩家>` - 接受传送请求
- `/tpadeny <玩家>` - 拒绝传送请求
- `/rtp [<维度>]` - 随机传送到世界各处，可指定维度

<br>

## 配置文件

配置文件位于 `config/tpa/config.yml`，每项配置均附有中文注释，支持以下配置：

```yaml
# TPA 插件配置文件
# 修改后需重启服务器生效

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

## 语言文件

语言文件位于 `config/tpa/lang/`，首次启动自动生成 `zh_cn.json` 和 `en_us.json`。
可直接编辑这些文件自定义翻译，也可新建文件并在配置中指定语言名称。

<br>

## 数据存储

- 配置文件：`config/tpa/config.yml`
- 语言文件：`config/tpa/lang/`
- 玩家数据：`config/tpa/storage.json`

<br>

## 如何构建

```bash
# Linux
./gradlew build

# Windows
.\gradlew.bat build
```

如有问题欢迎提交 [Issue](https://github.com/Tinmoli/tpa/issues)

<br>

## 鸣谢

- [TeleportCommands](https://github.com/MrSn0wy/TeleportCommands) — 本项目的灵感来源与参考实现
- [Dalict](https://github.com/Dalict) — 感谢贡献与支持
