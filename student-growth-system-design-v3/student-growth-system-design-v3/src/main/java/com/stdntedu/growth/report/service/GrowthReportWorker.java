package com.stdntedu.growth.report.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stdntedu.generated.model.GrowthReportSnapshotDto;
import com.stdntedu.growth.report.entity.GrowthReportEntity;
import com.stdntedu.growth.report.mapper.GrowthReportMapper;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GrowthReportWorker {
    private static final Logger LOG = LoggerFactory.getLogger(GrowthReportWorker.class);
    private final GrowthReportMapper reports;
    private final GrowthReportSnapshotService snapshots;
    private final ObjectMapper json;
    private final SystemTimezoneProvider time;

    public GrowthReportWorker(GrowthReportMapper reports, GrowthReportSnapshotService snapshots,
            ObjectMapper json, SystemTimezoneProvider time) {
        this.reports = reports;
        this.snapshots = snapshots;
        this.json = json;
        this.time = time;
    }

    public void run(Long id) {
        if (reports.update(null, Wrappers.<GrowthReportEntity>lambdaUpdate()
                .eq(GrowthReportEntity::getId, id).eq(GrowthReportEntity::getStatus, "PENDING")
                .set(GrowthReportEntity::getStatus, "RUNNING")
                .set(GrowthReportEntity::getStartTime, time.localDateTime())
                .set(GrowthReportEntity::getProgressPercent, 10).setSql("version=version+1")) != 1) return;
        try {
            GrowthReportEntity report = reports.selectById(id);
            GrowthReportSnapshotDto snapshot = snapshots.build(report);
            String snapshotJson = json.writeValueAsString(snapshot);
            String markdown = markdown(report, snapshot);
            int changed = reports.update(null, Wrappers.<GrowthReportEntity>lambdaUpdate()
                    .eq(GrowthReportEntity::getId, id).eq(GrowthReportEntity::getStatus, "RUNNING")
                    .eq(GrowthReportEntity::getCancelRequested, false)
                    .set(GrowthReportEntity::getStatus, "SUCCESS")
                    .set(GrowthReportEntity::getStatisticsSnapshotJson, snapshotJson)
                    .set(GrowthReportEntity::getContentMarkdown, markdown)
                    .set(GrowthReportEntity::getProgressPercent, 100)
                    .set(GrowthReportEntity::getFinishTime, time.localDateTime()).setSql("version=version+1"));
            if (changed != 1) markCancelled(id);
        } catch (Exception ex) {
            reports.update(null, Wrappers.<GrowthReportEntity>lambdaUpdate()
                    .eq(GrowthReportEntity::getId, id).eq(GrowthReportEntity::getStatus, "RUNNING")
                    .set(GrowthReportEntity::getStatus, "FAILED")
                    .set(GrowthReportEntity::getErrorCode, "REPORT_GENERATION_FAILED")
                    .set(GrowthReportEntity::getErrorMessage, "growth report generation failed")
                    .set(GrowthReportEntity::getStatisticsSnapshotJson, null)
                    .set(GrowthReportEntity::getContentMarkdown, null)
                    .set(GrowthReportEntity::getFinishTime, time.localDateTime()).setSql("version=version+1"));
            LOG.warn("Growth report generation failed for report {}", id, ex);
        }
    }

    private void markCancelled(Long id) {
        reports.update(null, Wrappers.<GrowthReportEntity>lambdaUpdate()
                .eq(GrowthReportEntity::getId, id).eq(GrowthReportEntity::getStatus, "RUNNING")
                .eq(GrowthReportEntity::getCancelRequested, true)
                .set(GrowthReportEntity::getStatus, "CANCELLED")
                .set(GrowthReportEntity::getFinishTime, time.localDateTime()).setSql("version=version+1"));
    }

    private String markdown(GrowthReportEntity report, GrowthReportSnapshotDto snapshot) {
        return "# " + report.getTitle().replace('\n', ' ').replace('\r', ' ') + "\n\n"
                + "- 报告类型：" + report.getReportType() + "\n"
                + "- 报告区间：" + report.getStartDate() + " 至 " + report.getEndDate() + "\n"
                + "- 生成时间：" + snapshot.getGeneratedAt() + "\n\n"
                + "## 成绩\n\n考试数：" + snapshot.getScores().get("examCount")
                + "，平均得分率：" + snapshot.getScores().get("averageScoreRate") + "。\n\n"
                + "## 掌握度\n\n知识点数：" + snapshot.getMastery().get("knowledgeCount")
                + "，薄弱知识点：" + snapshot.getMastery().get("weakCount") + "。\n\n"
                + "## 错题与复习\n\n错题数：" + snapshot.getWrongQuestions().get("totalCount")
                + "，到期复习：" + snapshot.getWrongQuestions().get("dueReviewCount") + "。\n\n"
                + "## 学习\n\n学习时长（秒）：" + snapshot.getLearning().get("studyDurationSeconds") + "。\n\n"
                + "## 成长事件\n\n事件数：" + snapshot.getGrowthEvents().get("totalCount") + "。\n\n"
                + "## 建议\n\n" + (snapshot.getRecommendations().isEmpty() ? "暂无。"
                        : String.join("\n", snapshot.getRecommendations().stream().map(value -> "- " + value).toList()))
                + "\n";
    }
}
