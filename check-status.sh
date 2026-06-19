#!/bin/bash
# MDataX 系统状态检查脚本
# 快速验证所有服务是否正常运行

echo "🔍 MDataX 系统状态检查"
echo "================================"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查函数
check_service() {
    local name=$1
    local url=$2
    local expected=$3

    echo -n "检查 $name... "

    if curl -s --max-time 3 "$url" > /dev/null 2>&1; then
        response=$(curl -s --max-time 3 "$url")
        if [[ $response == *"$expected"* ]] || [ -z "$expected" ]; then
            echo -e "${GREEN}✓ 正常${NC}"
            return 0
        else
            echo -e "${YELLOW}⚠ 响应异常${NC}"
            return 1
        fi
    else
        echo -e "${RED}✗ 无法访问${NC}"
        return 2
    fi
}

# 检查后端API
check_service "后端API (7081)" "http://localhost:7081/api/task/page" "未登录"

# 检查前端界面
check_service "前端界面 (5173)" "http://localhost:5173" "<!doctype"

# 检查MySQL容器
echo -n "检查 MySQL (mdatax-mysql)... "
if docker ps | grep -q "mdatax-mysql"; then
    echo -e "${GREEN}✓ 运行中${NC}"
else
    echo -e "${RED}✗ 未运行${NC}"
fi

# 检查ClickHouse容器
echo -n "检查 ClickHouse (mdatax-clickhouse)... "
if docker ps | grep -q "mdatax-clickhouse"; then
    echo -e "${GREEN}✓ 运行中${NC}"
else
    echo -e "${RED}✗ 未运行${NC}"
fi

# 检查数据库表
echo -n "检查数据库表结构... "
tables=$(docker exec mdatax-mysql mysql -uroot -proot123 mdatax -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mdatax' AND table_name LIKE 'task%';" 2>/dev/null)
if [ "$tables" -ge 6 ]; then
    echo -e "${GREEN}✓ 完整 ($tables 个任务相关表)${NC}"
else
    echo -e "${RED}✗ 表结构不完整 (仅 $tables 个表)${NC}"
    echo "   执行: mysql -u root -p mdatax < k8s/sql/mdatax-task-management.sql"
fi

echo ""
echo "================================"
echo "💡 访问地址:"
echo "  前端: http://localhost:5173"
echo "  后端: http://localhost:7081"
echo ""
echo "🔧 常用命令:"
echo "  启动后端: mvn spring-boot:run -pl mdatax-server"
echo "  启动前端: cd mdatax-web && npm run dev"
echo "  查看日志: docker logs mdatax-mysql"
