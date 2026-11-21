#!/usr/bin/env bash
# 校验 PR 标题符合本仓库的 Conventional Commits 约定，且摘要包含中文。
#
# 用法：PR_TITLE='feat(ui): 重建设置页' bash .github/scripts/check-pr-title.sh
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
title="${PR_TITLE:-}"

bash "$script_dir/check-subject.sh" "$title"
printf 'PR 标题校验通过：%s\n' "$title"
