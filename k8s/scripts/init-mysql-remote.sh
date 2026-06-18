#!/bin/bash

###############################################################################
# MySQL 远程初始化脚本
# 支持多种方式初始化外接 MySQL 服务的 schema
###############################################################################

set -e

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_step() {
    echo -e "${BLUE}===>${NC} $1"
}

# 检查环境变量
check_env_vars() {
    log_step "检查环境变量..."

    if [ -z "$MYSQL_HOST" ]; then
        log_error "请设置 MYSQL_HOST 环境变量"
        echo "  export MYSQL_HOST=your-mysql-host"
        exit 1
    fi

    if [ -z "$MYSQL_PASSWORD" ]; then
        log_error "请设置 MYSQL_PASSWORD 环境变量"
        echo "  export MYSQL_PASSWORD=your-password"
        exit 1
    fi

    log_info "✓ 环境变量检查通过"
    log_info "  Host: $MYSQL_HOST:${MYSQL_PORT:-3306}"
    log_info "  User: ${MYSQL_USER:-root}"
}

# 获取脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="$SCRIPT_DIR/../sql"

# 确保 SQL 目录存在
mkdir -p "$SQL_DIR"

# 方法 1：使用本地 mysql 客户端
init_with_mysql_client() {
    log_step "方法 1：使用本地 MySQL 客户端"

    if ! command -v mysql &> /dev/null; then
        log_warn "mysql 客户端未安装，跳过此方法"
        return 1
    fi

    log_info "✓ 找到 mysql 客户端: $(mysql --version 2>&1 | head -1)"

    # 创建 MDataX 数据库
    log_info "创建 MDataX 数据库..."
    mysql -h"$MYSQL_HOST" \
          -P"${MYSQL_PORT:-3306}" \
          -u"${MYSQL_USER:-root}" \
          -p"$MYSQL_PASSWORD" <<EOF
CREATE DATABASE IF NOT EXISTS mdatax
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
EOF
    log_info "✓ MDataX 数据库创建成功"

    # 导入 MDataX 表结构
    if [ -f "$SQL_DIR/mdatax-schema.sql" ]; then
        log_info "导入 MDataX 表结构..."
        mysql -h"$MYSQL_HOST" \
              -P"${MYSQL_PORT:-3306}" \
              -u"${MYSQL_USER:-root}" \
              -p"$MYSQL_PASSWORD" \
              mdatax < "$SQL_DIR/mdatax-schema.sql"
        log_info "✓ MDataX 表结构导入成功"
    else
        log_warn "MDataX SQL 文件不存在: $SQL_DIR/mdatax-schema.sql"
    fi

    # 创建 DolphinScheduler 数据库
    log_info "创建 DolphinScheduler 数据库..."
    mysql -h"$MYSQL_HOST" \
          -P"${MYSQL_PORT:-3306}" \
          -u"${MYSQL_USER:-root}" \
          -p"$MYSQL_PASSWORD" <<EOF
CREATE DATABASE IF NOT EXISTS dolphinscheduler
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
EOF
    log_info "✓ DolphinScheduler 数据库创建成功"

    # 导入 DS 表结构
    if [ -f "$SQL_DIR/dolphinscheduler-mysql-3.2.0.sql" ]; then
        log_info "导入 DolphinScheduler 表结构..."
        mysql -h"$MYSQL_HOST" \
              -P"${MYSQL_PORT:-3306}" \
              -u"${MYSQL_USER:-root}" \
              -p"$MYSQL_PASSWORD" \
              dolphinscheduler < "$SQL_DIR/dolphinscheduler-mysql-3.2.0.sql
        log_info "✓ DolphinScheduler 表结构导入成功"
    else
        log_warn "DolphinScheduler SQL 文件不存在: $SQL_DIR/dolphinscheduler-mysql-3.2.0.sql"
        log_info "请下载 DS SQL 文件："
        log_info "  cd $SQL_DIR"
        log_info "  wget https://downloads.apache.org/dolphinscheduler/3.2.0/apache-dolphinscheduler-3.2.0-src.zip"
        log_info "  unzip apache-dolphinscheduler-3.2.0-src.zip"
        log_info "  cp apache-dolphinscheduler-3.2.0-src/dolphinscheduler-dao/src/main/resources/sql/dolphinscheduler_mysql.sql dolphinscheduler-mysql-3.2.0.sql"
    fi

    log_info "✓ MySQL 客户端初始化完成"
    return 0
}

