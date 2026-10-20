package com.domesticservice.identity.entity;

import java.io.Serializable;
import java.time.*;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class CommunityLikeId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long postId;

    private Long userId;

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof CommunityLikeId that))
            return false;
        return Objects.equals(postId, that.postId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, userId);
    }
}
