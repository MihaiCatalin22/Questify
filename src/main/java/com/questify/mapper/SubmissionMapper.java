package com.questify.mapper;

import com.questify.domain.Submission;
import com.questify.dto.SubmissionDtos;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface SubmissionMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "quest", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "reviewStatus", ignore = true)
    @Mapping(target = "reviewNote", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Submission toEntity(SubmissionDtos.CreateSubmissionReq req);


    @Mapping(target = "questId", source = "quest.id")
    @Mapping(target = "userId", source = "user.id")
    SubmissionDtos.SubmissionRes toRes(Submission s);
}
