package com.questify.config;

import com.questify.repository.QuestRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("questSecurity")
public class QuestSecurity {
    private final QuestRepository quests;
    private final JwtAuth jwt;

    public QuestSecurity(QuestRepository quests, JwtAuth jwt) {
        this.quests = quests;
        this.jwt = jwt;
    }

    public boolean isOwner(Long questId, Authentication auth) {
        String me = jwt.userId(auth);
        if (me == null) return false;
        return quests.findById(questId)
                .map(q -> me.equals(q.getCreatedByUserId()))
                .orElse(false);
    }
}