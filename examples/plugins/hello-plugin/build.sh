#!/usr/bin/env bash
# 构建示例插件并把 plugin.json 放入 JAR 根目录，输出可直接安装的 plugin.jar。
set -euo pipefail
cd "$(dirname "$0")"

ROOT="$(cd ../../.. && pwd)"
if [ ! -f "$ROOT/pom.xml" ]; then
    echo "未找到仓库根目录 pom.xml" >&2
    exit 1
fi

# 先把接口模块安装到本地仓库，插件才能以 provided 依赖编译。
(cd "$ROOT" && mvn -q -DskipTests -pl mint-soda-plugin-api -am install)

mvn -q -DskipTests package
(cd target && jar uf plugin.jar ../plugin.json)

echo "已生成 $(pwd)/target/plugin.jar"
