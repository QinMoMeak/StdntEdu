package com.stdntedu.wrongquestion.converter;

import java.time.LocalDate;
import java.util.List;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.*;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import com.stdntedu.wrongquestion.entity.*;
import org.springframework.stereotype.Component;

@Component
public class WrongQuestionConverter {
 private final IdConverter ids;
 private final SystemTimezoneProvider time;
 public WrongQuestionConverter(IdConverter ids, SystemTimezoneProvider time) { this.ids=ids; this.time=time; }
 public WrongQuestionEntity fromCreate(WrongCreate r) { WrongQuestionEntity e=new WrongQuestionEntity(); apply(r.getStudentId(),r.getSubjectId(),r.getSourceType(),r.getQuestionType(),r.getQuestionText(),r.getStudentAnswer(),r.getCorrectAnswer(),r.getAnalysisText(),r.getErrorType(),r.getDifficulty(),e); e.setStatus("NEW"); e.setReviewStage(0); e.setReviewCount(0); e.setOccurredDate(time.today()); e.setNextReviewTime(time.localDateTime()); e.setDeleted(false); e.setVersion(0); return e; }
 public void apply(WrongUpdate r, WrongQuestionEntity e) { apply(r.getStudentId(),r.getSubjectId(),r.getSourceType(),r.getQuestionType(),r.getQuestionText(),r.getStudentAnswer(),r.getCorrectAnswer(),r.getAnalysisText(),r.getErrorType(),r.getDifficulty(),e); }
 private void apply(String studentId,String subjectId,WrongSource source,String questionType,String text,String answer,String correct,String analysis,String error,Integer difficulty,WrongQuestionEntity e) { e.setStudentId(ids.toLong(studentId));e.setSubjectId(ids.toLong(subjectId));e.setSourceType(source.getValue());e.setQuestionType(questionType);e.setQuestionText(text==null?null:text.trim());e.setStudentAnswer(answer);e.setCorrectAnswer(correct);e.setAnalysisText(analysis);e.setErrorType(error);e.setDifficulty(difficulty); }
 public Wrong toDto(WrongQuestionEntity e,List<WrongQuestionKnowledgeEntity> links) { return new Wrong().id(ids.toString(e.getId())).studentId(ids.toString(e.getStudentId())).subjectId(ids.toString(e.getSubjectId())).sourceType(WrongSource.fromValue(e.getSourceType())).questionType(e.getQuestionType()).questionText(e.getQuestionText()).studentAnswer(e.getStudentAnswer()).correctAnswer(e.getCorrectAnswer()).analysisText(e.getAnalysisText()).errorType(e.getErrorType()).difficulty(e.getDifficulty()).knowledgePoints(links.stream().map(l->new KnowledgeLink().knowledgeId(ids.toString(l.getKnowledgeId())).primary(l.getIsPrimary()).confidence(l.getConfidence())).toList()).status(WrongStatus.fromValue(e.getStatus())).version(e.getVersion()); }
}
