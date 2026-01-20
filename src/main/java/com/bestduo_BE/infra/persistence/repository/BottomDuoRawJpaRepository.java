package com.bestduo_BE.infra.persistence.repository;


import com.bestduo_BE.infra.persistence.entity.BottomDuoRawEntity;
import com.bestduo_BE.infra.persistence.entity.BottomDuoRawId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BottomDuoRawJpaRepository extends JpaRepository<BottomDuoRawEntity, BottomDuoRawId> {}
