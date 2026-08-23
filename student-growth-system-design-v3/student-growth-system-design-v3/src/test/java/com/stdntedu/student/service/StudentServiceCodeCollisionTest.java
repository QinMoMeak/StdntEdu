package com.stdntedu.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import com.stdntedu.base.entity.GradeEntity;
import com.stdntedu.base.entity.StageEntity;
import com.stdntedu.base.mapper.GradeMapper;
import com.stdntedu.base.mapper.StageMapper;
import com.stdntedu.common.exception.BusinessException;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.Student;
import com.stdntedu.generated.model.StudentCreate;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import com.stdntedu.student.converter.StudentConverter;
import com.stdntedu.student.entity.StudentEntity;
import com.stdntedu.student.mapper.StudentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class StudentServiceCodeCollisionTest {
    @Test
    void retriesOnlyStudentCodeCollisionAndReturnsSuccessfulInsert() {
        Fixture fixture = fixture();
        AtomicInteger inserts = new AtomicInteger();
        when(fixture.students.insert(any(StudentEntity.class))).thenAnswer(invocation -> {
            StudentEntity entity = invocation.getArgument(0);
            if (inserts.getAndIncrement() == 0) throw studentCodeConflict();
            entity.setId(42L);
            return 1;
        });

        Student result = fixture.service.create(new StudentCreate("Alice", "1", "2"));

        assertThat(result).isSameAs(fixture.dto);
        assertThat(inserts).hasValue(2);
    }

    @Test
    void boundedRetriesReturnStableInternalErrorAndOtherDuplicatesAreNotRetried() {
        Fixture exhausted = fixture();
        AtomicInteger attempts = new AtomicInteger();
        when(exhausted.students.insert(any(StudentEntity.class))).thenAnswer(invocation -> {
            attempts.incrementAndGet();
            throw studentCodeConflict();
        });
        assertThatThrownBy(() -> exhausted.service.create(new StudentCreate("Alice", "1", "2")))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getCode()).isEqualTo("STUDENT_CODE_GENERATION_FAILED");
                    assertThat(error.getStatus().value()).isEqualTo(500);
                });
        assertThat(attempts).hasValue(5);

        Fixture other = fixture();
        AtomicInteger otherAttempts = new AtomicInteger();
        when(other.students.insert(any(StudentEntity.class))).thenAnswer(invocation -> {
            otherAttempts.incrementAndGet();
            throw new DuplicateKeyException("other", new SQLException("Duplicate entry for key 'other_key'", "23000", 1062));
        });
        assertThatThrownBy(() -> other.service.create(new StudentCreate("Alice", "1", "2")))
                .isInstanceOf(com.stdntedu.common.exception.DataConflictException.class);
        assertThat(otherAttempts).hasValue(1);
    }

    private Fixture fixture() {
        StudentMapper students = mock(StudentMapper.class);
        StageMapper stages = mock(StageMapper.class);
        GradeMapper grades = mock(GradeMapper.class);
        StudentConverter converter = mock(StudentConverter.class);
        SystemTimezoneProvider time = mock(SystemTimezoneProvider.class);
        StageEntity stage = new StageEntity(); stage.setId(1L); stage.setEnabled(true);
        GradeEntity grade = new GradeEntity(); grade.setId(2L); grade.setStageId(1L); grade.setEnabled(true);
        when(stages.selectOne(any())).thenReturn(stage);
        when(grades.selectOne(any())).thenReturn(grade);
        when(time.today()).thenReturn(LocalDate.of(2027, 1, 1));
        StudentEntity entity = new StudentEntity();
        when(converter.fromCreate(any())).thenAnswer(ignored -> new StudentEntity());
        Student dto = mock(Student.class);
        when(converter.toDto(any())).thenReturn(dto);
        AtomicInteger codes = new AtomicInteger();
        StudentService service = new StudentService(students, stages, grades, converter, new IdConverter(), time) {
            @Override String generateStudentCode() { return "STU20270101" + String.format("%06d", codes.getAndIncrement()); }
        };
        return new Fixture(service, students, dto);
    }

    private DuplicateKeyException studentCodeConflict() {
        return new DuplicateKeyException("student code", new SQLException(
                "Duplicate entry for key 'student.student_code'", "23000", 1062));
    }

    private record Fixture(StudentService service, StudentMapper students, Student dto) { }
}
