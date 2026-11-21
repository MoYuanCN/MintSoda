#!/usr/bin/env bash
# MintSoda 一键安装脚本
#
# 一行命令安装到当前目录（不创建子目录）并注册 systemd 服务：
#   curl -fsSL https://raw.githubusercontent.com/MoYuanCN/MintSoda/main/linux/install.sh | sudo bash
#
# 安装过程中会在终端询问登录用户名与密码；也可直接传入参数跳过询问：
#   sudo bash install.sh --port 17789 --username myuser --password 'StrongPass!234' --yes
#
# 说明：
#   - 安装目录默认为执行命令时所在目录，可用 --dir 指定。
#   - 未传 --username 时会交互询问；用户名留空则由程序自动生成随机凭据。
#   - 用户名 3-32 位字母、数字、下划线或连字符；密码至少 8 位，且包含大写字母、
#     小写字母、数字、符号中的至少两类，需输入两次确认。
#   - 用户名与密码写入 /etc/mint-soda.env（仅 root 可读），由服务启动时读取；
#     程序启动时会再次校验强度并只保存摘要。
#   - 重新安装时若检测到已有配置，默认沿用原账号，选 n 可重新设置。
#   - --setup 会跳过账号询问，改为在 WebUI 中完成初始化引导。

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'

REPO_URL="https://github.com/MoYuanCN/MintSoda"
JAR_URL="$REPO_URL/releases/latest/download/MintSoda.jar"
MINTSODA_URL="https://raw.githubusercontent.com/MoYuanCN/MintSoda/main/linux/mintsoda"

ENV_FILE="/etc/mint-soda.env"
INSTALL_DIR="$(pwd)"
PORT="17789"
SERVICE_NAME="mint-soda.service"
SERVICE_USER="mint-soda"
USERNAME=""
PASSWORD=""
ASSUME_YES="no"
FORCE_SETUP="no"

log() { echo -e "${CYAN}$*${NC}"; }
ok() { echo -e "${GREEN}$*${NC}"; }
warn() { echo -e "${YELLOW}$*${NC}"; }
die() { echo -e "${RED}$*${NC}" >&2; exit 1; }

require_root() {
    [ "$EUID" -eq 0 ] || die "请使用 root 运行：sudo bash install.sh"
}

# 从终端读取输入。curl | bash 时标准输入是脚本本身，必须显式读取 /dev/tty。
read_tty() {
    local prompt="$1" silent="${2:-no}" value=""
    if [ "$silent" = "yes" ]; then
        IFS= read -r -s -p "$prompt" value </dev/tty || value=""
        printf '\n' >/dev/tty
    else
        IFS= read -r -p "$prompt" value </dev/tty || value=""
    fi
    printf '%s' "$value"
}

trim() {
    local value="$1"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "$value"
}

valid_username() {
    [[ "$1" =~ ^[A-Za-z0-9_-]{3,32}$ ]]
}

# 与后端 CredentialUtils 保持一致：至少 8 位，四类字符中至少两类。
# 成功时输出空字符串，失败时输出原因，始终返回 0，避免 set -e 提前退出。
password_error() {
    local password="$1" types=0
    if [ "${#password}" -lt 8 ]; then
        printf '%s' "密码至少需要 8 位"
        return 0
    fi
    if [[ "$password" =~ [a-z] ]]; then types=$((types + 1)); fi
    if [[ "$password" =~ [A-Z] ]]; then types=$((types + 1)); fi
    if [[ "$password" =~ [0-9] ]]; then types=$((types + 1)); fi
    if [[ "$password" =~ [^A-Za-z0-9] ]]; then types=$((types + 1)); fi
    if [ "$types" -lt 2 ]; then
        printf '%s' "密码需包含大写字母、小写字母、数字、符号中的至少两类"
        return 0
    fi
    printf '%s' ""
}

has_tty() {
    [ -r /dev/tty ] && [ -w /dev/tty ]
}

