# 传送MOD<img alt="tpa Logo" src="common\src\main\resources\tpa.png" width="30"/>

一个 Minecraft 服务端模组，添加了各种与传送相关的指令，包括 /home、/tpa、/back 等

项目地址：[https://github.com/Tinmoli/tpa](https://github.com/Tinmoli/tpa)

这里是[更新日志](https://github.com/Tinmoli/tpa/blob/main/CHANGELOG.md)

## 目前可用的指令

- `/tpals` - 获取可用命令
- `/spawn [<禁用安全检查>]` - 传送到主世界出生点，传入 `true` 跳过安全检查
- `/back [<禁用安全检查>]` - 传送到上次死亡地点，传入 `true` 跳过安全检查
- `/sethome <名称>` - 设置一个家
- `/home [<名称>]` - 回家，不填名称则前往默认家
- `/delhome <名称>` - 删除一个家
- `/renamehome <名称> <新名称>` - 重命名一个家
- `/defaulthome <名称>` - 设置默认家
- `/homes` - 查看所有家
- `/warp <名称>` - 传送到地标
- `/warps` - 查看所有地标
- `/setwarp <名称>` - 设置地标（需要管理员权限）
- `/delwarp <名称>` - 删除地标（需要管理员权限）
- `/renamewarp <名称> <新名称>` - 重命名地标（需要管理员权限）
- `/tpa <玩家>` - 向玩家发送传送请求
- `/tpahere <玩家>` - 请求将玩家传送到你这里
- `/tpaaccept <玩家>` - 接受传送请求
- `/tpadeny <玩家>` - 拒绝传送请求

<br>

## 配置文件

配置文件位于 `config/tpa/config.yml`，支持以下配置：

- **language**：界面语言（默认 `zh_cn`，支持 `en_us`）
- **back / home / tpa / warp / spawn**：各指令的启用状态等选项
- **tpa.delay**：接受传送请求后的等待秒数（默认 3 秒，设为 0 立即传送）
- **tpa.cancelOnMove**：等待期间移动则取消传送（默认开启）

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

- [TeleportCommands](https://github.com/MrSn0wy/TeleportCommands) — 借鉴于该项目的部分代码
- [Dalict](https://github.com/Dalict) — 感谢贡献与支持
