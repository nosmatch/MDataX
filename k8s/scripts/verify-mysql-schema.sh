#!/bin/bash

###############################################################################
# MySQL Schema 验证脚本
# 用于验证 MDataX 数据库的所有表是否正确创建
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
if [ -z "$MYSQL_HOST" ] || [ -z "$MYSQL_PASSWORD" ]; then
    log_error "请设置环境变量：MYSQL_HOST, MYSQL_PASSWORD"
    exit 1
fi

MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"

echo ""
echo "=========================================="
echo "MDataX Schema 验证"
echo "=========================================="
echo ""
log_info "Host: $MYSQL_HOST:$MYSQL_PORT"
log_info "User: $MYSQL_USER"
echo ""

# 选择 mysql 客户端
if command -v mysql &> /dev/null; then
    MYSQL_CMD="mysql -h$MYSQL_HOST -P$MYSQL_PORT -u$MYSQL_USER -p$MYSQL_PASSWORD"
elif command -v docker &> /dev/null; then
    MYSQL_CMD="docker run --rm mysql:8.0 mysql -h$MYSQL_HOST -P$MYSQL_PORT -u$MYSQL_USER -p$MYSQL_PASSWORD"
else
    log_error "未找到 mysql 客户端或 docker"
    exit 1
fi

# 期望的 22 个表
EXPECTED_TABLES=(
    "sys_user"
    "sys_role"
    "sys_user_role"
    "sys_role_permission"
    "sys_operation_log"
    "datasource"
    "metadata_table"
    "metadata_column"
    "user_table_visit"
    "table_access_history"
    "sql_task"
    "sql_task_dependency"
    "sql_task_workflow"
    "sql_task_workflow_instance"
    "sql_task_log"
    "sync_task"
    "sync_task_log"
    "task_collaborator"
    "permission_apply"
    "report"
    "report_chart"
    "system_config"
)

log_step "1. 检查数据库是否存在"
DB_EXISTS=$($MYSQL_CMD -N -e "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = 'mdatax';" 2>/dev/null | wc -l)

if [ "$DB_EXISTS" -eq 0 ]; then
    log_error "✗ 数据库 mdatax 不存在"
    exit 1
else
    log_info "✓ 数据库 mdatax 存在"
fi

echo ""

log_step "2. 检查表数量"
TABLE_COUNT=$($MYSQL_CMD -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'mdatax';" 2>/dev/null)

log_info "当前表数量: $TABLE_COUNT"
log_info "期望表数量: ${#EXPECTED_TABLES[@]}"

if [ "$TABLE_COUNT" -eq "${#EXPECTED_TABLES[@]}" ]; then
    log_info "✓ 表数量正确"
else
    log_error "✗ 表数量不匹配！"
fi

echo ""

log_step "3. 检查每个表是否存在"
MISSING_TABLES=()

for table in "${EXPECTED_TABLES[@]}"; do
    EXISTS=$($MYSQL_CMD -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'mdatax' AND table_name = '$table';" 2>/dev/null)

    if [ "$EXISTS" -eq 1 ]; then
        echo -e "${GREEN}✓${NC} $table"
    else
        echo -e "${RED}✗${NC} $table (缺失)"
        MISSING_TABLES+=("$table")
    fi
done

echo ""

if [ ${#MISSING_TABLES[@]} -gt 0 ]; then
    log_error "缺失的表: ${MISSING_TABLES[*]}"
    echo ""
    log_info "请执行以下命令重新初始化："
    log_info "  mysql -h$MYSQL_HOST -u$MYSQL_USER -p < k8s/sql/mdatax-schema.sql"
    exit 1
fi

log_step "4. 验证表结构"

# 检查关键表的字段数量
declare -A TABLE_COLUMNS=(
    ["sys_user"]="10"
    ["sys_role"]="7"
    ["datasource"]="13"
    ["sql_task"]="15"
    ["report"]="10"
    ["report_chart"]="9"
)

for table in "${!TABLE_COLUMNS[@]}"; do
    EXPECTED=${TABLE_COLUMNS[$table]}
    ACTUAL=$($MYSQL_CMD -N -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'mdatax' AND table_name = '$table';" 2>/dev/null)

    if [ "$ACTUAL" -eq "$EXPECTED" ]; then
        log_info "✓ $table: $ACTUAL 列"
    else
        log_warn "⚠ $table: 期望 $EXPECTED 列，实际 $ACTUAL 列"
    fi
done

echo ""

log_step "5. 验证初始数据"

# 检查默认用户
USER_COUNT=$($MYSQL_CMD -N -e "SELECT COUNT(*) FROM mdatax.sys_user WHERE username = 'admin';" 2>/dev/null)
if [ "$USER_COUNT" -eq 1 ]; then
    log_info "✓ 默认管理员账号存在"
else
    log_error "✗ 默认管理员账号不存在"
fi

# 检查默认角色
ROLE_COUNT=$($MYSQL_CMD -N -e "SELECT COUNT(*) FROM mdatax.sys_role WHERE role_code = 'SUPER_ADMIN';" 2>/dev/null)
if [ "$ROLE_COUNT" -eq 1 ]; then
    log_info "✓ 默认角色存在"
else
    log_error "✗ 默认角色不存在"
fi

# 检查系统配置
CONFIG_COUNT=$($MYSQL_CMD -N -e "SELECT COUNT(*) FROM mdatax.system_config;" 2>/dev/null)
if [ "$CONFIG_COUNT" -ge 4 ]; then
    log_info "✓ 系统配置已初始化 ($CONFIG_COUNT 条)"
else
    log_warn "⚠ 系统配置可能不完整"
fi

echo ""

log_step "6. 显示表统计"
$MYSQL_CMD -e "
SELECT
    TABLE_NAME as '表名',
    TABLE_ROWS as '行数',
    ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024, 2) as '大小(KB)'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'mdatax'
ORDER BY TABLE_ROWS DESC;
" 2>/dev/null

echo ""
echo "=========================================="
if [ ${#MISSING_TABLES[@]} -eq 0 ] && [ "$TABLE_COUNT" -eq "${#EXPECTED_TABLES[@]}" ]; then
    log_info "✓ 验证通过！所有 22 个表已正确创建"
else
    log_error "✗ 验证失败，请检查上述错误"
    exit 1
fi
echo "=========================================="
echo ""

log_info "默认账号："
log_info "  用户名: admin"
log_info "  密码: admin123"
echo ""