# 只传了 --username 或只传了 --password 时补齐另一半，避免留下无法登录的账号。
prompt_missing_password() {
    [ -n "$USERNAME" ] || return 0
    [ -z "$PASSWORD" ] || return 0
    if [ "$FORCE_SETUP" = "yes" ]; then
        die "已指定 --setup 时不要再传入 --username"
    fi
    if ! has_tty; then
        die "已指定 --username，但缺少 --password；请在终端交互安装或同时传入 --password"
    fi
    warn "已指定用户名 $USERNAME，请补充设置密码"
    local password="" confirm_password="" error=""
    while :; do
        password="$(read_tty "登录密码（输入时不显示）: " yes)"
        if [ -z "$password" ]; then
            warn "密码不能为空，请重新输入"
            continue
        fi
        error="$(password_error "$password")"
        if [ -n "$error" ]; then
            warn "$error，请重新输入"
            continue
        fi
        confirm_password="$(read_tty "再次输入密码: " yes)"
        if [ "$password" != "$confirm_password" ]; then
            warn "两次输入的密码不一致，请重新输入"
            continue
        fi
        break
    done
    PASSWORD="$password"
    ok "已设置登录密码"
}

existing_username() {
    sed -n 's/^MINT_SODA_USERNAME=//p' "$ENV_FILE" | head -1 | tr -d '"[[:space:]]'
}

env_has_credentials() {
    [ -f "$ENV_FILE" ] && [ -n "$(existing_username)" ]
}

prompt_credentials() {
    # 已提供用户名时直接沿用，--setup 不得覆盖已有凭据设置。
    if [ -n "$USERNAME" ]; then
        if [ "$FORCE_SETUP" = "yes" ]; then
            warn "已指定账号，忽略 --setup；如需初始化请在清空账号后重新运行"
            FORCE_SETUP="no"
        fi
        prompt_missing_password
        return 0
    fi

    # 已有账号配置时 --setup 完全失效，不允许把已配置的账号重置为引导状态。
    if [ "$FORCE_SETUP" = "yes" ]; then
        if env_has_credentials; then
            warn "检测到已有账号配置，忽略 --setup"
            FORCE_SETUP="no"
        else
            warn "已指定 --setup，跳过账号设置，首次访问时在 WebUI 中完成初始化"
            return 0
        fi
    fi

    # 重新安装时默认沿用已有账号，避免误把密码换成随机凭据。
    if env_has_credentials; then
        if [ "$ASSUME_YES" = "yes" ] || ! has_tty; then
            warn "检测到已有账号配置 $ENV_FILE，将沿用现有账号"
            PASSWORD="**existing**"
            USERNAME="$(existing_username)"
            return 0
        fi
        if confirm "检测到已有账号配置，是否沿用现有用户名与密码？（选 n 可重新设置）"; then
            USERNAME="$(existing_username)"
            PASSWORD="**existing**"
            ok "将沿用已有账号: $USERNAME"
            return 0
        fi
    fi

    if [ "$ASSUME_YES" = "yes" ]; then
        warn "已指定 --yes，跳过账号询问；未提供凭据时由程序在首次启动生成或引导设置"
        return 0
    fi
    if ! has_tty; then
        warn "未检测到可交互终端，跳过账号设置；请通过 WebUI 或环境变量配置账号"
        return 0
    fi

    echo
    log "请设置 MintSoda 登录账号"
    echo "  直接回车跳过：由程序在首次启动时生成随机凭据并输出到日志。"
    echo

    local input="" error=""
    while :; do
        input="$(trim "$(read_tty "登录用户名: ")")"
        if [ -z "$input" ]; then
            USERNAME=""
            PASSWORD=""
            warn "未设置用户名，稍后将由程序自动生成或进入引导"
            return 0
        fi
        if valid_username "$input"; then
            USERNAME="$input"
            break
        fi
        warn "用户名需为 3-32 位字母、数字、下划线或连字符，请重新输入"
    done

    local password="" confirm_password=""
    while :; do
        password="$(read_tty "登录密码（输入时不显示）: " yes)"
        if [ -z "$password" ]; then
            warn "密码不能为空，请重新输入"
            continue
        fi
        error="$(password_error "$password")"
        if [ -n "$error" ]; then
            warn "$error，请重新输入"
            continue
        fi
        confirm_password="$(read_tty "再次输入密码: " yes)"
        if [ "$password" != "$confirm_password" ]; then
            warn "两次输入的密码不一致，请重新输入"
            continue
        fi
        break
    done

    PASSWORD="$password"
    ok "已设置登录账号: $USERNAME"
}

confirm() {
    [ "$ASSUME_YES" = "yes" ] && return 0
    if ! has_tty; then
        return 0
    fi
    local reply="n"
    read -r -p "$1 [y/N]: " reply </dev/tty || reply="n"
    [[ "$reply" =~ ^[Yy]$ ]]
}

