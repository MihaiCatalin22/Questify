package com.questify.mapper;

import com.questify.domain.Quest;
import com.questify.dto.QuestDtos;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface QuestMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "participants", ignore = true)
    Quest toEntity(QuestDtos.CreateQuestReq req);

    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "participantsCount",
            expression = "java(q.getParticipants() == null ? 0 : q.getParticipants().size())")
    @Mapping(target = "completedByCurrentUser", ignore = true)
    QuestDtos.QuestRes toRes(Quest q);
}
