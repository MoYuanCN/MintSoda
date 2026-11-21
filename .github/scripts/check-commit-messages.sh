#!/usr/bin/env bash
# 校验一组提交信息，逐条调用 check-subject.sh。
#
# 用法：
#   bash .github/scripts/check-commit-messages.sh                  # 检查最近一条提交
#   COMMIT_RANGE=origin/main..HEAD bash .github/scripts/check-commit-messages.sh
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

rev_list() {
    local base="${1:-}"

    if [ -n "$base" ] && git cat-file -e "${base}^{commit}" 2>/dev/null \
        && git merge-base --is-ancestor "$base" HEAD 2>/dev/null; then
        git rev-list --no-merges "${base}..HEAD"
        return
    fi

    # 没有可用基线时只取最新一条，避免把历史提交一并算进来。
    git rev-list --no-merges -n 1 HEAD
}

resolve_base() {
    if [ -n "${PR_BASE_SHA:-}" ]; then
        printf '%s' "$PR_BASE_SHA"
        return
    fi
    if [ -n "${PUSH_BEFORE:-}" ] \
        && [ "$PUSH_BEFORE" != "0000000000000000000000000000000000000000" ]; then
        printf '%s' "$PUSH_BEFORE"
    fi
}

failed=0
total=0

if [ -n "${COMMIT_RANGE:-}" ]; then
    commits=$(git rev-list --no-merges "$COMMIT_RANGE")
else
    commits=$(rev_list "$(resolve_base)")
fi

while IFS= read -r sha; do
    [ -n "$sha" ] || continue
    subject=$(git log -1 --format=%s "$sha")
    case "$subject" in
        "Merge "*|"Revert "*|"fixup! "*|"squash! "*|"amend! "*) continue ;;
    esac
    total=$((total + 1))
    if ! bash "$script_dir/check-subject.sh" "$subject"; then
        printf '  ^ %s\n' "${sha:0:8}" >&2
        failed=$((failed + 1))
    fi
done <<< "$commits"

if [ "$failed" -gt 0 ]; then
    printf '\n共检查 %d 条提交，%d 条不符合规范。\n' "$total" "$failed" >&2
    exit 1
fi
printf '提交信息校验通过（%d 条）。\n' "$total"
