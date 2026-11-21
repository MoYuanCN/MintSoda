#!/usr/bin/env bash
# 校验单条提交标题是否符合本仓库的 Conventional Commits 约定。
#
# 用法：bash .github/scripts/check-subject.sh 'feat(ui): 重建设置页为分组导航'
set -euo pipefail

subject="${1:-}"
types='feat|fix|refactor|perf|build|ci|docs|test|chore|style|revert'
pattern="^(${types})(\(([a-z0-9][a-z0-9-]*)\))?!?: [^[:space:]].*$"

if [ -z "$subject" ]; then
    printf '提交标题为空。\n' >&2
    exit 1
fi

if ! printf '%s' "$subject" | grep -Eq "$pattern"; then
    printf '不符合 Conventional Commits：%s\n' "$subject" >&2
    printf '格式：<type>(<scope>): <中文摘要>，type 与 scope 用小写英文。\n' >&2
    printf '示例：feat(ui): 重建设置页为分组导航\n' >&2
    exit 1
fi

if ! printf '%s' "$subject" | LC_ALL=C grep -q '[^ -~]'; then
    printf '摘要需要使用中文：%s\n' "$subject" >&2
    exit 1
fi

case "$subject" in
    *。)
        printf '摘要句末不要加句号：%s\n' "$subject" >&2
        exit 1
        ;;
esac

length=$(printf '%s' "$subject" | wc -m | tr -d ' ')
if [ "$length" -gt 80 ]; then
    printf '摘要长度 %s 超过 80 个字符，请精简。\n' "$length" >&2
    exit 1
fi

exit 0
