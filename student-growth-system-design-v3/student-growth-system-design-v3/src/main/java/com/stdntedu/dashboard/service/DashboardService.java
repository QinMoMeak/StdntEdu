package com.stdntedu.dashboard.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.exception.ResourceNotFoundException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.dashboard.converter.DashboardConverter;
import com.stdntedu.dashboard.mapper.DashboardQueryMapper;
import com.stdntedu.dashboard.mapper.DashboardResourceCountRow;
import com.stdntedu.dashboard.mapper.DashboardReviewCountRow;
import com.stdntedu.dashboard.mapper.DashboardTaskCountRow;
import com.stdntedu.generated.model.DashboardDto;
import com.stdntedu.generated.model.DashboardDtoStatisticsPeriod;
import com.stdntedu.generated.model.DashboardDtoToday;
import com.stdntedu.resource.mapper.StudyLogQueryMapper;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import com.stdntedu.score.converter.ScoreConverter;
import com.stdntedu.student.entity.AcademicTermEntity;
import com.stdntedu.student.mapper.AcademicTermMapper;
import com.stdntedu.student.mapper.StudentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final DashboardQueryMapper dashboardQueries;
    private final StudyLogQueryMapper studyLogQueries;
    private final StudentMapper students;
    private final AcademicTermMapper terms;
    private final SystemTimezoneProvider timezones;
    private final DashboardConverter converter;
    private final ScoreConverter scoreConverter;
    private final IdConverter ids;

    public DashboardService(DashboardQueryMapper dashboardQueries, StudyLogQueryMapper studyLogQueries,
            StudentMapper students, AcademicTermMapper terms, SystemTimezoneProvider timezones,
            DashboardConverter converter, ScoreConverter scoreConverter, IdConverter ids) {
        this.dashboardQueries = dashboardQueries;
        this.studyLogQueries = studyLogQueries;
        this.students = students;
        this.terms = terms;
        this.timezones = timezones;
        this.converter = converter;
        this.scoreConverter = scoreConverter;
        this.ids = ids;
    }

    @Transactional(readOnly = true)
    public DashboardDto get(String studentId, String academicTermId, LocalDate date, String timezone) {
        Long studentKey = ids.toLong(studentId);
        if (students.selectById(studentKey) == null) {
            throw new ResourceNotFoundException("student not found");
        }

        ZoneId databaseZone = timezones.get();
        ZoneId dashboardZone = resolveDashboardZone(timezone, databaseZone);
        LocalDate targetDate = date == null ? LocalDate.now(dashboardZone) : date;
        LocalDateTime dayStart = targetDate.atStartOfDay(dashboardZone)
                .withZoneSameInstant(databaseZone).toLocalDateTime();
        LocalDateTime nextDayStart = targetDate.plusDays(1).atStartOfDay(dashboardZone)
                .withZoneSameInstant(databaseZone).toLocalDateTime();

        Period period = resolvePeriod(studentKey, academicTermId, targetDate);
        DashboardTaskCountRow taskCounts = dashboardQueries.selectTaskCounts(studentKey, targetDate);
        DashboardReviewCountRow reviewCounts = dashboardQueries.selectReviewCounts(studentKey, dayStart, nextDayStart);
        DashboardResourceCountRow resourceCounts = dashboardQueries.selectResourceCounts(studentKey);

        DashboardDtoToday today = new DashboardDtoToday()
                .studyDurationSeconds(dashboardQueries.sumStudyDuration(studentKey, targetDate))
                .completedTaskCount(taskCounts.getCompletedTaskCount())
                .totalTaskCount(taskCounts.getTotalTaskCount())
                .dueReviewCount(reviewCounts.getDueReviewCount())
                .overdueReviewCount(reviewCounts.getOverdueReviewCount())
                .waitingResourceCount(resourceCounts.getWaitingResourceCount())
                .learningResourceCount(resourceCounts.getLearningResourceCount());

        DashboardDtoStatisticsPeriod statisticsPeriod = new DashboardDtoStatisticsPeriod()
                .startDate(period.startDate())
                .endDate(period.endDate())
                .academicTermId(academicTermId);

        return new DashboardDto()
                .today(today)
                .latestExam(converter.toExam(dashboardQueries.selectLatestExam(studentKey, period.termId())))
                .scoreTrends(dashboardQueries.selectScoreTrends(studentKey, period.startDate(), period.endDate())
                        .stream().map(exam -> scoreConverter.toTrendPoint(exam, null)).toList())
                .weakKnowledge(dashboardQueries.selectWeakMastery(studentKey).stream()
                        .map(row -> converter.toMastery(row, databaseZone, dashboardZone)).toList())
                .dueReviews(dashboardQueries.selectDueReviews(studentKey, nextDayStart).stream()
                        .map(row -> converter.toWrongQuestion(row, databaseZone, dashboardZone)).toList())
                .waitingResources(dashboardQueries.selectWaitingResources(studentKey).stream()
                        .map(row -> converter.toResource(row, databaseZone, dashboardZone)).toList())
                .recentStudyLogs(studyLogQueries.selectPage(studentKey, null, period.startDate(), period.endDate(),
                        null, 0, 5).stream()
                        .map(row -> converter.toStudyLog(row, databaseZone, dashboardZone)).toList())
                .aiSuggestions(List.of())
                .statisticsPeriod(statisticsPeriod);
    }

    private ZoneId resolveDashboardZone(String timezone, ZoneId fallback) {
        if (timezone == null || timezone.isBlank()) {
            return fallback;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw validation("invalid dashboard timezone");
        }
    }

    private Period resolvePeriod(Long studentId, String academicTermId, LocalDate targetDate) {
        if (academicTermId == null || academicTermId.isBlank()) {
            return new Period(targetDate.minusDays(29), targetDate, null);
        }
        Long termId = ids.toLong(academicTermId);
        AcademicTermEntity term = terms.selectById(termId);
        if (term == null) {
            throw new ResourceNotFoundException("academic term not found");
        }
        if (!studentId.equals(term.getStudentId())) {
            throw validation("academic term does not belong to student");
        }
        if (term.getStartDate() == null || term.getEndDate() == null) {
            throw validation("academic term dates are required for dashboard statistics");
        }
        if (targetDate.isBefore(term.getStartDate())) {
            throw validation("dashboard date is before academic term start date");
        }
        LocalDate endDate = targetDate.isBefore(term.getEndDate()) ? targetDate : term.getEndDate();
        return new Period(term.getStartDate(), endDate, termId);
    }

    private BusinessException validation(String message) {
        return new BusinessException("BUSINESS_RULE_VIOLATION", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private record Period(LocalDate startDate, LocalDate endDate, Long termId) { }
}