# 方法 2：使用 Docker
init_with_docker() {
    log_step "方法 2：使用 Docker"

    if ! command -v docker &> /dev/null; then
        log_warn "docker 未安装，跳过此方法"
        return 1
    fi

    log_info "✓ 找到 docker: $(docker --version | head -1)"

    # 创建 MDataX 数据库
    log_info "创建 MDataX 数据库..."
    docker run --rm mysql:8.0 mysql \
        -h"$MYSQL_HOST" \
        -P"${MYSQL_PORT:-3306}" \
        -u"${MYSQL_USER:-root}" \
        -p"$MYSQL_PASSWORD" \
        -e "CREATE DATABASE IF NOT EXISTS mdatax DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    log_info "✓ MDataX 数据库创建成功"

    # 导入 MDataX 表结构
    if [ -f "$SQL_DIR/mdatax-schema.sql" ]; then
        log_info "导入 MDataX 表结构..."
        docker run --rm -v "$SQL_DIR:/sql" mysql:8.0 mysql \
            -h"$MYSQL_HOST" \
            -P"${MYSQL_PORT:-3306}" \
            -u"${MYSQL_USER:-root}" \
            -p"$MYSQL_PASSWORD" \
            mdatax < /sql/mdatax-schema.sql
        log_info "✓ MDataX 表结构导入成功"
    fi

    # 创建 DolphinScheduler 数据库
    log_info "创建 DolphinScheduler 数据库..."
    docker run --rm mysql:8.0 mysql \
        -h"$MYSQL_HOST" \
        -P"${MYSQL_PORT:-3306}" \
        -u"${MYSQL_USER:-root}" \
        -p"$MYSQL_PASSWORD" \
        -e "CREATE DATABASE IF NOT EXISTS dolphinscheduler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    log_info "✓ DolphinScheduler 数据库创建成功"

    # 导入 DS 表结构
    if [ -f "$SQL_DIR/dolphinscheduler-mysql-3.2.0.sql" ]; then
        log_info "导入 DolphinScheduler 表结构..."
        docker run --rm -v "$SQL_DIR:/sql" mysql:8.0 mysql \
            -h"$MYSQL_HOST" \
            -P"${MYSQL_PORT:-3306}" \
            -u"${MYSQL_USER:-root}" \
            -p"$MYSQL_PASSWORD" \
            dolphinscheduler < /sql/dolphinscheduler-mysql-3.2.0.sql
        log_info "✓ DolphinScheduler 表结构导入成功"
    fi

    log_info "✓ Docker 初始化完成"
    return 0
}

# 方法 3：生成 SQL 脚本（供手动执行）
init_generate_sql() {
    log_step "方法 3：生成 SQL 脚本（供手动执行）"

    local output_file="$SQL_DIR/manual-init-all.sql"

    log_info "生成完整的初始化 SQL 脚本..."

    cat > "$output_file" << 'SQL_HEADER'
-- =============================================
-- MDataX + DolphinScheduler 完整初始化脚本
-- 自动生成时间: $(date '+%Y-%m-%d %H:%M:%S')
-- =============================================

-- 创建 MDataX 数据库
CREATE DATABASE IF NOT EXISTS mdatax
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

-- 创建 DolphinScheduler 数据库
CREATE DATABASE IF NOT EXISTS dolphinscheduler
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

-- 使用 MDataX 数据库
USE mdatax;

SQL_HEADER

    # 追加 MDataX SQL
    if [ -f "$SQL_DIR/mdatax-schema.sql" ]; then
        cat "$SQL_DIR/mdatax-schema.sql" >> "$output_file"
    fi

    # 追加 DS SQL
    if [ -f "$SQL_DIR/dolphinscheduler-mysql-3.2.0.sql" ]; then
        echo "" >> "$output_file"
        echo "-- =============================================" >> "$output_file"
        echo "-- DolphinScheduler 表结构" >> "$output_file"
        echo "-- =============================================" >> "$output_file"
        echo "" >> "$output_file"
        echo "USE dolphinscheduler;" >> "$output_file"
        echo "" >> "$output_file"
        cat "$SQL_DIR/dolphinscheduler-mysql-3.2.0.sql" >> "$output_file"
    fi

    log_info "✓ SQL 脚本已生成: $output_file"
    log_info "  您可以使用以下方式执行："
    log_info "  1. 通过 GUI 工具（MySQL Workbench、DBeaver 等）"
    log_info "  2. 通过命令行："
    log_info "     mysql -h$MYSQL_HOST -P${MYSQL_PORT:-3306} -u${MYSQL_USER:-root} -p < $output_file"

    return 0
}

