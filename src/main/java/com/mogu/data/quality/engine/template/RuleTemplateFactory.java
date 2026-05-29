package com.mogu.data.quality.engine.template;

import com.mogu.data.quality.engine.template.impl.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则模板工厂
 * 负责管理所有规则模板的注册和获取
 *
 * @author fengzhu
 * @since 2026-05-09
 */
@Slf4j
@Component
public class RuleTemplateFactory {

    /**
     * 规则模板缓存
     * Key: 规则模板名称
     * Value: 规则模板实例
     */
    private final Map<String, RuleTemplate> templateCache = new ConcurrentHashMap<>();

    /**
     * 规则模板类型索引
     * Key: 规则类型（TABLE/COLUMN）
     * Value: 该类型下的所有模板名称列表
     */
    private final Map<String, List<String>> typeIndex = new ConcurrentHashMap<>();

    /**
     * 初始化工厂，注册所有内置规则模板
     */
    @PostConstruct
    public void initialize() {
        log.info("开始初始化规则模板工厂...");

        // 注册所有内置规则模板
        registerTemplate(new NullCheckTemplate());
        registerTemplate(new RowCountCheckTemplate());
        registerTemplate(new RowCountFluctuationTemplate());
        registerTemplate(new UniqueCheckTemplate());
        registerTemplate(new EnumCheckTemplate());
        registerTemplate(new RegexCheckTemplate());
        registerTemplate(new NumericRangeCheckTemplate());
        registerTemplate(new DateRangeCheckTemplate());
        registerTemplate(new BusinessRuleTemplate());

        log.info("规则模板工厂初始化完成，共注册{}个规则模板", templateCache.size());
    }

    /**
     * 注册规则模板
     *
     * @param template 规则模板
     */
    public void registerTemplate(RuleTemplate template) {
        String name = template.getName();
        String type = template.getType();

        templateCache.put(name, template);

        // 更新类型索引
        typeIndex.computeIfAbsent(type, k -> new ArrayList<>()).add(name);

        log.debug("注册规则模板: name={}, type={}, description={}",
            name, type, template.getDescription());
    }

    /**
     * 根据名称获取规则模板
     *
     * @param name 规则模板名称
     * @return 规则模板
     * @throws IllegalArgumentException 如果模板不存在
     */
    public RuleTemplate getTemplate(String name) {
        RuleTemplate template = templateCache.get(name);
        if (template == null) {
            throw new IllegalArgumentException(
                String.format("规则模板不存在: %s，可用的模板: %s", name, getAvailableTemplateNames())
            );
        }
        return template;
    }

    /**
     * 根据类型获取所有规则模板
     *
     * @param type 规则类型（TABLE/COLUMN）
     * @return 该类型下的所有规则模板
     */
    public List<RuleTemplate> getTemplatesByType(String type) {
        List<String> templateNames = typeIndex.get(type);
        if (templateNames == null || templateNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<RuleTemplate> templates = new ArrayList<>();
        for (String name : templateNames) {
            templates.add(templateCache.get(name));
        }
        return templates;
    }

    /**
     * 获取所有规则模板
     *
     * @return 所有规则模板
     */
    public Collection<RuleTemplate> getAllTemplates() {
        return templateCache.values();
    }

    /**
     * 获取所有可用的规则模板名称
     *
     * @return 所有规则模板名称
     */
    public List<String> getAvailableTemplateNames() {
        return new ArrayList<>(templateCache.keySet());
    }

    /**
     * 检查规则模板是否存在
     *
     * @param name 规则模板名称
     * @return 是否存在
     */
    public boolean hasTemplate(String name) {
        return templateCache.containsKey(name);
    }

    /**
     * 获取规则模板数量
     *
     * @return 规则模板数量
     */
    public int getTemplateCount() {
        return templateCache.size();
    }

    /**
     * 获取规则模板的详细信息列表
     *
     * @return 规则模板信息列表
     */
    public List<TemplateInfo> getTemplateInfos() {
        List<TemplateInfo> infos = new ArrayList<>();
        for (RuleTemplate template : templateCache.values()) {
            infos.add(new TemplateInfo(
                template.getName(),
                template.getType(),
                template.getDescription(),
                template.getParamDefinitions()
            ));
        }
        return infos;
    }

    /**
     * 规则模板信息
     */
    public static class TemplateInfo {
        private final String name;
        private final String type;
        private final String description;
        private final List<?> paramDefinitions;

        public TemplateInfo(String name, String type, String description, List<?> paramDefinitions) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.paramDefinitions = paramDefinitions;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        public List<?> getParamDefinitions() {
            return paramDefinitions;
        }
    }
}