parse_args() {
    while [ $# -gt 0 ]; do
        case "$1" in
            --dir) INSTALL_DIR="$2"; shift 2 ;;
            --port) PORT="$2"; shift 2 ;;
            --username) USERNAME="$2"; shift 2 ;;
            --password) PASSWORD="$2"; shift 2 ;;
            --setup) FORCE_SETUP="yes"; shift ;;
            --yes|-y) ASSUME_YES="yes"; shift ;;
            -h|--help)
                sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'
                exit 0 ;;
            *) die "未知参数: $1" ;;
        esac
    done
    [[ "$PORT" =~ ^[0-9]+$ ]] && [ "$PORT" -ge 1 ] && [ "$PORT" -le 65535 ] || die "端口无效: $PORT"
    if [ -n "$USERNAME" ] && ! valid_username "$USERNAME"; then
        die "用户名需为 3-32 位字母、数字、下划线或连字符: $USERNAME"
    fi
    if [ -n "$PASSWORD" ] && [ -z "$USERNAME" ]; then
        die "指定 --password 时必须同时指定 --username"
    fi
    if [ -n "$USERNAME" ] && [ -n "$PASSWORD" ]; then
        local error
        error="$(password_error "$PASSWORD")"
        [ -z "$error" ] || die "$error"
    fi
    mkdir -p "$INSTALL_DIR"
    INSTALL_DIR="$(cd "$INSTALL_DIR" && pwd)"
}

install_java() {
    if command -v java >/dev/null 2>&1; then
        local major
        major="$(java -version 2>&1 | sed -nE '1s/.*version "([0-9]+).*/\1/p')"
        if [[ "$major" =~ ^[0-9]+$ ]] && [ "$major" -ge 25 ]; then
            ok "已检测到 Java $major"
            return
        fi
        warn "检测到 Java $major，低于 25，尝试安装 Java 25"
    fi
    if command -v apt >/dev/null 2>&1; then
        apt update -qq && apt install -y openjdk-25-jdk
    elif command -v dnf >/dev/null 2>&1; then
        dnf install -y java-25-openjdk-devel
    elif command -v yum >/dev/null 2>&1; then
        yum install -y java-25-openjdk-devel
    else
        die "未找到受支持的包管理器，请手动安装 Java 25"
    fi
    command -v java >/dev/null 2>&1 || die "Java 安装失败"
}

create_user() {
    if id "$SERVICE_USER" >/dev/null 2>&1; then
        ok "系统用户 $SERVICE_USER 已存在"
    else
        useradd -r -s /usr/sbin/nologin "$SERVICE_USER" 2>/dev/null \
            || useradd -r -s /bin/false "$SERVICE_USER"
        ok "已创建系统用户 $SERVICE_USER"
    fi
}

download_jar() {
    local target="$INSTALL_DIR/MintSoda.jar"
    if [ -f "$target" ] && [ "$ASSUME_YES" != "yes" ]; then
        confirm "当前目录已存在 MintSoda.jar，是否覆盖？" || die "已取消"
    fi
    log "正在下载 MintSoda.jar 到 $target"
    curl -fL "$JAR_URL" -o "$target.tmp" || die "下载失败"
    mv "$target.tmp" "$target"
    ok "已下载 MintSoda.jar"
}

install_mintsoda() {
    log "正在安装快捷指令 mintsoda"
    local tmp
    tmp="$(mktemp)"
    local local_copy="/usr/local/share/mint-soda/mintsoda"
    if curl -fsSL "$MINTSODA_URL" -o "$tmp"; then
        mkdir -p "$(dirname "$local_copy")"
        install -m 755 "$tmp" "$local_copy"
    elif [ -f "$INSTALL_DIR/linux/mintsoda" ]; then
        mkdir -p "$(dirname "$local_copy")"
        install -m 755 "$INSTALL_DIR/linux/mintsoda" "$local_copy"
    else
        rm -f "$tmp"
        warn "未安装快捷指令，可稍后手动复制 linux/mintsoda 到 /usr/local/bin"
        return
    fi
    rm -f "$tmp"
    # 用包装脚本记录安装目录，避免改写菜单脚本本身，重新安装也不会重复替换。
    cat > /usr/local/bin/mintsoda <<WRAPPER
#!/usr/bin/env bash
export MINT_SODA_DIR="$INSTALL_DIR"
exec bash "$local_copy" "\$@"
WRAPPER
    chmod 755 /usr/local/bin/mintsoda
    ok "已安装 /usr/local/bin/mintsoda"
}

# systemd EnvironmentFile 支持双引号，转义反斜杠与双引号以容纳特殊字符。
escape_env_value() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