# 验证初始化
verify_init() {
    log_step "验证初始化结果"

    # 选择验证方式
    if command -v mysql &> /dev/null; then
        USE_MYSQL_CLIENT=true
    elif command -v docker &> /dev/null; then
        USE_MYSQL_CLIENT=false
    else
        log_warn "无法验证，未找到 mysql 客户端或 docker"
        return 0
    fi

    # 验证 MDataX 数据库
    log_info "验证 MDataX 数据库..."

    if [ "$USE_MYSQL_CLIENT" = true ]; then
        TABLE_COUNT=$(mysql -h"$MYSQL_HOST" \
            -P"${MYSQL_PORT:-3306}" \
            -u"${MYSQL_USER:-root}" \
            -p"$MYSQL_PASSWORD" \
            -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mdatax';" 2>/dev/null || echo "0")
    else
        TABLE_COUNT=$(docker run --rm mysql:8.0 mysql \
            -h"$MYSQL_HOST" \
            -P"${MYSQL_PORT:-3306}" \
            -u"${MYSQL_USER:-root}" \
            -p"$MYSQL_PASSWORD" \
            -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mdatax';" 2>/dev/null || echo "0")
    fi

    if [ "$TABLE_COUNT" -gt 0 ]; then
        log_info "✓ MDataX 数据库验证成功（$TABLE_COUNT 个表）"
    else
        log_error "✗ MDataX 数据库验证失败"
        return 1
    fi

    # 验证 DolphinScheduler 数据库
    log_info "验证 DolphinScheduler 数据库..."

    if [ "$USE_MYSQL_CLIENT" = true ]; then
        DS_TABLE_COUNT=$(mysql -h"$MYSQL_HOST" \
            -P"${MYSQL_PORT:-3306}" \
            -u"${MYSQL_USER:-root}" \
            -p"$MYSQL_PASSWORD" \
            -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='dolphinscheduler';" 2>/dev/null || echo "0")
    else
        DS_TABLE_COUNT=$(docker run --rm mysql:8.0 mysql \
            -h"$MYSQL_HOST" \
            -P"${MYSQL_PORT:-3306}" \
            -u"${MYSQL_USER:-root}" \
            -p"$MYSQL_PASSWORD" \
            -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='dolphinscheduler';" 2>/dev/null || echo "0")
    fi

    if [ "$DS_TABLE_COUNT" -gt 0 ]; then
        log_info "✓ DolphinScheduler 数据库验证成功（$DS_TABLE_COUNT 个表）"
    else
        log_warn "⚠ DolphinScheduler 数据库未初始化（可能未下载 SQL 文件）"
    fi

    log_info "✓ 验证完成"

    # 显示默认账号信息
    echo ""
    log_info "默认账号信息："
    log_info "  MDataX: admin / admin123"
    log_info "  DolphinScheduler: admin / dolphinscheduler123"
}

# 主函数
main() {
    echo ""
    echo "=========================================="
    echo "MySQL Schema 初始化"
    echo "=========================================="
    echo ""

    check_env_vars

    # 尝试不同的初始化方法
    local success=false

    # 方法 1：使用本地 mysql 客户端
    if init_with_mysql_client; then
        success=true
    # 方法 2：使用 Docker
    elif init_with_docker; then
        success=true
    else
        # 方法 3：生成 SQL 脚本
        log_warn "无法自动初始化，生成 SQL 脚本供手动执行"
        init_generate_sql

        echo ""
        log_warn "请手动执行以下步骤："
        log_warn "1. 打开 MySQL Workbench 或其他 GUI 工具"
        log_warn "2. 连接到 MySQL: $MYSQL_HOST:${MYSQL_PORT:-3306}"
        log_warn "3. 打开并执行: $SQL_DIR/manual-init-all.sql"
        log_warn "4. 或者单独执行各个 SQL 文件"
    fi

    echo ""

    # 验证
    if [ "$success" = true ]; then
        verify_init
    fi

    echo ""
    echo "=========================================="
    if [ "$success" = true ]; then
        log_info "✓ 初始化完成！"
    else
        log_info "⚠ 请手动完成初始化"
    fi
    echo "=========================================="
    echo ""
}

# 执行主函数
main "$@"
