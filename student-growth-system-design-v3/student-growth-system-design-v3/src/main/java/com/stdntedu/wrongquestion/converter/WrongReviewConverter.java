package com.stdntedu.wrongquestion.converter;

import java.time.ZoneOffset;
import com.stdntedu.common.validation.IdConverter;
import com.stdntedu.generated.model.ReviewCreate;
import com.stdntedu.wrongquestion.entity.WrongReviewEntity;
import org.springframework.stereotype.Component;

@Component
public class WrongReviewConverter {
 private final IdConverter ids; public WrongReviewConverter(IdConverter ids){this.ids=ids;}
 public WrongReviewEntity fromCreate(Long wrongQuestionId,String key,ReviewCreate r){ WrongReviewEntity e=new WrongReviewEntity();e.setWrongQuestionId(wrongQuestionId);e.setIdempotencyKey(key);e.setReviewTime(r.getReviewTime().atZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai")).toLocalDateTime());e.setResult(r.getResult().getValue());e.setDurationSeconds(r.getDurationSeconds());e.setStudentAnswer(r.getStudentAnswer());e.setRemark(r.getRemark());return e; }
 public String id(Long id){return ids.toString(id);}
}