write_env_credentials() {
    [ "$PASSWORD" = "**existing**" ] && return 0
    local env_file="$ENV_FILE"
    local content
    content="MINT_SODA_USERNAME=\"$(escape_env_value "$USERNAME")\"
MINT_SODA_PASSWORD=\"$(escape_env_value "$PASSWORD")\""
    (umask 077 && printf '%s\n' "$content" > "$env_file")
    ok "账号密码已写入 $env_file（仅 root 可读）"
}

setup_service() {
    local setup_env=""
    if [ "$FORCE_SETUP" = "yes" ]; then
        setup_env=" --setup"
    fi
    log "正在注册 systemd 服务"
    cat > "/etc/systemd/system/$SERVICE_NAME" <<UNIT
[Unit]
Description=MintSoda
After=network.target

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_USER
WorkingDirectory=$INSTALL_DIR
ExecStart=/usr/bin/java -Xms64m -Xmx512m -Xss256k -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+IgnoreUnrecognizedVMOptions -Dfile.encoding=UTF-8 -jar $INSTALL_DIR/MintSoda.jar$setup_env
Restart=on-failure
RestartSec=15
LimitNOFILE=65535
Environment="TZ=Asia/Shanghai"
Environment="SERVER_ADDRESS=0.0.0.0"
Environment="SERVER_PORT=$PORT"
Environment="CONFIG=$INSTALL_DIR/config"
Environment="SWAGGER_ENABLED=false"
Environment="MCP_ENABLED=false"
EnvironmentFile=-$ENV_FILE

[Install]
WantedBy=multi-user.target
UNIT

    systemctl daemon-reload
    systemctl enable "$SERVICE_NAME" >/dev/null 2>&1 || true
    systemctl restart "$SERVICE_NAME"
    ok "服务已启动"
}

fix_permissions() {
    mkdir -p "$INSTALL_DIR/config" "$INSTALL_DIR/data"
    chown -R "$SERVICE_USER:$SERVICE_USER" "$INSTALL_DIR/config" "$INSTALL_DIR/data"
    chown "$SERVICE_USER:$SERVICE_USER" "$INSTALL_DIR/MintSoda.jar"
    chmod 750 "$INSTALL_DIR/config" "$INSTALL_DIR/data"
    chmod 640 "$INSTALL_DIR/MintSoda.jar"

    # 服务以独立用户运行，安装目录必须可遍历，例如 /root 下的目录会失败。
    local readable="no"
    if command -v runuser >/dev/null 2>&1; then
        runuser -u "$SERVICE_USER" -- test -r "$INSTALL_DIR/MintSoda.jar" 2>/dev/null && readable="yes"
    elif command -v su >/dev/null 2>&1; then
        su -s /bin/sh "$SERVICE_USER" -c "test -r '$INSTALL_DIR/MintSoda.jar'" 2>/dev/null && readable="yes"
    else
        readable="yes"
    fi
    [ "$readable" = "yes" ] || die "服务用户 $SERVICE_USER 无法读取 $INSTALL_DIR/MintSoda.jar。请改用 --dir /opt/mint-soda 等可访问目录后重试"
}

show_hint() {
    echo
    echo "以后可直接运行 mintsoda 打开管理菜单，或使用 bash $INSTALL_DIR/install.sh 重新安装。"
}

show_result() {
    local ip
    ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
    local setup_hint=""
    if [ "$PASSWORD" = "**existing**" ]; then
        setup_hint="登录账号: $USERNAME（沿用原有密码）"
    elif [ -n "$USERNAME" ]; then
        setup_hint="登录账号: $USERNAME（密码为安装时设置的密码）"
    elif [ "$FORCE_SETUP" = "yes" ]; then
        setup_hint="首次访问会进入账号设置引导。"
    else
        setup_hint="未设置账号，首次启动会在日志输出随机凭据并引导登录。"
    fi
    echo
    ok "安装完成"
    echo "  安装目录: $INSTALL_DIR"
    echo "  访问地址: http://${ip:-127.0.0.1}:$PORT"
    echo "  $setup_hint"
    echo "  管理命令: mintsoda（不带参数进入菜单）"
}

main() {
    parse_args "$@"
    require_root
    log "MintSoda 安装开始，目标目录: $INSTALL_DIR"
    prompt_credentials
    install_java
    create_user
    download_jar
    fix_permissions
    install_mintsoda
    write_env_credentials
    setup_service
    show_result
    show_hint
}

main "$@"
