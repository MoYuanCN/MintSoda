#!/usr/bin/env bash
# ANI-RSS-Yuri 一键安装脚本
#
# 一行命令安装到当前目录（不创建子目录）并注册 systemd 服务：
#   curl -fsSL https://raw.githubusercontent.com/MoYuanCN/ani-rss-Yuri/main/linux/install-yuri.sh | bash
#
# 支持非交互参数：
#   bash install-yuri.sh --port 17789 --username myuser --password 'StrongPass!234' --yes
#
# 说明：
#   - 安装目录默认为执行命令时所在目录，可用 --dir 指定。
#   - 用户名与密码写入 /etc/ani-rss-yuri.env（仅 root 可读），由服务启动时读取；
#     程序会在启动时校验强度并只保存摘要。
#   - 未提供密码时，由程序在首次启动按平台规则生成或引导设置。
#   - --setup 会强制进入 WebUI 初始化引导，不生成随机凭据。

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'

REPO_URL="https://github.com/MoYuanCN/ani-rss-Yuri"
JAR_URL="$REPO_URL/releases/latest/download/ani-rss.jar"
ASSYURI_URL="https://raw.githubusercontent.com/MoYuanCN/ani-rss-Yuri/main/linux/assyuri"

INSTALL_DIR="$(pwd)"
PORT="17789"
SERVICE_NAME="ani-rss.service"
SERVICE_USER="ani-rss"
USERNAME=""
PASSWORD=""
ASSUME_YES="no"
FORCE_SETUP="no"

log() { echo -e "${CYAN}$*${NC}"; }
ok() { echo -e "${GREEN}$*${NC}"; }
warn() { echo -e "${YELLOW}$*${NC}"; }
die() { echo -e "${RED}$*${NC}" >&2; exit 1; }

require_root() {
    [ "$EUID" -eq 0 ] || die "请使用 root 运行：sudo bash install-yuri.sh"
}

confirm() {
    [ "$ASSUME_YES" = "yes" ] && return 0
    if [ -r /dev/tty ]; then
        read -r -p "$1 [y/N]: " reply </dev/tty || reply="n"
    else
        return 0
    fi
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
                sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
                exit 0 ;;
            *) die "未知参数: $1" ;;
        esac
    done
    [[ "$PORT" =~ ^[0-9]+$ ]] && [ "$PORT" -ge 1 ] && [ "$PORT" -le 65535 ] || die "端口无效: $PORT"
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
    local target="$INSTALL_DIR/ani-rss.jar"
    if [ -f "$target" ] && [ "$ASSUME_YES" != "yes" ]; then
        confirm "当前目录已存在 ani-rss.jar，是否覆盖？" || die "已取消"
    fi
    log "正在下载 ani-rss.jar 到 $target"
    curl -fL "$JAR_URL" -o "$target.tmp" || die "下载失败"
    mv "$target.tmp" "$target"
    ok "已下载 ani-rss.jar"
}

install_assyuri() {
    log "正在安装快捷指令 assyuri"
    local tmp
    tmp="$(mktemp)"
    local local_copy="/usr/local/share/ani-rss-yuri/assyuri"
    if curl -fsSL "$ASSYURI_URL" -o "$tmp"; then
        mkdir -p "$(dirname "$local_copy")"
        install -m 755 "$tmp" "$local_copy"
    elif [ -f "$INSTALL_DIR/linux/assyuri" ]; then
        mkdir -p "$(dirname "$local_copy")"
        install -m 755 "$INSTALL_DIR/linux/assyuri" "$local_copy"
    else
        rm -f "$tmp"
        warn "未安装快捷指令，可稍后手动复制 linux/assyuri 到 /usr/local/bin"
        return
    fi
    rm -f "$tmp"
    # 用包装脚本记录安装目录，避免改写菜单脚本本身，重新安装也不会重复替换。
    cat > /usr/local/bin/assyuri <<WRAPPER
#!/usr/bin/env bash
export ANI_RSS_DIR="$INSTALL_DIR"
exec bash "$local_copy" "\$@"
WRAPPER
    chmod 755 /usr/local/bin/assyuri
    ok "已安装 /usr/local/bin/assyuri"
}

