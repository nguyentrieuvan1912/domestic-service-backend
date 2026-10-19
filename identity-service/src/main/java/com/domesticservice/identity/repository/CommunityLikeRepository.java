package com.domesticservice.identity.repository;

import com.domesticservice.identity.entity.CommunityLike;
import com.domesticservice.identity.entity.CommunityLikeId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityLikeRepository extends JpaRepository<CommunityLike, CommunityLikeId> {
}
