package com.mogu.data.quality.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.mogu.data.common.LoginUser;
import com.mogu.data.common.Result;
import com.mogu.data.quality.dto.RuleCreateRequest;
import com.mogu.data.quality.dto.RuleUpdateRequest;
import com.mogu.data.quality.entity.QualityRule;
import com.mogu.data.quality.scheduler.QualityRuleSchedulerManager;
import com.mogu.data.quality.service.QualityRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 质量规则控制器
 *
 * @author fengzhu
 * @since 2026-05-10
 */
@Slf4j
@RestController
@RequestMapping("/quality/rules")
public class QualityRuleController {

    @Autowired
    private QualityRuleService qualityRuleService;

    // 仅在 scheduler.type=local 时存在；dolphinscheduler 模式下为 null，相关调度调用由各方法内的 try/catch 优雅跳过
    @Autowired(required = false)
    private QualityRuleSchedulerManager schedulerManager;

    /**
     * 创建质量规则
     *
     * @param request 创建请求
     * @return 规则ID
     */
    @PostMapping
    public Result<Long> createRule(@Valid @RequestBody RuleCreateRequest request) {
        log.info("创建质量规则请求: {}", request);

        try {
            String createdBy = LoginUser.currentUsername();
            if (createdBy == null) {
                createdBy = "system"; // 默认值（理论上不应该到达这里，因为有拦截器）
            }
            Long ruleId = qualityRuleService.createRule(request, createdBy);

            // 如果规则支持SCHEDULED模式且已启用，注册定时调度
            if (request.getEnabled() != null && request.getEnabled()
                    && request.getCheckModes() != null
                    && request.getCheckModes().contains("SCHEDULED")
                    && request.getScheduleCron() != null
                    && !request.getScheduleCron().trim().isEmpty()) {
                QualityRule rule = qualityRuleService.getRule(ruleId);
                if (rule != null) {
                    try {
                        schedulerManager.schedule(rule);
                        log.info("规则创建成功并已注册调度: ruleId={}", ruleId);
                    } catch (Exception e) {
                        log.warn("规则创建成功但调度注册失败: ruleId={}, error={}", ruleId, e.getMessage());
                    }
                }
            }

            return Result.ok("规则创建成功", ruleId);

        } catch (IllegalArgumentException e) {
            log.error("创建规则失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("创建规则异常", e);
            String errorMsg = e.getMessage();
            if (errorMsg == null) {
                errorMsg = e.getClass().getSimpleName();
            }
            return Result.error("创建规则失败: " + errorMsg);
        }
    }

    /**
     * 更新质量规则
     *
     * @param ruleId 规则ID
     * @param request 更新请求
     * @return 是否成功
     */
    @PutMapping("/{id}")
    public Result<Boolean> updateRule(@PathVariable("id") Long ruleId,
                                     @Valid @RequestBody RuleUpdateRequest request) {
        log.info("更新质量规则请求[ruleId={}]: {}", ruleId, request);

        try {
            boolean success = qualityRuleService.updateRule(ruleId, request);

            if (success) {
                // 重新调度规则
                QualityRule rule = qualityRuleService.getRule(ruleId);
                if (rule != null) {
                    try {
                        schedulerManager.reschedule(rule);
                        log.info("规则更新成功并已重新调度: ruleId={}", ruleId);
                    } catch (Exception e) {
                        log.warn("规则更新成功但重新调度失败: ruleId={}, error={}", ruleId, e.getMessage());
                    }
                }
            }

            return success ? Result.ok("规则更新成功", true) : Result.error("规则更新失败");

        } catch (IllegalArgumentException e) {
            log.error("更新规则失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新规则异常", e);
            return Result.error("更新规则失败: " + e.getMessage());
        }
    }

    /**
     * 删除质量规则
     *
     * @param ruleId 规则ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteRule(@PathVariable("id") Long ruleId) {
        log.info("删除质量规则请求[ruleId={}]", ruleId);

        try {
            boolean success = qualityRuleService.deleteRule(ruleId);

            if (success) {
                // 取消定时调度
                try {
                    schedulerManager.cancel(ruleId);
                    log.info("规则删除成功并已取消调度: ruleId={}", ruleId);
                } catch (Exception e) {
                    log.warn("规则删除成功但取消调度失败: ruleId={}, error={}", ruleId, e.getMessage());
                }
            }

            return success ? Result.ok("规则删除成功", true) : Result.error("规则删除失败");

        } catch (Exception e) {
            log.error("删除规则异常", e);
            return Result.error("删除规则失败: " + e.getMessage());
        }
    }

    /**
     * 获取规则详情
     *
     * @param ruleId 规则ID
     * @return 规则详情
     */
    @GetMapping("/{id}")
    public Result<QualityRule> getRule(@PathVariable("id") Long ruleId) {
        log.info("获取规则详情请求[ruleId={}]", ruleId);

        try {
            QualityRule rule = qualityRuleService.getRule(ruleId);

            return Result.ok(rule);

        } catch (IllegalArgumentException e) {
            log.error("获取规则失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取规则异常", e);
            return Result.error("获取规则失败: " + e.getMessage());
        }
    }

    /**
     * 查询规则列表
     *
     * @param tableId 表ID（可选）
     * @param ruleTemplate 规则模板（可选）
     * @param enabled 是否启用（可选）
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 规则列表
     */
    @GetMapping
    public Result<IPage<QualityRule>> listRules(
            @RequestParam(value = "tableId", required = false) Long tableId,
            @RequestParam(value = "ruleName", required = false) String ruleName,
            @RequestParam(value = "ruleType", required = false) String ruleType,
            @RequestParam(value = "ruleTemplate", required = false) String ruleTemplate,
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        log.info("查询规则列表请求[tableId={}, ruleName={}, ruleType={}, template={}, enabled={}, page={}, size={}]",
            tableId, ruleName, ruleType, ruleTemplate, enabled, pageNum, pageSize);

        try {
            IPage<QualityRule> page = qualityRuleService.listRules(
                tableId, ruleName, ruleType, ruleTemplate, enabled, pageNum, pageSize);

            return Result.ok(page);

        } catch (Exception e) {
            log.error("查询规则列表异常", e);
            return Result.error("查询规则列表失败: " + e.getMessage());
        }
    }

    /**
     * 启用规则
     *
     * @param ruleId 规则ID
     * @return 是否成功
     */
    @PostMapping("/{id}/enable")
    public Result<Boolean> enableRule(@PathVariable("id") Long ruleId) {
        log.info("启用规则请求[ruleId={}]", ruleId);

        try {
            boolean success = qualityRuleService.enableRule(ruleId);

            if (success) {
                // 如果规则支持SCHEDULED模式，注册定时调度
                QualityRule rule = qualityRuleService.getRule(ruleId);
                if (rule != null && rule.getCheckMode() != null
                        && rule.getCheckMode().contains("SCHEDULED")
                        && rule.getScheduleCron() != null
                        && !rule.getScheduleCron().trim().isEmpty()) {
                    try {
                        schedulerManager.schedule(rule);
                        log.info("规则启用成功并已注册调度: ruleId={}", ruleId);
                    } catch (Exception e) {
                        log.warn("规则启用成功但调度注册失败: ruleId={}, error={}", ruleId, e.getMessage());
                    }
                }
            }

            return success ? Result.ok("规则启用成功", true) : Result.error("规则启用失败");

        } catch (Exception e) {
            log.error("启用规则异常", e);
            return Result.error("启用规则失败: " + e.getMessage());
        }
    }

    /**
     * 禁用规则
     *
     * @param ruleId 规则ID
     * @return 是否成功
     */
    @PostMapping("/{id}/disable")
    public Result<Boolean> disableRule(@PathVariable("id") Long ruleId) {
        log.info("禁用规则请求[ruleId={}]", ruleId);

        try {
            boolean success = qualityRuleService.disableRule(ruleId);

            if (success) {
                // 取消定时调度
                try {
                    schedulerManager.cancel(ruleId);
                    log.info("规则禁用成功并已取消调度: ruleId={}", ruleId);
                } catch (Exception e) {
                    log.warn("规则禁用成功但取消调度失败: ruleId={}, error={}", ruleId, e.getMessage());
                }
            }

            return success ? Result.ok("规则禁用成功", true) : Result.error("规则禁用失败");

        } catch (Exception e) {
            log.error("禁用规则异常", e);
            return Result.error("禁用规则失败: " + e.getMessage());
        }
    }

    /**
     * 测试规则
     *
     * @param ruleId 规则ID
     * @return 测试结果
     */
    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> testRule(@PathVariable("id") Long ruleId) {
        log.info("测试规则请求[ruleId={}]", ruleId);

        try {
            Map<String, Object> result = qualityRuleService.testRule(ruleId);

            return Result.ok("规则测试成功", result);

        } catch (Exception e) {
            log.error("测试规则异常", e);
            return Result.error("测试规则失败: " + e.getMessage());
        }
    }

    /**
     * 复制规则
     *
     * @param ruleId 原规则ID
     * @param newRuleName 新规则名称
     * @return 新规则ID
     */
    @PostMapping("/{id}/copy")
    public Result<Long> copyRule(@PathVariable("id") Long ruleId,
                               @RequestParam("newRuleName") String newRuleName) {
        log.info("复制规则请求[ruleId={}, newRuleName={}]", ruleId, newRuleName);

        try {
            String createdBy = LoginUser.currentUsername();
            if (createdBy == null) {
                createdBy = "system"; // 默认值（理论上不应该到达这里，因为有拦截器）
            }
            Long newRuleId = qualityRuleService.copyRule(ruleId, newRuleName, createdBy);

            return Result.ok("规则复制成功", newRuleId);

        } catch (Exception e) {
            log.error("复制规则异常", e);
            return Result.error("复制规则失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除规则
     *
     * @param ruleIds 规则ID列表
     * @return 删除数量
     */
    @DeleteMapping("/batch")
    public Result<Integer> batchDeleteRules(@RequestBody List<Long> ruleIds) {
        log.info("批量删除规则请求[ruleIds={}]", ruleIds);

        try {
            int count = 0;
            for (Long ruleId : ruleIds) {
                if (qualityRuleService.deleteRule(ruleId)) {
                    count++;
                }
            }

            return Result.ok("成功删除" + count + "条规则", count);

        } catch (Exception e) {
            log.error("批量删除规则异常", e);
            return Result.error("批量删除规则失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有规则模板
     *
     * @return 规则模板列表
     */
    @GetMapping("/templates")
    public Result<List> getRuleTemplates() {
        log.info("获取规则模板列表请求");

        try {
            // 这里需要从RuleTemplateFactory获取模板列表
            // 暂时返回空列表
            return Result.ok(Arrays.asList(
                "NULL_CHECK",
                "ROW_COUNT_CHECK",
                "ROW_COUNT_FLUCTUATION",
                "UNIQUE_CHECK",
                "ENUM_CHECK",
                "REGEX_CHECK",
                "NUMERIC_RANGE_CHECK",
                "DATE_RANGE_CHECK",
                "BUSINESS_RULE"
            ));

        } catch (Exception e) {
            log.error("获取规则模板列表异常", e);
            return Result.error("获取规则模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取规则模板的参数定义
     *
     * @param templateName 模板名称
     * @return 参数定义
     */
    @GetMapping("/templates/{name}/params")
    public Result<List> getTemplateParams(@PathVariable("name") String templateName) {
        log.info("获取规则模板参数定义请求[templateName={}]", templateName);

        try {
            // 这里需要从RuleTemplateFactory获取参数定义
            // 暂时返回空列表
            return Result.ok(Arrays.asList());

        } catch (Exception e) {
            log.error("获取规则模板参数定义异常", e);
            return Result.error("获取规则模板参数定义失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有数据库列表
     *
     * @return 数据库列表
     */
    @GetMapping("/metadata/databases")
    public Result<List<String>> getDatabases() {
        log.info("获取数据库列表请求");

        try {
            List<String> databases = qualityRuleService.getAllDatabases();
            return Result.ok(databases);

        } catch (Exception e) {
            log.error("获取数据库列表异常", e);
            return Result.error("获取数据库列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据数据库名获取表列表
     *
     * @param database 数据库名
     * @return 表列表
     */
    @GetMapping("/metadata/tables")
    public Result<List<Map<String, String>>> getTables(@RequestParam("database") String database) {
        log.info("获取表列表请求[database={}]", database);

        try {
            List<Map<String, String>> tables = qualityRuleService.getTablesByDatabase(database);
            return Result.ok(tables);

        } catch (Exception e) {
            log.error("获取表列表异常", e);
            return Result.error("获取表列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据表名获取字段列表
     *
     * @param database 数据库名
     * @param table 表名
     * @return 字段列表
     */
    @GetMapping("/metadata/columns")
    public Result<List<String>> getColumns(
            @RequestParam("database") String database,
            @RequestParam("table") String table) {
        log.info("获取字段列表请求[database={}, table={}]", database, table);

        try {
            List<String> columns = qualityRuleService.getColumnsByTable(database, table);
            return Result.ok(columns);

        } catch (Exception e) {
            log.error("获取字段列表异常", e);
            return Result.error("获取字段列表失败: " + e.getMessage());
        }
    }

}
