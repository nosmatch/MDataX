package com.mogu.data.metadata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mogu.data.metadata.entity.MetadataColumn;
import com.mogu.data.metadata.entity.MetadataTable;
import com.mogu.data.metadata.mapper.MetadataColumnMapper;
import com.mogu.data.metadata.mapper.MetadataTableMapper;
import com.mogu.data.metadata.vo.TableDetailVO;
import com.mogu.data.metadata.vo.TablePageVO;
import com.mogu.data.system.entity.User;
import com.mogu.data.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 元数据-表服务
 *
 * @author fengzhu
 */
@Service
@RequiredArgsConstructor
public class MetadataTableService extends ServiceImpl<MetadataTableMapper, MetadataTable> {

    private final UserMapper userMapper;
    private final MetadataColumnMapper columnMapper;

    /**
     * 分页查询表列表（支持关键词搜索 + 责任人名称关联）
     */
    public Page<TablePageVO> pageTables(String keyword, long page, long size) {
        LambdaQueryWrapper<MetadataTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetadataTable::getDeleted, 0);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(MetadataTable::getDatabaseName, keyword)
                    .or()
                    .like(MetadataTable::getTableName, keyword));
        }

        wrapper.orderByDesc(MetadataTable::getCreateTime);
        Page<MetadataTable> tablePage = page(new Page<>(page, size), wrapper);

        Map<Long, String> userNameMap = resolveUserNames(tablePage.getRecords().stream()
                .map(MetadataTable::getOwnerId)
                .distinct()
                .collect(Collectors.toList()));

        List<TablePageVO> voList = tablePage.getRecords().stream().map(t -> {
            TablePageVO vo = new TablePageVO();
            vo.setId(t.getId());
            vo.setDatabaseName(t.getDatabaseName());
            vo.setTableName(t.getTableName());
            vo.setTableComment(t.getTableComment());
            vo.setEngine(t.getEngine());
            vo.setTotalRows(t.getTotalRows());
            vo.setTotalBytes(t.getTotalBytes());
            vo.setOwnerId(t.getOwnerId());
            vo.setOwnerName(userNameMap.getOrDefault(t.getOwnerId(), ""));
            vo.setCreateTime(t.getCreateTime());
            vo.setUpdateTime(t.getUpdateTime());
            return vo;
        }).collect(Collectors.toList());

        Page<TablePageVO> result = new Page<>();
        result.setCurrent(tablePage.getCurrent());
        result.setSize(tablePage.getSize());
        result.setTotal(tablePage.getTotal());
        result.setRecords(voList);
        return result;
    }

    /**
     * 查询表详情（含责任人名称）
     */
    public TableDetailVO getTableDetail(Long id) {
        MetadataTable table = getById(id);
        if (table == null || table.getDeleted() != null && table.getDeleted() == 1) {
            return null;
        }

        Map<Long, String> userNameMap = resolveUserNames(Collections.singletonList(table.getOwnerId()));

        TableDetailVO vo = new TableDetailVO();
        vo.setId(table.getId());
        vo.setDatabaseName(table.getDatabaseName());
        vo.setTableName(table.getTableName());
        vo.setTableComment(table.getTableComment());
        vo.setEngine(table.getEngine());
        vo.setTotalRows(table.getTotalRows());
        vo.setTotalBytes(table.getTotalBytes());
        vo.setOwnerId(table.getOwnerId());
        vo.setOwnerName(userNameMap.getOrDefault(table.getOwnerId(), ""));
        vo.setCreateTime(table.getCreateTime());
        vo.setUpdateTime(table.getUpdateTime());
        return vo;
    }

    /**
     * 查询表的字段列表
     */
    public List<MetadataColumn> getColumns(Long tableId) {
        LambdaQueryWrapper<MetadataColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetadataColumn::getTableId, tableId);
        wrapper.orderByAsc(MetadataColumn::getOrdinalPosition);
        return columnMapper.selectList(wrapper);
    }

    /**
     * 查询所有表（不分页，用于权限配置）
     */
    public List<MetadataTable> listAllTables() {
        LambdaQueryWrapper<MetadataTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetadataTable::getDeleted, 0);
        wrapper.orderByDesc(MetadataTable::getCreateTime);
        return list(wrapper);
    }

    private Map<Long, String> resolveUserNames(List<Long> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(ownerIds);
        return users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> u.getNickname() != null ? u.getNickname() : u.getUsername(),
                        (existing, replacement) -> existing
                ));
    }
}
