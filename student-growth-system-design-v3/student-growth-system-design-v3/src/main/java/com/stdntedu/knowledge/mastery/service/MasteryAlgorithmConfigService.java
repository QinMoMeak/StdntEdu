package com.stdntedu.knowledge.mastery.service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.stdntedu.knowledge.mastery.algorithm.MasteryAlgorithmConfig;
import com.stdntedu.knowledge.mastery.evidence.MasteryEvidenceResult;
import com.stdntedu.knowledge.mastery.evidence.MasteryEvidenceType;
import com.stdntedu.wrongquestion.entity.SystemConfigReferenceEntity;
import com.stdntedu.wrongquestion.mapper.SystemConfigReferenceMapper;
import org.springframework.stereotype.Service;

@Service
public class MasteryAlgorithmConfigService {
    private static final Set<String> REQUIRED_KEYS = Set.of(
            "mastery.algorithm.version", "mastery.score_rate.correct_min", "mastery.score_rate.partial_min",
            "mastery.time_decay.enabled", "system.timezone",
            "mastery.initial.correct", "mastery.initial.partial", "mastery.initial.wrong", "mastery.initial.unknown",
            "mastery.change.exam.correct", "mastery.change.exam.partial", "mastery.change.exam.wrong",
            "mastery.change.practice.correct", "mastery.change.practice.partial", "mastery.change.practice.wrong",
            "mastery.change.review.correct", "mastery.change.review.partial", "mastery.change.review.wrong",
            "mastery.change.review.unknown", "mastery.max_increase_per_event", "mastery.max_decrease_per_event",
            "mastery.max_daily_increase", "mastery.max_daily_decrease");

    private final SystemConfigReferenceMapper configs;

    public MasteryAlgorithmConfigService(SystemConfigReferenceMapper configs) {
        this.configs = configs;
    }

    public MasteryAlgorithmConfig load() {
        Map<String, String> values = configs.selectList(Wrappers.<SystemConfigReferenceEntity>lambdaQuery()
                .in(SystemConfigReferenceEntity::getConfigKey, REQUIRED_KEYS)).stream()
                .collect(Collectors.toMap(SystemConfigReferenceEntity::getConfigKey,
                        SystemConfigReferenceEntity::getConfigValue, (left, right) -> left, LinkedHashMap::new));
        List<String> missing = REQUIRED_KEYS.stream().filter(key -> !values.containsKey(key)).sorted().toList();
        if (!missing.isEmpty()) throw new IllegalStateException("missing mastery algorithm configuration: " + missing);
        try {
            Map<MasteryEvidenceResult, BigDecimal> initial = new EnumMap<>(MasteryEvidenceResult.class);
            for (MasteryEvidenceResult result : MasteryEvidenceResult.values()) {
                initial.put(result, decimal(values, "mastery.initial." + lower(result)));
            }
            Map<String, BigDecimal> changes = new LinkedHashMap<>();
            for (MasteryEvidenceType type : MasteryEvidenceType.values()) {
                for (MasteryEvidenceResult result : supported(type)) {
                    changes.put(type.name() + "." + result.name(),
                            decimal(values, "mastery.change." + lower(type) + "." + lower(result)));
                }
            }
            boolean decay = parseBoolean(values.get("mastery.time_decay.enabled"));
            MasteryAlgorithmConfig config = new MasteryAlgorithmConfig(values.get("mastery.algorithm.version"),
                    decimal(values, "mastery.score_rate.correct_min"),
                    decimal(values, "mastery.score_rate.partial_min"), decay,
                    ZoneId.of(values.get("system.timezone")), Map.copyOf(initial), Map.copyOf(changes),
                    decimal(values, "mastery.max_increase_per_event"),
                    decimal(values, "mastery.max_decrease_per_event"),
                    decimal(values, "mastery.max_daily_increase"),
                    decimal(values, "mastery.max_daily_decrease"), Map.copyOf(values));
            validate(config);
            return config;
        } catch (NumberFormatException | DateTimeException ex) {
            throw new IllegalStateException("invalid mastery algorithm configuration", ex);
        }
    }

    private void validate(MasteryAlgorithmConfig config) {
        if (!"1.0".equals(config.algorithmVersion())) {
            throw new IllegalStateException("unsupported mastery algorithm version: " + config.algorithmVersion());
        }
        if (config.timeDecayEnabled()) {
            throw new IllegalStateException("unsupported mastery algorithm configuration: time decay is enabled");
        }
        if (config.partialMinimum().signum() < 0 || config.correctMinimum().compareTo(BigDecimal.ONE) > 0
                || config.partialMinimum().compareTo(config.correctMinimum()) > 0) {
            throw new IllegalStateException("invalid mastery score rate thresholds");
        }
    }

    private BigDecimal decimal(Map<String, String> values, String key) {
        return new BigDecimal(values.get(key));
    }

    private boolean parseBoolean(String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalStateException("invalid boolean mastery configuration");
        }
        return Boolean.parseBoolean(value);
    }

    private List<MasteryEvidenceResult> supported(MasteryEvidenceType type) {
        return switch (type) {
            case EXAM -> List.of(MasteryEvidenceResult.CORRECT, MasteryEvidenceResult.PARTIAL,
                    MasteryEvidenceResult.WRONG);
            case PRACTICE -> List.of(MasteryEvidenceResult.CORRECT, MasteryEvidenceResult.PARTIAL,
                    MasteryEvidenceResult.WRONG);
            case REVIEW -> List.of(MasteryEvidenceResult.values());
        };
    }

    private String lower(Enum<?> value) {
        return value.name().toLowerCase(java.util.Locale.ROOT);
    }
}
