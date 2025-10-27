package com.questify.mapper;

import com.questify.domain.Submission;
import com.questify.dto.SubmissionDtos;
import org.mapstruct.*;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface SubmissionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "quest", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "reviewStatus", ignore = true)
    @Mapping(target = "reviewNote", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "mediaType", ignore = true)
    @Mapping(target = "fileSize", ignore = true)
    @Mapping(target = "proofObjectKey", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "reviewerUserId", ignore = true)
    @Mapping(target = "closed", ignore = true)
    Submission toEntity(SubmissionDtos.CreateSubmissionReq req);

    @Mapping(target = "questId", source = "quest.id")
    @Mapping(target = "userId", source = "user.id")
    SubmissionDtos.SubmissionRes toRes(Submission s);
}