write_env_credentials() {
    [ -n "$USERNAME" ] || return 0
    local env_file="/etc/ani-rss-yuri.env"
    local content="ANI_RSS_USERNAME=$USERNAME"
    if [ -n "$PASSWORD" ]; then
        content="$content
ANI_RSS_PASSWORD=$PASSWORD"
    fi
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
Description=ANI-RSS-Yuri
After=network.target

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_USER
WorkingDirectory=$INSTALL_DIR
ExecStart=/usr/bin/java -Xms64m -Xmx512m -Xss256k -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+IgnoreUnrecognizedVMOptions -Dfile.encoding=UTF-8 -jar $INSTALL_DIR/ani-rss.jar$setup_env
Restart=on-failure
RestartSec=15
LimitNOFILE=65535
Environment="TZ=Asia/Shanghai"
Environment="SERVER_ADDRESS=0.0.0.0"
Environment="SERVER_PORT=$PORT"
Environment="CONFIG=$INSTALL_DIR/config"
Environment="SWAGGER_ENABLED=false"
Environment="MCP_ENABLED=false"
EnvironmentFile=-/etc/ani-rss-yuri.env

[Install]
WantedBy=multi-user.target
UNIT

    systemctl daemon-reload
    systemctl enable "$SERVICE_NAME" >/dev/null 2>&1 || true
    systemctl restart "$SERVICE_NAME"
    ok "服务已启动"
}

fix_permissions() {
    mkdir -p "$INSTALL_DIR/config"
    chown -R "$SERVICE_USER:$SERVICE_USER" "$INSTALL_DIR/config"
    chown "$SERVICE_USER:$SERVICE_USER" "$INSTALL_DIR/ani-rss.jar"
    chmod 750 "$INSTALL_DIR/config"
    chmod 640 "$INSTALL_DIR/ani-rss.jar"

    # 服务以独立用户运行，安装目录必须可遍历，例如 /root 下的目录会失败。
    local readable="no"
    if command -v runuser >/dev/null 2>&1; then
        runuser -u "$SERVICE_USER" -- test -r "$INSTALL_DIR/ani-rss.jar" 2>/dev/null && readable="yes"
    elif command -v su >/dev/null 2>&1; then
        su -s /bin/sh "$SERVICE_USER" -c "test -r '$INSTALL_DIR/ani-rss.jar'" 2>/dev/null && readable="yes"
    else
        readable="yes"
    fi
    [ "$readable" = "yes" ] || die "服务用户 $SERVICE_USER 无法读取 $INSTALL_DIR/ani-rss.jar。请改用 --dir /opt/ani-rss 等可访问目录后重试"
}

show_hint() {
    echo
    echo "以后可直接运行 assyuri 打开管理菜单，或使用 bash $INSTALL_DIR/install-yuri.sh 重新安装。"
}

show_result() {
    local ip
    ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
    local setup_hint=""
    if [ "$FORCE_SETUP" = "yes" ] || [ -z "$USERNAME" ]; then
        setup_hint="首次访问会进入账号设置引导（或已在首次启动日志中输出随机凭据）。"
    else
        setup_hint="登录账号: $USERNAME"
    fi
    echo
    ok "安装完成"
    echo "  安装目录: $INSTALL_DIR"
    echo "  访问地址: http://${ip:-127.0.0.1}:$PORT"
    echo "  $setup_hint"
    echo "  管理命令: assyuri（不带参数进入菜单）"
}

main() {
    parse_args "$@"
    require_root
    log "ANI-RSS-Yuri 安装开始，目标目录: $INSTALL_DIR"
    install_java
    create_user
    download_jar
    fix_permissions
    install_assyuri
    write_env_credentials
    setup_service
    show_result
    show_hint
}

main "$@"
