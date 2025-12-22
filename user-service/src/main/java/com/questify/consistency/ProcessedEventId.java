package com.questify.consistency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ProcessedEventId implements Serializable {

    @Column(length = 200, nullable = false)
    private String consumerGroup;

    @Column(length = 36, nullable = false)
    private String eventId;
}
