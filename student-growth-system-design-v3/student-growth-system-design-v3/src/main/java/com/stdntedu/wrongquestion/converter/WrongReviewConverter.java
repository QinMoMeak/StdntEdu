package com.stdntedu.wrongquestion.converter;

import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.ReviewCreate;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import com.stdntedu.wrongquestion.entity.WrongReviewEntity;
import org.springframework.stereotype.Component;

@Component
public class WrongReviewConverter {
 private final IdConverter ids; private final SystemTimezoneProvider time;
 public WrongReviewConverter(IdConverter ids,SystemTimezoneProvider time){this.ids=ids;this.time=time;}
 public WrongReviewEntity fromCreate(Long wrongQuestionId,String key,ReviewCreate r){ WrongReviewEntity e=new WrongReviewEntity();e.setWrongQuestionId(wrongQuestionId);e.setIdempotencyKey(key);e.setReviewTime(time.toLocalDateTime(r.getReviewTime()));e.setResult(r.getResult().getValue());e.setDurationSeconds(r.getDurationSeconds());e.setStudentAnswer(r.getStudentAnswer());e.setRemark(r.getRemark());return e; }
 public String id(Long id){return ids.toString(id);}
}
