# Minecraft 26.1 独立开发说明

当前目录是完整、独立的 Git 仓库。源码与资源在 `fabric/src/main/`，构建配置在 `fabric/build.gradle`，Gradle Wrapper 在 `gradle/wrapper/`。不需要父目录或其他版本。首次构建需要联网下载依赖；需要 JDK 25。

```powershell
.\gradlew.bat clean build check
git add .
git commit -m "说明修改内容"
git push
```

Linux/macOS：`bash ./gradlew clean build check`。发行文件位于 `fabric/build/libs/`。

分支是 `26.1`，远程为 `https://github.com/Tinmoli/tpa.git`。作者为 Tinmoli <tinmoli@163.com>。
本目录的 `git push` 仅更新远程 `26.1`。

Git 本地配置不会随克隆传递。重新克隆时使用 `git clone --branch 26.1 https://github.com/Tinmoli/tpa.git 26.1`，进入目录后配置：

```sh
git config user.name Tinmoli
git config user.email tinmoli@163.com
git config --replace-all remote.origin.push refs/heads/26.1:refs/heads/26.1
```

构建产物、缓存、日志及运行时数据由 `.gitignore` 排除，不应提交。历史变更日志保留当时的结构说明；当前路径和命令以 README 及本文为准。

## 验证

运行 `clean build check` 会编译、打包并执行现有 Gradle 检查。原仓库在提交 `57f85ab` 删除了测试源码，当前没有自动化测试用例；Gradle 的 `test NO-SOURCE` 不代表功能测试通过。以后新增测试应放在本版本的 `fabric/src/test/`。服务端回归检查见 [tests/README.md](tests/README.md)。
