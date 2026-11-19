#!/usr/bin/env bash
# 把脚本插件打包成 ZIP：plugin.json 与入口脚本必须位于压缩包根目录。
set -euo pipefail
cd "$(dirname "$0")"

rm -f plugin.zip
zip -q plugin.zip plugin.json main.js
echo "已生成 $(pwd)/plugin.zip"
